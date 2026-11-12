package uk.gov.pay.connector.app.commands;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.integration.spring.SpringResourceAccessor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.config.DatabaseConfig;

import java.sql.Connection;
import java.sql.DriverManager;

/**
 * The {@code db migrate} command: applies all pending change sets of {@code migrations.xml} (which includes the
 * change logs of the {@code migrations} directory of the class path) to the configured database.
 */
@Configuration(proxyBeanMethods = false)
public class DatabaseMigrationCommand implements ApplicationRunner {

    private static final String MIGRATIONS_FILE = "migrations.xml";

    private final ConnectorConfiguration configuration;
    private final ResourceLoader resourceLoader;

    public DatabaseMigrationCommand(ConnectorConfiguration configuration, ResourceLoader resourceLoader) {
        this.configuration = configuration;
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        DatabaseConfig databaseConfig = configuration.getDatabaseConfig();
        try (Connection connection = DriverManager.getConnection(databaseConfig.getUrl(), databaseConfig.getUser(),
                databaseConfig.getPassword())) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (Liquibase liquibase = new Liquibase(MIGRATIONS_FILE, new SpringResourceAccessor(resourceLoader), database)) {
                liquibase.update("");
            }
        }
    }
}
