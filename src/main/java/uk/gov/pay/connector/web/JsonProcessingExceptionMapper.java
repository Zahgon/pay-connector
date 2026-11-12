package uk.gov.pay.connector.web;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import uk.gov.pay.connector.common.exception.ErrorMessage;

/**
 * JSON that cannot be read is a bad request ({@code Unable to process JSON}); a failure to write an answer as JSON,
 * or a type the JSON mapper cannot handle at all, is the service's own error.
 */
@Component
public class JsonProcessingExceptionMapper implements ExceptionMapper<JsonProcessingException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(JsonProcessingExceptionMapper.class);

    private final LoggingExceptionMapper loggingExceptionMapper;

    public JsonProcessingExceptionMapper(LoggingExceptionMapper loggingExceptionMapper) {
        this.loggingExceptionMapper = loggingExceptionMapper;
    }

    @Override
    public ResponseEntity<?> toResponse(JsonProcessingException exception) {
        if (exception instanceof JsonGenerationException || exception instanceof InvalidDefinitionException) {
            return loggingExceptionMapper.toResponse(exception);
        }
        LOGGER.debug("Unable to process JSON", exception);
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorMessage(400, "Unable to process JSON", null));
    }
}
