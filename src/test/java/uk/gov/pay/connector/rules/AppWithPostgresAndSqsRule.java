package uk.gov.pay.connector.rules;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.jdbi.v3.core.Jdbi;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.SqsClient;
import uk.gov.pay.connector.app.ConfigOverride;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.ConnectorTestApplication;
import uk.gov.pay.connector.app.config.DatabaseConfig;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import java.util.ArrayList;
import java.util.List;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static uk.gov.pay.connector.app.ConfigOverride.config;
import static uk.gov.pay.connector.rules.PostgresTestDocker.getConnectionUrl;
import static uk.gov.pay.connector.rules.PostgresTestDocker.getDbPassword;
import static uk.gov.pay.connector.rules.PostgresTestDocker.getDbUsername;
import static uk.gov.pay.connector.rules.PostgresTestDocker.getOrCreate;

/**
 * Runs the tests of a class against the connector started on a freshly migrated PostgreSQL database, with SQS queues
 * of a local SQS container and the payment gateways, ledger and card id pointed at a WireMock server.
 */
public class AppWithPostgresAndSqsRule implements TestRule {
    private static final Logger LOGGER = LoggerFactory.getLogger(AppWithPostgresAndSqsRule.class);

    private final String configPath;
    private final List<ConfigOverride> configOverrides = new ArrayList<>();
    private final SqsClient sqsClient;
    private final WireMockServer wireMockServer;
    private final int wireMockPort = AppWithPostgresRule.findFreePort();

    private ConnectorTestApplication application;
    private DatabaseTestHelper databaseTestHelper;

    public AppWithPostgresAndSqsRule(ConfigOverride... configOverrides) {
        this("config/test-it-config.yaml", configOverrides);
    }

    public AppWithPostgresAndSqsRule(String configPath, ConfigOverride... configOverrides) {
        getOrCreate();
        this.configPath = configPath;
        sqsClient = SqsTestDocker.initialise(List.of("capture-queue", "event-queue", "tasks-queue"));

        this.configOverrides.add(config("database.url", getConnectionUrl()));
        this.configOverrides.add(config("database.user", getDbUsername()));
        this.configOverrides.add(config("database.password", getDbPassword()));
        this.configOverrides.add(config("sqsConfig.captureQueueUrl", SqsTestDocker.getQueueUrl("capture-queue")));
        this.configOverrides.add(config("sqsConfig.eventQueueUrl", SqsTestDocker.getQueueUrl("event-queue")));
        this.configOverrides.add(config("sqsConfig.taskQueueUrl", SqsTestDocker.getQueueUrl("tasks-queue")));
        this.configOverrides.add(config("sqsConfig.endpoint", SqsTestDocker.getEndpoint()));
        this.configOverrides.add(config("worldpay.urls.test", "http://localhost:" + wireMockPort + "/jsp/merchant/xml/paymentService.jsp"));
        this.configOverrides.add(config("worldpay.threeDsFlexDdcUrls.test", "http://localhost:" + wireMockPort + "/shopper/3ds/ddc.html"));
        this.configOverrides.add(config("stripe.url", "http://localhost:" + wireMockPort));
        this.configOverrides.add(config("ledgerBaseURL", "http://localhost:" + wireMockPort));
        this.configOverrides.add(config("cardidBaseURL", "http://localhost:" + wireMockPort));
        this.configOverrides.addAll(List.of(configOverrides));

        wireMockServer = new WireMockServer(wireMockConfig().port(wireMockPort));
        wireMockServer.start();
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                LOGGER.info("Clearing database.");
                ConnectorConfiguration configuration = ConnectorTestApplication.loadConfiguration(configPath, configOverrides);
                TestDatabase.recreate(configuration);
                DatabaseConfig database = configuration.getDatabaseConfig();
                databaseTestHelper = new DatabaseTestHelper(Jdbi.create(database.getUrl(), database.getUser(), database.getPassword()));
                try (ConnectorTestApplication started = ConnectorTestApplication.start(configPath, configOverrides)) {
                    application = started;
                    base.evaluate();
                } finally {
                    application = null;
                }
            }
        };
    }

    public int getLocalPort() {
        return application.getLocalPort();
    }

    public DatabaseTestHelper getDatabaseTestHelper() {
        return databaseTestHelper;
    }

    public SqsClient getSqsClient() {
        return sqsClient;
    }

    public int getWireMockPort() {
        return wireMockPort;
    }

    public WireMockServer getWireMockServer() {
        return wireMockServer;
    }
}
