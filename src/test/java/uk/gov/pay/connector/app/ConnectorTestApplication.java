package uk.gov.pay.connector.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.text.lookup.StringLookupFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import uk.gov.pay.connector.app.commands.DatabaseMigrationCommand;
import uk.gov.pay.connector.app.config.ServerConfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Starts the connector for tests: with the settings of a configuration file on the test class path (environment
 * variables substituted as for the service), changed by the test's {@link ConfigOverride}s, and with any
 * configuration classes whose beans replace the service's own (marked {@code @Primary}).
 */
public final class ConnectorTestApplication implements AutoCloseable {

    private final ConnectorConfiguration configuration;
    private final ConfigurableApplicationContext context;

    private ConnectorTestApplication(ConnectorConfiguration configuration, ConfigurableApplicationContext context) {
        this.configuration = configuration;
        this.context = context;
    }

    public static ConnectorTestApplication start(String configResource, List<ConfigOverride> overrides,
                                                 Class<?>... overridingConfigurations) {
        ConnectorConfiguration configuration = loadConfiguration(configResource, overrides);
        ServerConfig.Connector application = configuration.getServerConfig().getApplicationConnector();
        ServerConfig.Connector admin = configuration.getServerConfig().getAdminConnector();
        Class<?>[] sources = Stream.concat(Stream.of(ConnectorApp.class), Stream.of(overridingConfigurations))
                .toArray(Class<?>[]::new);
        ConfigurableApplicationContext context = new SpringApplicationBuilder(sources)
                .web(WebApplicationType.SERVLET)
                .properties(Map.of(
                        "server.port", application.getPort(),
                        "server.address", application.getBindHost(),
                        // the admin port must differ from the application port to get a server of its own
                        "management.server.port", admin.getPort() == 0 ? freePort() : admin.getPort(),
                        "management.server.address", admin.getBindHost()))
                .initializers(applicationContext -> {
                    applicationContext.getBeanFactory().registerSingleton("connectorConfiguration", configuration);
                    applicationContext.getBeanFactory().registerSingleton(TestConfigurationExcludeFilter.class.getName(),
                            new TestConfigurationExcludeFilter());
                })
                .run();
        return new ConnectorTestApplication(configuration, context);
    }

    /**
     * Applies the change log to the database the configuration names, as the service's {@code db migrate} does.
     */
    public static void migrateDatabase(ConnectorConfiguration configuration) {
        new SpringApplicationBuilder(DatabaseMigrationCommand.class)
                .web(WebApplicationType.NONE)
                .initializers(applicationContext -> applicationContext.getBeanFactory()
                        .registerSingleton("connectorConfiguration", configuration))
                .run()
                .close();
    }

    public static ConnectorConfiguration loadConfiguration(String configResource, List<ConfigOverride> overrides) {
        YAMLMapper yamlMapper = new YAMLMapper();
        try (InputStream in = ConnectorTestApplication.class.getClassLoader().getResourceAsStream(configResource)) {
            if (in == null) {
                throw new IllegalArgumentException(configResource + " is not on the test class path");
            }
            // variables are substituted first, as the service substitutes them, so that a default such as [] is YAML
            StringSubstitutor substitutor = new StringSubstitutor(StringLookupFactory.INSTANCE.environmentVariableStringLookup());
            String yaml = substitutor.replace(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            ObjectNode document = (ObjectNode) yamlMapper.readTree(yaml);
            for (ConfigOverride override : overrides) {
                set(document, override.key(), override.value());
            }
            return ConfigurationLoader.parse(yamlMapper.writeValueAsString(document));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void set(ObjectNode document, String key, String value) {
        String[] path = key.split("\\.");
        ObjectNode node = document;
        for (int i = 0; i < path.length - 1; i++) {
            JsonNode child = node.get(path[i]);
            node = child instanceof ObjectNode objectNode ? objectNode : node.putObject(path[i]);
        }
        node.set(path[path.length - 1], new TextNode(value));
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public ConnectorConfiguration getConfiguration() {
        return configuration;
    }

    public ConfigurableApplicationContext getApplicationContext() {
        return context;
    }

    public int getLocalPort() {
        return ((WebServerApplicationContext) context).getWebServer().getPort();
    }

    public int getAdminPort() {
        return context.getEnvironment().getRequiredProperty("local.management.port", Integer.class);
    }

    public <T> T getBean(Class<T> type) {
        return context.getBean(type);
    }

    @Override
    public void close() {
        context.close();
    }
}
