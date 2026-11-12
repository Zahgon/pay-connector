package uk.gov.pay.connector.rules;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.mock.web.MockHttpServletResponse;
import uk.gov.pay.connector.util.ObjectMapperFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * The answer of the resources under test: its status, headers and entity, read as a string or as JSON.
 */
public final class ResourceTestResponse implements AutoCloseable {

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.newObjectMapper();

    private final MockHttpServletResponse response;

    ResourceTestResponse(MockHttpServletResponse response) {
        this.response = response;
    }

    public int getStatus() {
        return response.getStatus();
    }

    public String getHeaderString(String name) {
        return response.getHeader(name);
    }

    public String getContentType() {
        return response.getContentType();
    }

    public <T> T readEntity(Class<T> type) {
        byte[] body = response.getContentAsByteArray();
        if (type == String.class) {
            return type.cast(new String(body, StandardCharsets.UTF_8));
        }
        try {
            return OBJECT_MAPPER.readValue(body, type);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public <T> T readEntity(TypeReference<T> type) {
        try {
            return OBJECT_MAPPER.readValue(response.getContentAsByteArray(), type);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        // the whole answer is held in memory: nothing to release
    }
}
