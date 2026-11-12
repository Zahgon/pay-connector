package uk.gov.pay.connector.rules;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import uk.gov.pay.connector.util.ObjectMapperFactory;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * The entity of a request to the resources under test: a string as it is, anything else as JSON.
 */
public record ResourceTestEntity(byte[] body, String mediaType) {

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.newObjectMapper();

    public static ResourceTestEntity json(Object entity) {
        return entity(entity, MediaType.APPLICATION_JSON_VALUE);
    }

    public static ResourceTestEntity entity(Object entity, MediaType mediaType) {
        return entity(entity, mediaType.toString());
    }

    public static ResourceTestEntity entity(Object entity, String mediaType) {
        if (entity instanceof String text) {
            return new ResourceTestEntity(text.getBytes(StandardCharsets.UTF_8), mediaType);
        }
        try {
            return new ResourceTestEntity(OBJECT_MAPPER.writeValueAsBytes(entity), mediaType);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
