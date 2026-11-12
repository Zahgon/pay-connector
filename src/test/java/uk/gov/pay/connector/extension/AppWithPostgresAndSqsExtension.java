package uk.gov.pay.connector.extension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.restassured.specification.RequestSpecification;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest;
import uk.gov.pay.connector.app.ConfigOverride;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.ConnectorTestApplication;
import uk.gov.pay.connector.app.config.AuthorisationConfig;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.rules.AdyenBalancePlatformMockClient;
import uk.gov.pay.connector.rules.AdyenKycMockClient;
import uk.gov.pay.connector.rules.AdyenManagementMockClient;
import uk.gov.pay.connector.rules.AdyenCheckoutMockClient;
import uk.gov.pay.connector.rules.CardidStub;
import uk.gov.pay.connector.rules.LedgerStub;
import uk.gov.pay.connector.rules.NotifyStub;
import uk.gov.pay.connector.rules.SqsTestDocker;
import uk.gov.pay.connector.rules.StripeMockClient;
import uk.gov.pay.connector.rules.WorldpayMockClient;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import java.util.List;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.google.common.collect.Lists.newArrayList;
import static uk.gov.pay.connector.app.ConfigOverride.config;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static uk.gov.pay.connector.rules.PostgresTestDocker.getConnectionUrl;
import static uk.gov.pay.connector.rules.PostgresTestDocker.getDbPassword;
import static uk.gov.pay.connector.rules.PostgresTestDocker.getDbUsername;
import static uk.gov.pay.connector.rules.PostgresTestDocker.getOrCreate;
import static uk.gov.pay.connector.util.RandomTestDataGeneratorUtils.secureRandomInt;

public class AppWithPostgresAndSqsExtension implements BeforeEachCallback, BeforeAllCallback, AfterEachCallback, AfterAllCallback {
    private static final Logger logger = LoggerFactory.getLogger(AppWithPostgresAndSqsExtension.class);
    private static final String CONFIG_PATH = "config/test-it-config.yaml";
    private final Jdbi jdbi;
    private final SqsClient sqsClient;
    private final ConnectorTestApplication application;
    private final int wireMockPort;
    private final int worldpayWireMockPort;
    private final int ledgerWireMockPort;
    private final int stripeWireMockPort;
    private final int adyenWireMockPort;
    private final int notifyWireMockPort;

    protected static DatabaseTestHelper databaseTestHelper;
    private final WireMockServer wireMockServer;
    private final WireMockServer worldpayWireMockServer;
    private final WireMockServer ledgerWireMockServer;
    private final WireMockServer stripeWireMockServer;
    private final WireMockServer adyenWireMockServer;
    private final WireMockServer notifyWireMockServer;
    private final WorldpayMockClient worldpayMockClient;
    private final StripeMockClient stripeMockClient;
    private final AdyenCheckoutMockClient adyenCheckoutMockClient;
    private final AdyenKycMockClient adyenKycMockClient;
    private final AdyenManagementMockClient adyenManagementMockClient;
    private final AdyenBalancePlatformMockClient adyenBalancePlatformMockClient;
    private final LedgerStub ledgerStub;
    private final CardidStub cardidStub;
    private final NotifyStub notifyStub;

    protected static String accountId = String.valueOf(secureRandomInt());
    protected static ObjectMapper mapper;
    protected DatabaseFixtures databaseFixtures;
    private static boolean migratedDatabase = false;

    public AppWithPostgresAndSqsExtension() {
        this(new Class<?>[0], new ConfigOverride[0]);
    }

    /**
     * @param overridingConfiguration a configuration class whose {@code @Primary} beans replace the service's own
     */
    public AppWithPostgresAndSqsExtension(Class<?> overridingConfiguration) {
        this(new Class<?>[]{overridingConfiguration}, new ConfigOverride[0]);
    }

    public AppWithPostgresAndSqsExtension(ConfigOverride... configOverrides) {
        this(new Class<?>[0], configOverrides);
    }

    public AppWithPostgresAndSqsExtension(Class<?> overridingConfiguration, ConfigOverride... configOverrides) {
        this(new Class<?>[]{overridingConfiguration}, configOverrides);
    }

    private AppWithPostgresAndSqsExtension(Class<?>[] overridingConfigurations, ConfigOverride... configOverrides) {
        getOrCreate();

        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort().bindAddress("localhost"));
        worldpayWireMockServer = new WireMockServer(wireMockConfig().dynamicPort().bindAddress("localhost"));
        stripeWireMockServer = new WireMockServer(wireMockConfig().dynamicPort().bindAddress("localhost"));
        adyenWireMockServer = new WireMockServer(wireMockConfig().dynamicPort().bindAddress("localhost"));
        ledgerWireMockServer = new WireMockServer(wireMockConfig().dynamicPort().bindAddress("localhost"));
        notifyWireMockServer = new WireMockServer(wireMockConfig().dynamicPort().bindAddress("localhost"));

        wireMockServer.start();
        worldpayWireMockServer.start();
        adyenWireMockServer.start();
        stripeWireMockServer.start();
        ledgerWireMockServer.start();
        notifyWireMockServer.start();

        wireMockPort = wireMockServer.port();
        worldpayWireMockPort = worldpayWireMockServer.port();
        stripeWireMockPort = stripeWireMockServer.port();
        adyenWireMockPort = adyenWireMockServer.port();
        ledgerWireMockPort = ledgerWireMockServer.port();
        notifyWireMockPort = notifyWireMockServer.port();

        sqsClient = SqsTestDocker.initialise(List.of("capture-queue", "event-queue", "tasks-queue", "reconcile-queue"));

        ConfigOverride[] newConfigOverrides = overrideDatabaseConfig(configOverrides);
        newConfigOverrides = overrideEndpointConfig(newConfigOverrides);
        newConfigOverrides = overrideSqsConfig(newConfigOverrides);

        List<ConfigOverride> overrides = List.of(newConfigOverrides);
        if (!migratedDatabase) {
            // Only run DB migrations once for the entire test JVM, before the first application starts
            ConnectorTestApplication.migrateDatabase(ConnectorTestApplication.loadConfiguration(CONFIG_PATH, overrides));
            migratedDatabase = true;
        }
        try {
            application = ConnectorTestApplication.start(CONFIG_PATH, overrides, overridingConfigurations);
        } catch (RuntimeException e) {
            logger.error("Exception starting application - {}", e.getMessage());
            throw e;
        }

        jdbi = Jdbi.create(getConnectionUrl(), getDbUsername(), getDbPassword());
        jdbi.installPlugin(new SqlObjectPlugin());

        databaseTestHelper = new DatabaseTestHelper(jdbi);

        worldpayMockClient = new WorldpayMockClient(worldpayWireMockServer);
        stripeMockClient = new StripeMockClient(stripeWireMockServer);
        adyenKycMockClient = new AdyenKycMockClient(adyenWireMockServer);
        adyenManagementMockClient = new AdyenManagementMockClient(adyenWireMockServer);
        adyenBalancePlatformMockClient = new AdyenBalancePlatformMockClient(adyenWireMockServer);
        adyenCheckoutMockClient = new AdyenCheckoutMockClient(adyenWireMockServer);
        
        ledgerStub = new LedgerStub(ledgerWireMockServer);
        cardidStub = new CardidStub(wireMockServer);
        notifyStub = new NotifyStub(notifyWireMockServer);

        databaseFixtures = DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper);
        mapper = new ObjectMapper();
    }

    private void resetIfRunning(WireMockServer server) {
        if (server.isRunning()) {
            server.resetRequests();
        }
    }

    public void resetWireMockServer() {
        resetIfRunning(wireMockServer);
        resetIfRunning(worldpayWireMockServer);
        resetIfRunning(ledgerWireMockServer);
        resetIfRunning(stripeWireMockServer);
        resetIfRunning(notifyWireMockServer);
        resetIfRunning(adyenWireMockServer);
        ledgerStub.acceptPostEvent();
    }

    public void resetDatabase() {
        databaseTestHelper.truncateAllData();
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        // the database is migrated before the first application starts
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        ledgerStub.acceptPostEvent();
    }

    @Override
    public void afterEach(ExtensionContext context) {
        resetWireMockServer();
        resetDatabase();
    }

    @Override
    public void afterAll(ExtensionContext context) {
        if (context.getRequiredTestClass().getEnclosingClass() == null) {
            wireMockServer.stop();
            adyenWireMockServer.stop();
            worldpayWireMockServer.stop();
            stripeWireMockServer.stop();
            ledgerWireMockServer.stop();
            notifyWireMockServer.stop();
            application.close();
        }
    }

    public RequestSpecification givenSetup() {
        return given().port(getLocalPort())
                .contentType(JSON);
    }

    private ConfigOverride[] overrideDatabaseConfig(ConfigOverride[] configOverrides) {
        List<ConfigOverride> newConfigOverride = newArrayList(configOverrides);
        newConfigOverride.add(config("database.url", getConnectionUrl()));
        newConfigOverride.add(config("database.user", getDbUsername()));
        newConfigOverride.add(config("database.password", getDbPassword()));
        return newConfigOverride.toArray(new ConfigOverride[0]);
    }

    private ConfigOverride[] overrideEndpointConfig(ConfigOverride[] configOverrides) {
        List<ConfigOverride> newConfigOverride = newArrayList(configOverrides);
        newConfigOverride.add(config("worldpay.urls.test", "http://localhost:" + worldpayWireMockPort + "/jsp/merchant/xml/paymentService.jsp"));
        newConfigOverride.add(config("worldpay.threeDsFlexDdcUrls.test", "http://localhost:" + worldpayWireMockPort + "/shopper/3ds/ddc.html"));
        newConfigOverride.add(config("worldpay.threeDsFlexDdcUrls.live", "http://localhost:" + worldpayWireMockPort + "/shopper/3ds/ddc.html"));
        newConfigOverride.add(config("stripe.url", "http://localhost:" + stripeWireMockPort));
        newConfigOverride.add(config("ledgerBaseURL", "http://localhost:" + ledgerWireMockPort));
        newConfigOverride.add(config("cardidBaseURL", "http://localhost:" + wireMockPort));
        newConfigOverride.add(config("notifyConfig.notificationBaseURL", "http://localhost:" + notifyWireMockPort));
        newConfigOverride.add(config("notifyConfig.apiKey", "BD498A6F75C2E8BD3E4F9BEE97E287BBC5C35D8B7"));  // pragma: allowlist secret
        newConfigOverride.add(config("adyen.baseUrls.checkout.test", "http://localhost:" + adyenWireMockPort));
        newConfigOverride.add(config("adyen.baseUrls.legalEntityManagement.test", "http://localhost:" + adyenWireMockPort));
        newConfigOverride.add(config("adyen.baseUrls.management.test", "http://localhost:" + adyenWireMockPort));
        newConfigOverride.add(config("adyen.baseUrls.balancePlatform.test", "http://localhost:" + adyenWireMockPort));
        return newConfigOverride.toArray(new ConfigOverride[0]);
    }

    private ConfigOverride[] overrideSqsConfig(ConfigOverride[] configOverrides) {
        List<ConfigOverride> newConfigOverride = newArrayList(configOverrides);
        newConfigOverride.add(config("sqsConfig.eventQueueUrl", SqsTestDocker.getQueueUrl("event-queue")));
        newConfigOverride.add(config("sqsConfig.captureQueueUrl", SqsTestDocker.getQueueUrl("capture-queue")));
        newConfigOverride.add(config("sqsConfig.taskQueueUrl", SqsTestDocker.getQueueUrl("tasks-queue")));
        newConfigOverride.add(config("sqsConfig.payoutReconcileQueueUrl", SqsTestDocker.getQueueUrl("reconcile-queue")));
        newConfigOverride.add(config("sqsConfig.endpoint", SqsTestDocker.getEndpoint()));
        return newConfigOverride.toArray(new ConfigOverride[0]);
    }

    public <T> T getBean(Class<T> klazz) {
        return application.getBean(klazz);
    }

    public AuthorisationConfig getAuthorisationConfig() {
        return application.getConfiguration().getAuthorisationConfig();
    }

    public ConnectorTestApplication getAppRule() {
        return application;
    }

    public ConnectorConfiguration getConfiguration() {
        return application.getConfiguration();
    }

    public int getLocalPort() {
        return application.getLocalPort();
    }

    public int getAdminPort() {
        return application.getAdminPort();
    }

    public Jdbi getJdbi() {
        return jdbi;
    }

    public SqsClient getSqsClient() {
        return sqsClient;
    }

    public WireMockServer getWireMockServer() {
        return wireMockServer;
    }

    public WireMockServer getStripeWireMockServer() {
        return stripeWireMockServer;
    }

    public WireMockServer getAdyenWireMockServer() {
        return adyenWireMockServer;
    }

    public DatabaseTestHelper getDatabaseTestHelper() {
        return databaseTestHelper;
    }

    public WorldpayMockClient getWorldpayMockClient() {
        return worldpayMockClient;
    }

    public WireMockServer getWorldpayWireMockServer() {
        return worldpayWireMockServer;
    }

    public WireMockServer getNotifyWireMockServer() {
        return notifyWireMockServer;
    }
    
    public AdyenKycMockClient getAdyenKycMockClient() {
        return adyenKycMockClient;
    }

    public AdyenManagementMockClient getAdyenManagementMockClient() {
        return adyenManagementMockClient;
    }

    public AdyenBalancePlatformMockClient getAdyenBalancePlatformMockClient() {
        return adyenBalancePlatformMockClient;
    }
    
    public AdyenCheckoutMockClient getAdyenCheckoutMockClient() {
        return adyenCheckoutMockClient;
    }

    public StripeMockClient getStripeMockClient() {
        return stripeMockClient;
    }
    
    public LedgerStub getLedgerStub() {
        return ledgerStub;
    }

    public CardidStub getCardidStub() {
        return cardidStub;
    }

    public NotifyStub getNotifyStub() {
        return notifyStub;
    }

    public DatabaseFixtures getDatabaseFixtures() {
        return databaseFixtures;
    }

    public String getEventQueueUrl() {
        return SqsTestDocker.getQueueUrl("event-queue");
    }

    public void purgeEventQueue() {
        SqsClient sqsClient = getBean(SqsClient.class);
        sqsClient.purgeQueue(
                PurgeQueueRequest.builder()
                        .queueUrl(getEventQueueUrl())
                        .build()
        );
    }
}
