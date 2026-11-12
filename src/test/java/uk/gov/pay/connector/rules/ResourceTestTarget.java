package uk.gov.pay.connector.rules;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Arrays;

/**
 * A path of the resources under test, with the query parameters a test adds to it.
 */
public final class ResourceTestTarget {

    private final MockMvc mockMvc;
    private final UriComponentsBuilder uri;

    ResourceTestTarget(MockMvc mockMvc, String path) {
        this.mockMvc = mockMvc;
        this.uri = UriComponentsBuilder.fromUriString(path);
    }

    public ResourceTestTarget queryParam(String name, Object... values) {
        uri.queryParam(name, values);
        return this;
    }

    public ResourceTestRequest request() {
        return new ResourceTestRequest(mockMvc, uri.build().encode().toUriString(), null);
    }

    public ResourceTestRequest request(String... acceptedMediaTypes) {
        return new ResourceTestRequest(mockMvc, uri.build().encode().toUriString(), String.join(", ", acceptedMediaTypes));
    }

    public ResourceTestRequest request(MediaType... acceptedMediaTypes) {
        return request(Arrays.stream(acceptedMediaTypes).map(MediaType::toString).toArray(String[]::new));
    }
}
