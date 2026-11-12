package uk.gov.pay.connector.client;

import com.google.common.base.Stopwatch;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecChainHandler;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.service.payments.logging.LoggingKeys.HTTP_STATUS;
import static uk.gov.service.payments.logging.LoggingKeys.MDC_REQUEST_ID_KEY;
import static uk.gov.service.payments.logging.LoggingKeys.METHOD;
import static uk.gov.service.payments.logging.LoggingKeys.RESPONSE_TIME;
import static uk.gov.service.payments.logging.LoggingKeys.URL;

/**
 * Passes the id of the request being handled on to the services the connector calls (as {@code X-Request-Id}, empty
 * when the request had none) and logs the start and end of every call. The content type of a request entity is sent
 * with the request's own headers, after the request id.
 */
public class RestClientLoggingInterceptor implements ExecChainHandler {

    private static final Logger logger = LoggerFactory.getLogger(RestClientLoggingInterceptor.class);
    private static final String HEADER_REQUEST_ID = "X-Request-Id";

    @SuppressWarnings("java:S3457")
    @Override
    public ClassicHttpResponse execute(ClassicHttpRequest request, ExecChain.Scope scope, ExecChain chain)
            throws IOException, HttpException {
        Stopwatch timer = Stopwatch.createStarted();
        String requestId = StringUtils.defaultString(MDC.get(MDC_REQUEST_ID_KEY));

        request.addHeader(HEADER_REQUEST_ID, requestId);
        HttpEntity entity = request.getEntity();
        if (entity != null && entity.getContentType() != null && !request.containsHeader(HttpHeaders.CONTENT_TYPE)) {
            request.addHeader(HttpHeaders.CONTENT_TYPE, entity.getContentType());
        }

        URI uri = uriOf(request);
        logger.info("[{}] - {} to {} began",
                requestId,
                request.getMethod(),
                uri,
                kv(METHOD, request.getMethod()),
                kv(URL, uri));

        ClassicHttpResponse response = chain.proceed(request, scope);

        long elapsed = timer.stop().elapsed(TimeUnit.MILLISECONDS);
        logger.info("[{}] - {} to {} ended - total time {}ms",
                requestId,
                request.getMethod(),
                uri,
                elapsed,
                kv(METHOD, request.getMethod()),
                kv(URL, uri),
                kv(HTTP_STATUS, response.getCode()),
                kv(RESPONSE_TIME, elapsed));
        return response;
    }

    private static URI uriOf(ClassicHttpRequest request) {
        try {
            return request.getUri();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
