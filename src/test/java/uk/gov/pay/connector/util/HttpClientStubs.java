package uk.gov.pay.connector.util;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;

import java.io.IOException;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * HTTP clients for unit tests that answer every request with the same response, handed to the caller's response
 * handler as a real HTTP client would hand it.
 */
public final class HttpClientStubs {

    private HttpClientStubs() {
    }

    public static CloseableHttpClient respondingWith(int status, String body, Map<String, String> headers) {
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        try {
            when(client.execute(any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class))).thenAnswer(invocation -> {
                HttpClientResponseHandler<?> handler = invocation.getArgument(1);
                return handler.handleResponse(response(status, body, headers));
            });
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return client;
    }

    public static BasicClassicHttpResponse response(int status, String body, Map<String, String> headers) {
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(status);
        if (body != null) {
            response.setEntity(new StringEntity(body, ContentType.TEXT_XML));
        }
        headers.forEach(response::addHeader);
        return response;
    }
}
