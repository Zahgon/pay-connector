package uk.gov.pay.connector.charge.exception.motoapi;

import uk.gov.pay.connector.common.model.api.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import uk.gov.pay.connector.web.ExceptionMapper;


import static org.springframework.http.MediaType.APPLICATION_JSON;
import static uk.gov.service.payments.commons.model.ErrorIdentifier.AUTHORISATION_REJECTED;

@Component
public class AuthorisationRejectedExceptionMapper implements ExceptionMapper<AuthorisationRejectedException> {
    
    @Override
    public ResponseEntity<ErrorResponse> toResponse(AuthorisationRejectedException exception) {
        ErrorResponse errorResponse = new ErrorResponse(AUTHORISATION_REJECTED, exception.getMessage());

        return ResponseEntity.status(402)
                .contentType(APPLICATION_JSON)
                .body(errorResponse);
    }
}
