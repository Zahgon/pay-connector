package uk.gov.pay.connector.charge.exception;

import uk.gov.pay.connector.common.model.api.ErrorResponse;
import uk.gov.service.payments.commons.model.ErrorIdentifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import uk.gov.pay.connector.web.ExceptionMapper;
import org.springframework.http.HttpStatus;


import static org.springframework.http.MediaType.APPLICATION_JSON;

@Component
public class IdempotencyKeyUsedExceptionMapper implements ExceptionMapper<IdempotencyKeyUsedException> {
    @Override
    public ResponseEntity<ErrorResponse> toResponse(IdempotencyKeyUsedException exception) {
        ErrorResponse errorResponse = new ErrorResponse(ErrorIdentifier.IDEMPOTENCY_KEY_USED, exception.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(APPLICATION_JSON)
                .body(errorResponse);
    }
}
