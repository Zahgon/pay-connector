package uk.gov.pay.connector.rules;

import org.jdbi.v3.core.Jdbi;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConfigOverride;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.ConnectorTestApplication;
import uk.gov.pay.connector.app.config.DatabaseConfig;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;

import static uk.gov.pay.connector.app.ConfigOverride.config;
import static uk.gov.pay.connector.rules.PostgresTestDocker.getConnectionUrl;
import static uk.gov.pay.connector.rules.PostgresTestDocker.getDbPassword;
import static uk.gov.pay.connector.rules.PostgresTestDocker.getDbUsername;
import static uk.gov.pay.connector.rules.PostgresTestDocker.getOrCreate;

/**
 * Runs the tests of a class against the connector started on a freshly migrated PostgreSQL database, with the payment
 * gateways, ledger, card id and the capture queue pointed at a WireMock port of the test's choosing.
 */
public class AppWithPostgresRule implements TestRule {
    private static final Logger logger = LoggerFactory.getLogger(AppWithPostgresRule.class);

    private final String configPath;
    private final List<ConfigOverride> configOverrides;
    private final int wireMockPort = findFreePort();

    private ConnectorTestApplication application;
    private DatabaseTestHelper databaseTestHelper;

    public AppWithPostgresRule(ConfigOverride... configOverrides) {
        this(true, configOverrides);
    }

    public AppWithPostgresRule(boolean stubPaymentGateways, ConfigOverride... configOverrides) {
        this("config/test-it-config.yaml", stubPaymentGateways, configOverrides);
    }

    public AppWithPostgresRule(String configPath, ConfigOverride... configOverrides) {
        this(configPath, true, configOverrides);
    }

    public AppWithPostgresRule(String configPath, boolean stubPaymentGateways, ConfigOverride... configOverrides) {
        getOrCreate();
        this.configPath = configPath;
        this.configOverrides = new ArrayList<>(List.of(configOverrides));
        this.configOverrides.add(config("database.url", getConnectionUrl()));
        this.configOverrides.add(config("database.user", getDbUsername()));
        this.configOverrides.add(config("database.password", getDbPassword()));
        this.configOverrides.add(config("sqsConfig.endpoint", "http://localhost:" + wireMockPort));
        this.configOverrides.add(config("sqsConfig.captureQueueUrl", "http://localhost:" + wireMockPort + "/capture-queue"));
        if (stubPaymentGateways) {
            this.configOverrides.add(config("worldpay.urls.test", "http://localhost:" + wireMockPort + "/jsp/merchant/xml/paymentService.jsp"));
            this.configOverrides.add(config("worldpay.threeDsFlexDdcUrls.test", String.format("http://localhost:%s/shopper/3ds/ddc.html", wireMockPort)));
            this.configOverrides.add(config("worldpay.threeDsFlexDdcUrls.live", String.format("http://localhost:%s/shopper/3ds/ddc.html", wireMockPort)));
            this.configOverrides.add(config("stripe.url", "http://localhost:" + wireMockPort));
        }
        this.configOverrides.add(config("ledgerBaseURL", "http://localhost:" + wireMockPort));
        this.configOverrides.add(config("cardidBaseURL", "http://localhost:" + wireMockPort));
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                logger.info("Clearing database.");
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

    public ConnectorConfiguration getConf() {
        return application.getConfiguration();
    }

    public int getLocalPort() {
        return application.getLocalPort();
    }

    public int getAdminPort() {
        return application.getAdminPort();
    }

    public int getWireMockPort() {
        return wireMockPort;
    }

    public <T> T getBean(Class<T> type) {
        return application.getBean(type);
    }

    public DatabaseTestHelper getDatabaseTestHelper() {
        return databaseTestHelper;
    }

    static int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
