package uk.gov.pay.connector.it.gatewayclient;

import com.codahale.metrics.MetricRegistry;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.ConnectorTestApplication;
import uk.gov.pay.connector.gateway.ClientFactory;
import uk.gov.pay.connector.util.HttpClientInspector;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static uk.gov.pay.connector.gateway.GatewayOperation.AUTHORISE;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;

class ClientFactoryIT {

    private static final String DEFAULT_CONFIG = "config/test-it-config.yaml";
    private static final String PROXY_HOST_PROPERTY = "https.proxyHost";
    private static final String PROXY_PORT_PROPERTY = "https.proxyPort";

    private static final WireMockServer wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());

    private final MetricRegistry mockMetricRegistry = mock(MetricRegistry.class);
    private ConnectorConfiguration configuration;

    @BeforeAll
    static void startWireMock() {
        wireMockServer.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @BeforeEach
    void setup() {
        wireMockServer.resetAll();
    }

    @AfterEach
    void after() {
        System.clearProperty(PROXY_HOST_PROPERTY);
        System.clearProperty(PROXY_PORT_PROPERTY);
    }

    @Test
    void shouldProxyRequestToTargetServer_whenProxyEnabled() throws IOException {
        configuration = loadConfiguration(DEFAULT_CONFIG, true);

        wireMockServer.stubFor(get(urlPathEqualTo("/hello"))
                .willReturn(aResponse().withBody("world").withStatus(200)));

        try (CloseableHttpClient client = new ClientFactory(configuration)
                .createHttpClient(WORLDPAY, AUTHORISE, mockMetricRegistry)) {

            client.execute(new HttpGet(getServerUrl() + "/hello"), response -> response.getCode());

            wireMockServer.verify(1, getRequestedFor(urlEqualTo("/hello")));
            assertEquals(90000, readTimeoutOf(client));
        }
    }

    @Test
    void shouldNotProxyRequestToTargetServer_whenProxyDisabled() throws IOException {
        configuration = loadConfiguration(DEFAULT_CONFIG, false);

        wireMockServer.stubFor(get(urlPathEqualTo("/hello"))
                .willReturn(aResponse().withBody("world").withStatus(200)));

        try (CloseableHttpClient client = new ClientFactory(configuration)
                .createHttpClient(WORLDPAY, AUTHORISE, mockMetricRegistry)) {

            client.execute(new HttpGet(getServerUrl() + "/hello"), response -> response.getCode());

            wireMockServer.verify(1, getRequestedFor(urlEqualTo("/hello")));
            assertEquals(90000, readTimeoutOf(client));
        }
    }

    @Test
    void anHttpRequestShouldTimeOut_whenCustomReadTimeoutIsConfigured() throws IOException {
        configuration = loadConfiguration("config/client-factory-test-config.yaml", false);

        wireMockServer.stubFor(get(urlPathEqualTo("/hello"))
                .willReturn(aResponse().withBody("world").withStatus(200).withFixedDelay(2000)));

        try (CloseableHttpClient client = new ClientFactory(configuration)
                .createHttpClient(WORLDPAY, AUTHORISE, mockMetricRegistry)) {

            /*
            The read timeout is the longest the client waits for data once the request is sent. Within this test the
            connection is established at once, and there is some overhead in the overall request process which is
            hard to quantify (on a dev laptop the overhead was about 140ms). In order to avoid random test failures,
            we've chosen a generous overhead value: we assert that the request timed out, in less than the configured
            read timeout plus the overhead.
            */
            final long connectionOverheadInMillis = 1000;
            long expectedTimeout = configuration.getCustomJerseyClient().getReadTimeout().toMillis()
                    + connectionOverheadInMillis;

            assertTimesOutWithin(client, expectedTimeout);
        }
    }

    @Test
    void shouldUseGatewaySpecificReadTimeoutOverride_whenSpecified() throws IOException {
        configuration = loadConfiguration("config/client-factory-test-config-with-worldpay-timeout-override.yaml", false);

        wireMockServer.stubFor(get(urlPathEqualTo("/hello"))
                .willReturn(aResponse().withBody("world").withStatus(200).withFixedDelay(2000)));

        try (CloseableHttpClient client = new ClientFactory(configuration)
                .createHttpClient(WORLDPAY, AUTHORISE, mockMetricRegistry)) {

            long authOverriddenTimeout = configuration.getWorldpayConfig().getJerseyClientOverrides()
                    .map(override -> override.getAuth().getReadTimeout().toMillis())
                    .orElse(0L);
            final long connectionOverheadInMillis = 1000;
            assertTimesOutWithin(client, authOverriddenTimeout + connectionOverheadInMillis);
        }
    }

    private void assertTimesOutWithin(CloseableHttpClient client, long expectedTimeout) {
        long startTime = System.currentTimeMillis();
        IOException exception = assertThrows(IOException.class,
                () -> client.execute(new HttpGet(getServerUrl() + "/hello"), response -> response.getCode()));
        long actualDuration = System.currentTimeMillis() - startTime;

        assertThat(exception, instanceOf(SocketTimeoutException.class));
        assertThat(actualDuration, lessThan(expectedTimeout));
    }

    private static long readTimeoutOf(CloseableHttpClient client) {
        return HttpClientInspector.find(client, RequestConfig.class).orElseThrow().getResponseTimeout().toMilliseconds();
    }

    private ConnectorConfiguration loadConfiguration(String configuration, boolean proxyEnabled) {
        if (proxyEnabled) {
            System.setProperty(PROXY_HOST_PROPERTY, "localhost");
            System.setProperty(PROXY_PORT_PROPERTY, String.valueOf(wireMockServer.port()));
        } else {
            System.clearProperty(PROXY_HOST_PROPERTY);
            System.clearProperty(PROXY_PORT_PROPERTY);
        }
        return ConnectorTestApplication.loadConfiguration(configuration, List.of());
    }

    private String getServerUrl() {
        return format("http://localhost:%s", wireMockServer.port());
    }
}
