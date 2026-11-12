package uk.gov.pay.connector.healthcheck;

import com.codahale.metrics.health.HealthCheck;
import uk.gov.pay.connector.app.config.DatabaseConfig;

import java.sql.Connection;
import java.sql.DriverManager;

/**
 * Healthy while a new, read-only connection to the configured database can be opened and validated.
 */
public class DatabaseHealthCheck extends HealthCheck {

    private final DatabaseConfig databaseConfig;

    public DatabaseHealthCheck(DatabaseConfig databaseConfig) {
        this.databaseConfig = databaseConfig;
    }

    @Override
    protected Result check() {
        try (Connection connection = DriverManager.getConnection(
                databaseConfig.getUrl(),
                databaseConfig.getUser(),
                databaseConfig.getPassword())) {
            connection.setReadOnly(true);

            return connection.isValid(2)
                    ? Result.healthy()
                    : Result.unhealthy("Could not validate the DB connection.");
        } catch (Exception e) {
            return Result.unhealthy(e);
        }
    }
}
