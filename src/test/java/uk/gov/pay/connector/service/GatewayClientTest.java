package uk.gov.pay.connector.service;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.google.common.collect.ImmutableList;
import io.prometheus.client.CollectorRegistry;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.OrderRequestType;

import java.io.IOException;
import java.net.HttpCookie;
import java.net.ServerSocket;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;

@ExtendWith(MockitoExtension.class)
public class GatewayClientTest {

    private static final String ORDER_PATH = "/worldpay/order";

    private final WireMockServer gateway = new WireMockServer(wireMockConfig().dynamicPort());
    private final CloseableHttpClient httpClient = HttpClients.createDefault();
    private URI worldpayApiEndpoint;
    private GatewayClient gatewayClient;

    private final String orderPayload = "a-sample-payload";

    private final MediaType mediaType = MediaType.APPLICATION_XML;

    private final CollectorRegistry collectorRegistry = CollectorRegistry.defaultRegistry;
    private final String[] labelNames = new String[]{"gatewayName", "gatewayAccountType", "requestType"};

    @Mock
    private MetricRegistry mockMetricRegistry;
    @Mock
    private Histogram mockResponseTimeHistogram;
    @Mock
    private Counter mockFailureCounter;

    @BeforeEach
    public void setup() {
        gateway.start();
        worldpayApiEndpoint = URI.create("http://localhost:" + gateway.port() + ORDER_PATH);
        gatewayClient = new GatewayClient(httpClient, mockMetricRegistry);
    }

    @AfterEach
    public void tearDown() throws IOException {
        gateway.stop();
        httpClient.close();
    }

    @Test
    public void shouldReturnGatewayErrorWhenProviderFails() {
        setupPostRequestMocks();
        gatewayAnswers(500);
        when(mockMetricRegistry.counter("gateway-operations.worldpay.test.authorise.failures")).thenReturn(mockFailureCounter);
        double failureCounterBefore = getMetricSample("gateway_operations_failures_total", new String[]{"worldpay", "test", "authorise"});
        double histogramCountBefore = getMetricSample("gateway_operations_response_time_seconds_count", new String[]{"worldpay", "test", "authorise"});

        assertThrows(GatewayException.GatewayErrorException.class,
                () -> gatewayClient.postRequestFor(worldpayApiEndpoint, WORLDPAY, "test", order(), emptyMap()));

        double failureCounterAfter = getMetricSample("gateway_operations_failures_total", new String[]{"worldpay", "test", "authorise"});
        double histogramCountAfter = getMetricSample("gateway_operations_response_time_seconds_count", new String[]{"worldpay", "test", "authorise"});
        assertEquals(failureCounterBefore + 1, failureCounterAfter);
        assertEquals(histogramCountBefore + 1, histogramCountAfter);
        verify(mockFailureCounter).inc();
    }

    @Test
    public void shouldReturnGatewayErrorWhenProviderFailsWithAnIOException() throws IOException {
        setupPostRequestMocks();
        URI unreachable = unreachableEndpoint();
        when(mockMetricRegistry.counter("gateway-operations.worldpay.test.authorise.failures")).thenReturn(mockFailureCounter);
        double failureCounterBefore = getMetricSample("gateway_operations_failures_total", new String[]{"worldpay", "test", "authorise"});
        double histogramCountBefore = getMetricSample("gateway_operations_response_time_seconds_count", new String[]{"worldpay", "test", "authorise"});

        assertThrows(GatewayException.GenericGatewayException.class,
                () -> gatewayClient.postRequestFor(unreachable, WORLDPAY, "test", order(), emptyMap()));

        double failureCounterAfter = getMetricSample("gateway_operations_failures_total", new String[]{"worldpay", "test", "authorise"});
        double histogramCountAfter = getMetricSample("gateway_operations_response_time_seconds_count", new String[]{"worldpay", "test", "authorise"});
        assertEquals(failureCounterBefore + 1, failureCounterAfter);
        assertEquals(histogramCountBefore + 1, histogramCountAfter);
        verify(mockFailureCounter).inc();
    }

    @Test
    public void shouldIncludeCookieIfSessionIdentifierAvailableInOrder() throws Exception {
        setupPostRequestMocks();
        gatewayAnswers(200);
        double histogramCountBefore = getMetricSample("gateway_operations_response_time_seconds_count", new String[]{"worldpay", "test", "authorise"});

        gatewayClient.postRequestFor(worldpayApiEndpoint, WORLDPAY, "test", order(),
                ImmutableList.of(new HttpCookie("machine", "value")), emptyMap());

        gateway.verify(postRequestedFor(urlPathEqualTo(ORDER_PATH))
                .withHeader("Cookie", equalTo("machine=value"))
                .withHeader("Content-Type", equalTo(mediaType.toString()))
                .withRequestBody(equalTo(orderPayload)));
        verify(mockResponseTimeHistogram).update(anyLong());

        double histogramCountAfter = getMetricSample("gateway_operations_response_time_seconds_count", new String[]{"worldpay", "test", "authorise"});
        assertEquals(histogramCountBefore + 1, histogramCountAfter);
    }

    @Test
    public void getRequestShouldReturnGatewayErrorWhenProviderFails() {
        setupGetRequestMocks();
        gatewayAnswers(500);
        when(mockMetricRegistry.counter("gateway-operations.get.worldpay.test.query.failures")).thenReturn(mockFailureCounter);
        double failureCounterBefore = getMetricSample("gateway_operations_failures_total", new String[]{"worldpay", "test", "query"});
        double histogramCountBefore = getMetricSample("gateway_operations_response_time_seconds_count", new String[]{"worldpay", "test", "query"});

        assertThrows(GatewayException.GatewayErrorException.class,
                () -> gatewayClient.getRequestFor(worldpayApiEndpoint, WORLDPAY, "test", OrderRequestType.QUERY, emptyList(), emptyMap(), emptyMap()));

        double failureCounterAfter = getMetricSample("gateway_operations_failures_total", new String[]{"worldpay", "test", "query"});
        double histogramCountAfter = getMetricSample("gateway_operations_response_time_seconds_count", new String[]{"worldpay", "test", "query"});
        assertEquals(failureCounterBefore + 1, failureCounterAfter);
        assertEquals(histogramCountBefore + 1, histogramCountAfter);
        verify(mockFailureCounter).inc();
    }

    @Test
    public void getRequestShouldReturnGatewayErrorWhenProviderFailsWithAnIOException() throws IOException {
        setupGetRequestMocks();
        URI unreachable = unreachableEndpoint();
        when(mockMetricRegistry.counter("gateway-operations.get.worldpay.test.query.failures")).thenReturn(mockFailureCounter);
        double failureCounterBefore = getMetricSample("gateway_operations_failures_total", new String[]{"worldpay", "test", "query"});
        double histogramCountBefore = getMetricSample("gateway_operations_response_time_seconds_count", new String[]{"worldpay", "test", "query"});

        assertThrows(GatewayException.GenericGatewayException.class,
                () -> gatewayClient.getRequestFor(unreachable, WORLDPAY, "test", OrderRequestType.QUERY, emptyList(), emptyMap(), emptyMap()));
        verify(mockFailureCounter).inc();

        double failureCounterAfter = getMetricSample("gateway_operations_failures_total", new String[]{"worldpay", "test", "query"});
        double histogramCountAfter = getMetricSample("gateway_operations_response_time_seconds_count", new String[]{"worldpay", "test", "query"});
        assertEquals(failureCounterBefore + 1, failureCounterAfter);
        assertEquals(histogramCountBefore + 1, histogramCountAfter);
    }

    @Test
    public void getRequestShouldIncludeCookieIfSessionIdentifierAvailableInOrder() throws Exception {
        setupGetRequestMocks();
        gatewayAnswers(200);
        double histogramCountBefore = getMetricSample("gateway_operations_response_time_seconds_count", new String[]{"worldpay", "test", "query"});

        gatewayClient.getRequestFor(worldpayApiEndpoint, WORLDPAY, "test", OrderRequestType.QUERY,
                ImmutableList.of(new HttpCookie("machine", "value")), emptyMap(), emptyMap());

        gateway.verify(getRequestedFor(urlPathEqualTo(ORDER_PATH)).withHeader("Cookie", equalTo("machine=value")));
        verify(mockResponseTimeHistogram).update(anyLong());

        double histogramCountAfter = getMetricSample("gateway_operations_response_time_seconds_count", new String[]{"worldpay", "test", "query"});
        assertEquals(histogramCountBefore + 1, histogramCountAfter);
    }

    @Test
    void getRequestShouldIncludeQueryParamsIfAvailable() throws Exception {
        setupGetRequestMocks();
        gatewayAnswers(200);

        Map<String, String> queryParams = Map.of(
                "foo", "bar",
                "foo1", "bar1"
        );
        gatewayClient.getRequestFor(worldpayApiEndpoint, WORLDPAY, "test", OrderRequestType.QUERY,
                ImmutableList.of(new HttpCookie("machine", "value")), emptyMap(), queryParams);

        gateway.verify(getRequestedFor(urlPathEqualTo(ORDER_PATH))
                .withQueryParam("foo", equalTo("bar"))
                .withQueryParam("foo1", equalTo("bar1")));
    }

    @Test
    void deleteRequestShouldIncludeQueryParamsIfAvailable() throws Exception {
        setupDeleteRequestMocks();
        gatewayAnswers(200);

        Map<String, String> queryParams = Map.of(
                "foo", "bar",
                "foo1", "bar1"
        );

        gatewayClient.deleteRequestFor(worldpayApiEndpoint, WORLDPAY, "test", OrderRequestType.QUERY,
                List.of(new HttpCookie("machine", "value")), emptyMap(), queryParams);

        gateway.verify(deleteRequestedFor(urlPathEqualTo(ORDER_PATH))
                .withHeader("Cookie", equalTo("machine=value"))
                .withQueryParam("foo", equalTo("bar"))
                .withQueryParam("foo1", equalTo("bar1")));
        verify(mockResponseTimeHistogram).update(anyLong());
    }

    @Test
    void deleteRequestShouldReturnGatewayErrorWhenProviderFails() {
        setupDeleteRequestMocks();
        gatewayAnswers(500);
        when(mockMetricRegistry.counter("gateway-operations.delete.worldpay.test.query.failures")).thenReturn(mockFailureCounter);
        double failureCounterBefore = getMetricSample("gateway_operations_failures_total", new String[]{"worldpay", "test", "query"});
        double histogramCountBefore = getMetricSample("gateway_operations_response_time_seconds_count", new String[]{"worldpay", "test", "query"});

        assertThrows(GatewayException.GatewayErrorException.class,
                () -> gatewayClient.deleteRequestFor(worldpayApiEndpoint, WORLDPAY, "test", OrderRequestType.QUERY, emptyList(), emptyMap(), emptyMap()));

        double failureCounterAfter = getMetricSample("gateway_operations_failures_total", new String[]{"worldpay", "test", "query"});
        double histogramCountAfter = getMetricSample("gateway_operations_response_time_seconds_count", new String[]{"worldpay", "test", "query"});
        assertEquals(failureCounterBefore + 1, failureCounterAfter);
        assertEquals(histogramCountBefore + 1, histogramCountAfter);
        verify(mockFailureCounter).inc();
    }

    @Test
    void deleteRequestShouldReturnGatewayErrorWhenProviderFailsWithAnIOException() throws IOException {
        setupDeleteRequestMocks();
        URI unreachable = unreachableEndpoint();
        when(mockMetricRegistry.counter("gateway-operations.delete.worldpay.test.query.failures")).thenReturn(mockFailureCounter);
        double failureCounterBefore = getMetricSample("gateway_operations_failures_total", new String[]{"worldpay", "test", "query"});
        double histogramCountBefore = getMetricSample("gateway_operations_response_time_seconds_count", new String[]{"worldpay", "test", "query"});

        assertThrows(GatewayException.GenericGatewayException.class,
                () -> gatewayClient.deleteRequestFor(unreachable, WORLDPAY, "test", OrderRequestType.QUERY, emptyList(), emptyMap(), emptyMap()));
        verify(mockFailureCounter).inc();

        double failureCounterAfter = getMetricSample("gateway_operations_failures_total", new String[]{"worldpay", "test", "query"});
        double histogramCountAfter = getMetricSample("gateway_operations_response_time_seconds_count", new String[]{"worldpay", "test", "query"});
        assertEquals(failureCounterBefore + 1, failureCounterAfter);
        assertEquals(histogramCountBefore + 1, histogramCountAfter);
    }

    private GatewayOrder order() {
        return new GatewayOrder(OrderRequestType.AUTHORISE, orderPayload, mediaType);
    }

    private void gatewayAnswers(int status) {
        gateway.stubFor(any(urlPathEqualTo(ORDER_PATH)).willReturn(aResponse().withStatus(status).withBody("<reply/>")));
    }

    /**
     * An address on this host where nothing listens: connecting to it fails.
     */
    private static URI unreachableEndpoint() throws IOException {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        return URI.create("http://localhost:" + port + ORDER_PATH);
    }

    private void setupPostRequestMocks() {
        when(mockMetricRegistry.histogram("gateway-operations.worldpay.test.authorise.response_time")).thenReturn(mockResponseTimeHistogram);
    }

    private void setupGetRequestMocks() {
        when(mockMetricRegistry.histogram("gateway-operations.get.worldpay.test.query.response_time")).thenReturn(mockResponseTimeHistogram);
    }

    private void setupDeleteRequestMocks() {
        when(mockMetricRegistry.histogram("gateway-operations.delete.worldpay.test.query.response_time")).thenReturn(mockResponseTimeHistogram);
    }

    private double getMetricSample(String name, String[] labelValues) {
        return Optional.ofNullable(collectorRegistry.getSampleValue(name, labelNames, labelValues)).orElse(0.0);
    }
}
