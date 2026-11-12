package uk.gov.pay.connector.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.freemarker.FreeMarkerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import uk.gov.pay.connector.app.commands.DatabaseMigrationCommand;
import uk.gov.pay.connector.app.commands.DependentResourceWaitCommand;
import uk.gov.pay.connector.command.RenderStateTransitionGraphCommand;
import uk.gov.pay.connector.app.config.ServerConfig;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;

/**
 * Entry point of the connector. The first arguments name the command to run, as they always have: {@code server} runs
 * the service, {@code db migrate} applies the Liquibase change log, {@code waitOnDependencies} waits until the
 * database accepts connections and {@code render-state-transition-graph} writes the payment state transitions as a
 * graphviz file. A trailing configuration file argument is accepted and ignored: the settings are those of
 * {@code config/config.yaml} on the class path, with the environment variables it names (see
 * {@link ConfigurationLoader}). Errors without an entity are rendered by the servlet container itself, so Spring
 * Boot's error controller stays off; migrations run only on request.
 */
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = {ErrorMvcAutoConfiguration.class, LiquibaseAutoConfiguration.class,
        DataSourceAutoConfiguration.class, FreeMarkerAutoConfiguration.class})
@ComponentScan(basePackages = "uk.gov.pay.connector", excludeFilters = {
        @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
        // The commands run in contexts of their own.
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "uk\\.gov\\.pay\\.connector\\.(app\\.commands|command)\\.[A-Za-z]*Command"),
        // The admin port's beans belong to the management context only.
        @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = ManagementContextConfiguration.class)})
public class ConnectorApp {

    private static final String USAGE = "usage: java -jar pay-connector-0.1-SNAPSHOT-allinone.jar "
            + "{server | db migrate | waitOnDependencies | render-state-transition-graph} [config file]";

    public static void main(String[] args) {
        List<String> arguments = List.of(args);
        if (startsWith(arguments, "server")) {
            run(ConnectorApp.class, WebApplicationType.SERVLET);
        } else if (startsWith(arguments, "db", "migrate")) {
            exit(run(DatabaseMigrationCommand.class, WebApplicationType.NONE));
        } else if (startsWith(arguments, "waitOnDependencies")) {
            exit(run(DependentResourceWaitCommand.class, WebApplicationType.NONE));
        } else if (startsWith(arguments, "render-state-transition-graph")) {
            exit(run(RenderStateTransitionGraphCommand.class, WebApplicationType.NONE));
        } else if (arguments.isEmpty()) {
            System.out.println(USAGE);
        } else {
            System.err.println(USAGE);
            System.exit(1);
        }
    }

    /**
     * Starts a context for the given configuration class with the service's settings as a bean and the ports and
     * addresses they name.
     */
    public static ConfigurableApplicationContext run(Class<?> source, WebApplicationType webApplicationType) {
        ConnectorConfiguration configuration = ConfigurationLoader.load();
        ServerConfig.Connector application = configuration.getServerConfig().getApplicationConnector();
        ServerConfig.Connector admin = configuration.getServerConfig().getAdminConnector();
        return new SpringApplicationBuilder(source)
                .web(webApplicationType)
                .properties(Map.of(
                        "server.port", application.getPort(),
                        "server.address", application.getBindHost(),
                        "management.server.port", adminPort(application, admin),
                        "management.server.address", admin.getBindHost()))
                .initializers(context -> context.getBeanFactory()
                        .registerSingleton("connectorConfiguration", configuration))
                .run();
    }

    /**
     * The admin port; when it is to be chosen at random (0) like the application port, a free port is chosen now, as
     * the two always are distinct servers.
     */
    private static int adminPort(ServerConfig.Connector application, ServerConfig.Connector admin) {
        if (admin.getPort() != 0 || application.getPort() != 0) {
            return admin.getPort();
        }
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName(admin.getBindHost()))) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException("No free port for the admin server", e);
        }
    }

    private static boolean startsWith(List<String> arguments, String... command) {
        return arguments.size() >= command.length && arguments.subList(0, command.length).equals(List.of(command));
    }

    private static void exit(ConfigurableApplicationContext context) {
        System.exit(SpringApplication.exit(context));
    }
}
