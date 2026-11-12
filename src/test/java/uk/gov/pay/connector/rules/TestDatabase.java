package uk.gov.pay.connector.rules;

import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.ConnectorTestApplication;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static java.sql.DriverManager.getConnection;

/**
 * Gives the tests' PostgreSQL database a fresh schema: everything dropped, the service's change log applied as
 * {@code db migrate} applies it, then the test-only change log {@code it-migrations.xml}.
 */
final class TestDatabase {

    private TestDatabase() {
    }

    static void recreate(ConnectorConfiguration configuration) {
        String url = configuration.getDatabaseConfig().getUrl();
        String user = configuration.getDatabaseConfig().getUser();
        String password = configuration.getDatabaseConfig().getPassword();
        try (Connection connection = getConnection(url, user, password);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA public CASCADE");
            statement.execute("CREATE SCHEMA public");
            statement.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");
            statement.execute("CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\"");
        } catch (SQLException e) {
            throw new PostgresTestDockerException(e);
        }
        ConnectorTestApplication.migrateDatabase(configuration);
        try (Connection connection = getConnection(url, user, password);
             Liquibase migrator = new Liquibase("it-migrations.xml", new ClassLoaderResourceAccessor(),
                     new JdbcConnection(connection))) {
            migrator.update("");
        } catch (LiquibaseException | SQLException e) {
            throw new PostgresTestDockerException(e);
        }
    }
}
