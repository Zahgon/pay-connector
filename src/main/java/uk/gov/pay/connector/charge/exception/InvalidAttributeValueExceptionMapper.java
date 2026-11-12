package uk.gov.pay.connector.charge.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.common.model.api.ErrorResponse;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import uk.gov.pay.connector.web.ExceptionMapper;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static uk.gov.service.payments.commons.model.ErrorIdentifier.INVALID_ATTRIBUTE_VALUE;

@Component
public class InvalidAttributeValueExceptionMapper implements ExceptionMapper<InvalidAttributeValueException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(InvalidAttributeValueExceptionMapper.class);

    @Override
    public ResponseEntity<ErrorResponse> toResponse(InvalidAttributeValueException exception) {
        LOGGER.info(exception.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(INVALID_ATTRIBUTE_VALUE, List.of(exception.getMessage()));

        return ResponseEntity.status(422)
                .contentType(APPLICATION_JSON)
                .body(errorResponse);
    }
}
