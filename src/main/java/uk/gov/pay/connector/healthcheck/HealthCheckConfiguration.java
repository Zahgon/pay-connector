package uk.gov.pay.connector.healthcheck;

import com.codahale.metrics.health.HealthCheckRegistry;
import com.codahale.metrics.health.jvm.ThreadDeadlockHealthCheck;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.pay.connector.app.ConnectorConfiguration;

/**
 * Registers the service's health checks, which the admin port's {@code /healthcheck} and the application's
 * {@code /healthcheck} report: {@code ping}, {@code database}, {@code cardExecutorService}, {@code sqsQueue} and
 * {@code deadlocks}.
 */
@Configuration(proxyBeanMethods = false)
public class HealthCheckConfiguration {

    @Bean
    public InitializingBean healthChecks(HealthCheckRegistry healthCheckRegistry,
                                         ConnectorConfiguration configuration,
                                         CardExecutorServiceHealthCheck cardExecutorServiceHealthCheck,
                                         SQSHealthCheck sqsHealthCheck) {
        return () -> {
            healthCheckRegistry.register("deadlocks", new ThreadDeadlockHealthCheck());
            healthCheckRegistry.register("ping", new Ping());
            healthCheckRegistry.register("database", new DatabaseHealthCheck(configuration.getDatabaseConfig()));
            healthCheckRegistry.register("cardExecutorService", cardExecutorServiceHealthCheck);
            healthCheckRegistry.register("sqsQueue", sqsHealthCheck);
        };
    }
}
