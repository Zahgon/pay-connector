package uk.gov.pay.connector.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.common.model.api.ErrorResponse;
import uk.gov.service.payments.commons.api.exception.ValidationException;
import uk.gov.service.payments.commons.model.ErrorIdentifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import uk.gov.pay.connector.web.ExceptionMapper;


import static org.springframework.http.MediaType.APPLICATION_JSON;

@Component
public class ValidationExceptionMapper implements ExceptionMapper<ValidationException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ValidationExceptionMapper.class);

    @Override
    public ResponseEntity<ErrorResponse> toResponse(ValidationException exception) {
        LOGGER.error(String.join("\n", exception.getErrors()));
        ErrorResponse errorResponse = new ErrorResponse(ErrorIdentifier.GENERIC, exception.getErrors());
        return ResponseEntity.status(400)
                .contentType(APPLICATION_JSON)
                .body(errorResponse);
    }
}
