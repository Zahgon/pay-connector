package uk.gov.pay.connector.rules;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * A request to the resources under test: its headers, and the method that sends it with or without an entity.
 */
public final class ResourceTestRequest {

    private final MockMvc mockMvc;
    private final String uri;
    private final List<Map.Entry<String, String>> headers = new ArrayList<>();

    ResourceTestRequest(MockMvc mockMvc, String uri, String accept) {
        this.mockMvc = mockMvc;
        this.uri = uri;
        if (accept != null) {
            headers.add(Map.entry(HttpHeaders.ACCEPT, accept));
        }
    }

    public ResourceTestRequest header(String name, Object value) {
        headers.add(Map.entry(name, String.valueOf(value)));
        return this;
    }

    public ResourceTestResponse get() {
        return method("GET");
    }

    public ResourceTestResponse delete() {
        return method("DELETE");
    }

    public ResourceTestResponse post(ResourceTestEntity entity) {
        return method("POST", entity);
    }

    public ResourceTestResponse put(ResourceTestEntity entity) {
        return method("PUT", entity);
    }

    public ResourceTestResponse method(String method) {
        return method(method, null);
    }

    public ResourceTestResponse method(String method, ResourceTestEntity entity) {
        MockHttpServletRequestBuilder builder = request(HttpMethod.valueOf(method), URI.create(uri));
        headers.forEach(header -> builder.header(header.getKey(), header.getValue()));
        if (entity != null) {
            builder.contentType(entity.mediaType()).content(entity.body());
        }
        try {
            return new ResourceTestResponse(mockMvc.perform(builder).andReturn().getResponse());
        } catch (Exception e) {
            throw new IllegalStateException("The request " + method + " " + uri + " failed", e);
        }
    }
}
