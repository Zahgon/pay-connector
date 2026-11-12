package uk.gov.pay.connector.gateway;

import com.codahale.metrics.MetricRegistry;
import com.google.common.base.Stopwatch;
import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.GatewayException.GatewayConnectionTimeoutException;
import uk.gov.pay.connector.gateway.GatewayException.GatewayErrorException;
import uk.gov.pay.connector.gateway.GatewayException.GenericGatewayException;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.GatewayClientDeleteRequest;
import uk.gov.pay.connector.gateway.model.request.GatewayClientGetRequest;
import uk.gov.pay.connector.gateway.model.request.GatewayClientPostRequest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.HttpCookie;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;
import static java.util.Collections.emptyList;

public class GatewayClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayClient.class);

    private static final String QUERY_PARAMETER_CHARACTERS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~!$'()*,;:@/?";

    private final CloseableHttpClient client;
    private final MetricRegistry metricRegistry;

    private static final Counter gatewayOperationsFailures = Counter.build()
            .name("gateway_operations_failures_total")
            .help("Number of failed gateway operations")
            .labelNames("gatewayName", "gatewayAccountType", "requestType")
            .register();

    private static final Histogram gatewayOperationsResponseTime = Histogram.build()
            .name("gateway_operations_response_time_seconds")
            .help("Response times for gateway operations in seconds")
            .labelNames("gatewayName", "gatewayAccountType", "requestType")
            .register();

    public GatewayClient(CloseableHttpClient client, MetricRegistry metricRegistry) {
        this.client = client;
        this.metricRegistry = metricRegistry;
    }

    public Response postRequestFor(URI url, PaymentGatewayName gatewayName, String gatewayAccountType, GatewayOrder request, Map<String, String> headers)
            throws GenericGatewayException, GatewayErrorException, GatewayConnectionTimeoutException {
        return postRequestFor(url, gatewayName, gatewayAccountType, request, emptyList(), headers);
    }

    public Response postRequestFor(GatewayClientPostRequest request)
            throws GenericGatewayException, GatewayErrorException, GatewayConnectionTimeoutException {
        return postRequestFor(request.getUrl(), request.getPaymentProvider(), request.getGatewayAccountType(), request.getGatewayOrder(), emptyList(), request.getHeaders());
    }

    public Response postRequestFor(URI url,
                                   PaymentGatewayName gatewayName,
                                   String gatewayAccountType,
                                   GatewayOrder request,
                                   List<HttpCookie> cookies,
                                   Map<String, String> headers)
            throws GenericGatewayException, GatewayConnectionTimeoutException, GatewayErrorException {

        String metricsPrefix = format("gateway-operations.%s.%s.%s", gatewayName.getName(), gatewayAccountType, request.orderRequestType());

        RequestExecution requestCallable = () -> {
            LOGGER.info("POSTing request for account '{}' with type '{}'", gatewayName.getName(), gatewayAccountType);

            HttpPost httpRequest = new HttpPost(url);
            addHeaders(httpRequest, headers, cookies);
            Charset charset = request.mediaType().getCharset() == null ? StandardCharsets.UTF_8 : request.mediaType().getCharset();
            httpRequest.setEntity(new ByteArrayEntity(request.payload().getBytes(charset),
                    ContentType.parse(request.mediaType().toString()), true));
            return client.execute(httpRequest, Response::new);
        };
        return executeRequest(url, gatewayName, gatewayAccountType, request.orderRequestType(), metricsPrefix, requestCallable);
    }

    public Response getRequestFor(GatewayClientGetRequest request)
            throws GenericGatewayException, GatewayConnectionTimeoutException, GatewayErrorException {
        return getRequestFor(request.getUrl(), request.getPaymentProvider(), request.getGatewayAccountType(),
                request.getOrderRequestType(), emptyList(), request.getHeaders(), request.getQueryParams());
    }

    public Response deleteRequestFor(GatewayClientDeleteRequest request)
            throws GenericGatewayException, GatewayConnectionTimeoutException, GatewayErrorException {
        return deleteRequestFor(request.getUrl(), request.getPaymentProvider(), request.getGatewayAccountType(),
                request.getOrderRequestType(), emptyList(), request.getHeaders(), request.getQueryParams());
    }

    public Response getRequestFor(URI url,
                                  PaymentGatewayName gatewayName,
                                  String gatewayAccountType,
                                  OrderRequestType orderRequestType,
                                  List<HttpCookie> cookies,
                                  Map<String, String> headers,
                                  Map<String, String> queryParams)
            throws GenericGatewayException, GatewayConnectionTimeoutException, GatewayErrorException {

        String metricsPrefix = format("gateway-operations.get.%s.%s.%s", gatewayName.getName(), gatewayAccountType, orderRequestType);

        RequestExecution requestCallable = () -> {
            LOGGER.info("Making GET request for account '{}' with type '{}'", gatewayName.getName(), gatewayAccountType);

            HttpGet httpRequest = new HttpGet(withQueryParameters(url, queryParams));
            addHeaders(httpRequest, headers, cookies);
            return client.execute(httpRequest, Response::new);
        };

        return executeRequest(url, gatewayName, gatewayAccountType, orderRequestType, metricsPrefix, requestCallable);
    }

    public Response deleteRequestFor(URI url,
                                     PaymentGatewayName gatewayName,
                                     String gatewayAccountType,
                                     OrderRequestType orderRequestType,
                                     List<HttpCookie> cookies,
                                     Map<String, String> headers,
                                     Map<String, String> queryParams)
            throws GenericGatewayException, GatewayConnectionTimeoutException, GatewayErrorException {

        String metricsPrefix = format("gateway-operations.delete.%s.%s.%s", gatewayName.getName(), gatewayAccountType, orderRequestType);

        RequestExecution requestCallable = () -> {
            LOGGER.info("Making DELETE request for account '{}' with type '{}'", gatewayName.getName(), gatewayAccountType);

            HttpDelete httpRequest = new HttpDelete(withQueryParameters(url, queryParams));
            addHeaders(httpRequest, headers, cookies);
            return client.execute(httpRequest, Response::new);
        };

        return executeRequest(url, gatewayName, gatewayAccountType, orderRequestType, metricsPrefix, requestCallable);
    }

    private Response executeRequest(URI url,
                                    PaymentGatewayName gatewayName,
                                    String gatewayAccountType,
                                    OrderRequestType orderRequestType,
                                    String metricsPrefix,
                                    RequestExecution requestCallable)
            throws GenericGatewayException, GatewayConnectionTimeoutException, GatewayErrorException {

        Stopwatch responseTimeStopwatch = Stopwatch.createStarted();
        Histogram.Timer responseTimeTimer = gatewayOperationsResponseTime.labels(
                gatewayName.toString().toLowerCase(),
                gatewayAccountType.toLowerCase(),
                orderRequestType.toString().toLowerCase()
        ).startTimer();

        try {
            Response gatewayResponse = requestCallable.execute();
            int statusCode = gatewayResponse.getStatus();
            if (statusCode >= 200 && statusCode < 300) {
                return gatewayResponse;
            } else {
                if (statusCode >= 500) {
                    LOGGER.warn("Gateway returned unexpected status code: {}, for gateway url={} with type {} with order request type {}",
                            statusCode, url, gatewayAccountType, orderRequestType);
                    incrementFailureCounter(metricRegistry, metricsPrefix);
                    incrementPrometheusFailureCounter(gatewayName, gatewayAccountType, orderRequestType);
                } else {
                    LOGGER.warn("Gateway returned non-success status code: {}, for gateway url={} with type {} with order request type {}",
                            statusCode, url, gatewayAccountType, orderRequestType);
                }
                throw new GatewayErrorException("Non-success HTTP status code " + statusCode + " from gateway", gatewayResponse.getEntity(), statusCode);
            }
        } catch (IOException | UncheckedIOException e) {
            incrementFailureCounter(metricRegistry, metricsPrefix);
            incrementPrometheusFailureCounter(gatewayName, gatewayAccountType, orderRequestType);
            Throwable cause = e instanceof UncheckedIOException ? e.getCause() : e;
            if (cause instanceof SocketTimeoutException) {
                LOGGER.warn(format("Connection timed out error for gateway url=%s", url), e);
                throw new GatewayConnectionTimeoutException("Gateway connection timeout error");
            }
            LOGGER.warn(format("Exception for gateway url=%s, error message: %s", url, cause), e);
            throw new GenericGatewayException(cause.toString());
        } catch (GatewayErrorException e) {
            throw e;
        } catch (Exception e) {
            incrementFailureCounter(metricRegistry, metricsPrefix);
            incrementPrometheusFailureCounter(gatewayName, gatewayAccountType, orderRequestType);
            LOGGER.error(format("Exception for gateway url=%s", url), e);
            throw new GenericGatewayException(e.getMessage());
        } finally {
            responseTimeStopwatch.stop();
            metricRegistry.histogram(metricsPrefix + ".response_time").update(responseTimeStopwatch.elapsed(TimeUnit.MILLISECONDS));
            responseTimeTimer.observeDuration();
        }
    }

    private static void addHeaders(HttpUriRequestBase httpRequest, Map<String, String> headers, List<HttpCookie> cookies) {
        headers.keySet().forEach(headerKey -> httpRequest.addHeader(headerKey, headers.get(headerKey)));
        cookies.forEach(cookie -> httpRequest.addHeader("Cookie", cookie.getName() + "=" + cookie.getValue()));
    }

    /**
     * Appends the query parameters to the URL, encoding their names and values for a query: spaces as {@code +},
     * escape sequences kept as they are and every other character outside the query's character set percent-encoded.
     */
    private static URI withQueryParameters(URI url, Map<String, String> queryParams) {
        if (queryParams.isEmpty()) {
            return url;
        }
        StringBuilder uri = new StringBuilder(url.toString());
        char separator = url.getRawQuery() == null ? '?' : '&';
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            uri.append(separator).append(encodeQueryParameter(entry.getKey()))
                    .append('=').append(encodeQueryParameter(entry.getValue()));
            separator = '&';
        }
        return URI.create(uri.toString());
    }

    private static String encodeQueryParameter(String value) {
        StringBuilder encoded = new StringBuilder();
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < bytes.length; i++) {
            char c = (char) (bytes[i] & 0xff);
            if (c == ' ') {
                encoded.append('+');
            } else if (c < 0x80 && QUERY_PARAMETER_CHARACTERS.indexOf(c) >= 0) {
                encoded.append(c);
            } else if (c == '%' && i + 2 < bytes.length && isHex(bytes[i + 1]) && isHex(bytes[i + 2])) {
                encoded.append(c);
            } else {
                encoded.append(format("%%%02X", (int) c));
            }
        }
        return encoded.toString();
    }

    private static boolean isHex(byte b) {
        return Character.digit((char) b, 16) >= 0;
    }

    @FunctionalInterface
    private interface RequestExecution {
        Response execute() throws IOException;
    }

    private void incrementFailureCounter(MetricRegistry metricRegistry, String metricsPrefix) {
        metricRegistry.counter(metricsPrefix + ".failures").inc();
    }

    private void incrementPrometheusFailureCounter(PaymentGatewayName gatewayName, String gatewayAccountType, OrderRequestType orderRequestType) {
        gatewayOperationsFailures.labels(
                gatewayName.toString().toLowerCase(),
                gatewayAccountType.toLowerCase(),
                orderRequestType.toString().toLowerCase()
        ).inc();
    }

    public static class Response {
        private final int status;
        private final String entity;
        private final Map<String, String> responseCookies = new HashMap<>();

        protected Response(final ClassicHttpResponse delegate) throws IOException {
            this.status = delegate.getCode();
            try {
                this.entity = delegate.getEntity() == null ? "" : EntityUtils.toString(delegate.getEntity(), StandardCharsets.UTF_8);
            } catch (ParseException e) {
                throw new IOException(e);
            }
            for (Header header : delegate.getHeaders(HttpHeaders.SET_COOKIE)) {
                HttpCookie.parse(header.getValue()).forEach(cookie -> responseCookies.put(cookie.getName(), cookie.getValue()));
            }
        }

        public int getStatus() {
            return status;
        }

        public String getEntity() {
            return entity;
        }

        public Map<String, String> getResponseCookies() {
            return responseCookies;
        }
    }
}
