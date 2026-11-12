package uk.gov.pay.connector.util;

import com.fasterxml.jackson.databind.JsonMappingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.service.payments.commons.model.ErrorIdentifier;
import uk.gov.pay.connector.common.model.api.ErrorResponse;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import uk.gov.pay.connector.web.ExceptionMapper;

import static org.springframework.http.MediaType.APPLICATION_JSON;

@Component
public class JsonMappingExceptionMapper implements ExceptionMapper<JsonMappingException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(JsonMappingExceptionMapper.class);

    @Override
    public ResponseEntity<ErrorResponse> toResponse(JsonMappingException exception) {
        LOGGER.error(exception.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(ErrorIdentifier.GENERIC, List.of(sanitiseExceptionMessage(exception.getMessage())));
        return ResponseEntity.status(400)
                .contentType(APPLICATION_JSON)
                .body(errorResponse);
    }

    private String sanitiseExceptionMessage(String message) {
        if (message.contains("Field [metadata] must be an object of JSON key-value pairs")) {
            return "Field [metadata] must be an object of JSON key-value pairs";
        }

        if (message.contains("Field [source] must be one of CARD_API, CARD_PAYMENT_LINK, CARD_AGENT_INITIATED_MOTO")) {
            return "Field [source] must be one of CARD_API, CARD_PAYMENT_LINK, CARD_AGENT_INITIATED_MOTO";
        }

        return message;
    }
}
