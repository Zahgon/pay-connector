package uk.gov.pay.connector.app.commands;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.util.ApplicationStartupDependentResource;
import uk.gov.pay.connector.util.ApplicationStartupDependentResourceChecker;

/**
 * The {@code waitOnDependencies} command: waits for dependent resources (the database) to become available.
 */
@Configuration(proxyBeanMethods = false)
public class DependentResourceWaitCommand implements ApplicationRunner {

    private final ConnectorConfiguration configuration;

    public DependentResourceWaitCommand(ConnectorConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public void run(ApplicationArguments args) {
        new ApplicationStartupDependentResourceChecker(new ApplicationStartupDependentResource(configuration))
                .checkAndWaitForResources();
    }
}
