package uk.gov.pay.connector.charge.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.common.model.api.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import uk.gov.pay.connector.web.ExceptionMapper;


import static org.springframework.http.MediaType.APPLICATION_JSON;
import static uk.gov.service.payments.commons.model.ErrorIdentifier.GENERIC;

@Component
public class ConflictWebApplicationExceptionMapper implements ExceptionMapper<ConflictWebApplicationException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConflictWebApplicationExceptionMapper.class);

    @Override
    public ResponseEntity<ErrorResponse> toResponse(ConflictWebApplicationException exception) {
        LOGGER.info(exception.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(GENERIC, exception.getMessage());

        return ResponseEntity.status(409)
                .contentType(APPLICATION_JSON)
                .body(errorResponse);
    }

}
