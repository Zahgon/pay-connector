package uk.gov.pay.connector.gateway;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.httpclient5.InstrumentedHttpClientConnectionManager;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.ManagedHttpClientConnectionFactory;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.OperationOverrides;
import uk.gov.pay.connector.client.RestClientLoggingInterceptor;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.lang.String.format;

@Component
public class ClientFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientFactory.class);

    private final ConnectorConfiguration conf;
    private final List<CloseableHttpClient> clients = new CopyOnWriteArrayList<>();

    private final static String PROXY_HOST_PROPERTY = "https.proxyHost";
    private final static String PROXY_PORT_PROPERTY = "https.proxyPort";

    @Inject
    public ClientFactory(ConnectorConfiguration conf) {
        this.conf = conf;
    }

    public CloseableHttpClient createHttpClient(PaymentGatewayName gateway, GatewayOperation operation, MetricRegistry metricRegistry) {
        return createHttpClient(gateway, getReadTimeout(operation, gateway), operation.getConfigKey(), metricRegistry);
    }

    public CloseableHttpClient createHttpClient(PaymentGatewayName gateway, MetricRegistry metricRegistry) {
        return createHttpClient(gateway, conf.getCustomJerseyClient().getReadTimeout(), "all", metricRegistry);
    }

    private CloseableHttpClient createHttpClient(PaymentGatewayName gateway, Duration readTimeout, String metricName, MetricRegistry metricRegistry) {
        PoolingHttpClientConnectionManager connectionManager = createConnectionManager(gateway.getName(), metricName,
                metricRegistry, conf.getCustomJerseyClient().getConnectionTTL());

        HttpClientBuilder clientBuilder = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setResponseTimeout(Timeout.ofMilliseconds(readTimeout.toMillis()))
                        .build())
                .disableCookieManagement()
                .addExecInterceptorFirst("request-logging", new RestClientLoggingInterceptor());

        if (System.getProperty(PROXY_HOST_PROPERTY) != null && System.getProperty(PROXY_PORT_PROPERTY) != null) {
            clientBuilder.setProxy(new HttpHost("http", System.getProperty(PROXY_HOST_PROPERTY),
                    Integer.parseInt(System.getProperty(PROXY_PORT_PROPERTY))));
        }

        CloseableHttpClient client = clientBuilder.build();
        clients.add(client);
        return client;
    }

    /**
     * Closes the clients this factory made, with their connection pools, when the application stops.
     */
    @PreDestroy
    public void closeClients() {
        for (CloseableHttpClient client : clients) {
            try {
                client.close();
            } catch (IOException e) {
                LOGGER.warn("Failed to close an HTTP client: {}", e.getMessage());
            }
        }
        clients.clear();
    }

    private Duration getReadTimeout(GatewayOperation operation, PaymentGatewayName gateway) {
        return getOverridesFor(operation, gateway)
                .map(OperationOverrides::getReadTimeout)
                .orElse(conf.getCustomJerseyClient().getReadTimeout());
    }

    private Optional<OperationOverrides> getOverridesFor(GatewayOperation operation, PaymentGatewayName gateway) {
        if (gateway.equals(PaymentGatewayName.STRIPE)) return Optional.empty();
        return conf.getGatewayConfigFor(gateway)
                .getJerseyClientOverrides()
                .map(jerseyClientOverrides -> jerseyClientOverrides.getOverridesFor(operation));
    }

    private PoolingHttpClientConnectionManager createConnectionManager(String gatewayName, String operation,
                                                                       MetricRegistry metricRegistry,
                                                                       Duration connectionTimeToLive) {

        SSLConnectionSocketFactory sslConnectionSocketFactory;

        try {
            sslConnectionSocketFactory = new SSLConnectionSocketFactory(
                    SSLContext.getDefault(),
                    new String[]{"TLSv1.3"},
                    null,
                    null
            );
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Unable to create SSL connection socket factory", e);
        }

        TimeValue timeToLive = TimeValue.ofMilliseconds(connectionTimeToLive.toMillis());
        PoolingHttpClientConnectionManager connectionManager = InstrumentedHttpClientConnectionManager.builder(metricRegistry)
                .socketFactoryRegistry(RegistryBuilder.<ConnectionSocketFactory>create()
                        .register("http", PlainConnectionSocketFactory.getSocketFactory())
                        .register("https", sslConnectionSocketFactory)
                        .build())
                .connFactory(new ManagedHttpClientConnectionFactory())
                .dnsResolver(SystemDefaultDnsResolver.INSTANCE)
                .timeToLive(timeToLive)
                .name(format("%s.%s", gatewayName, operation)).build();
        connectionManager.setDefaultConnectionConfig(ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(conf.getClientConfiguration().getConnectionTimeout().toMillis()))
                .setTimeToLive(timeToLive)
                .build());
        return connectionManager;
    }
}
