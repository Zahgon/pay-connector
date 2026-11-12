package uk.gov.pay.connector.util;

import uk.gov.pay.connector.app.ConnectorConfiguration;

import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.springframework.stereotype.Component;

@Component
public class ApplicationStartupDependentResource {

    private final ConnectorConfiguration configuration;

    @Inject
    public ApplicationStartupDependentResource(ConnectorConfiguration configuration) {
        this.configuration = configuration;
    }

    public Connection getDatabaseConnection() throws SQLException {
        return DriverManager.getConnection(
                configuration.getDatabaseConfig().getUrl(),
                configuration.getDatabaseConfig().getUser(),
                configuration.getDatabaseConfig().getPassword());
    }

    public void sleep(long durationSeconds) {
        try {
            Thread.sleep(durationSeconds);
        } catch (InterruptedException ignored) {}
    }

}
