package uk.gov.pay.connector.charge.exception.motoapi;

import uk.gov.pay.connector.common.model.api.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import uk.gov.pay.connector.web.ExceptionMapper;


import static org.springframework.http.MediaType.APPLICATION_JSON;
import static uk.gov.service.payments.commons.model.ErrorIdentifier.AUTHORISATION_ERROR;

@Component
public class AuthorisationErrorExceptionMapper implements ExceptionMapper<AuthorisationErrorException> {
    
    @Override
    public ResponseEntity<ErrorResponse> toResponse(AuthorisationErrorException exception) {
        ErrorResponse errorResponse = new ErrorResponse(AUTHORISATION_ERROR, exception.getMessage());

        return ResponseEntity.status(500)
                .contentType(APPLICATION_JSON)
                .body(errorResponse);
    }
}
