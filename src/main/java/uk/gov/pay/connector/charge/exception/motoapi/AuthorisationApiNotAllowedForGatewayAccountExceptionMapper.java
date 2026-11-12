package uk.gov.pay.connector.charge.exception.motoapi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.common.model.api.ErrorResponse;
import uk.gov.service.payments.commons.model.ErrorIdentifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import uk.gov.pay.connector.web.ExceptionMapper;


import static org.springframework.http.MediaType.APPLICATION_JSON;

@Component
public class AuthorisationApiNotAllowedForGatewayAccountExceptionMapper implements ExceptionMapper<AuthorisationApiNotAllowedForGatewayAccountException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorisationApiNotAllowedForGatewayAccountExceptionMapper.class);

    @Override
    public ResponseEntity<ErrorResponse> toResponse(AuthorisationApiNotAllowedForGatewayAccountException exception) {
        LOGGER.info(exception.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(ErrorIdentifier.AUTHORISATION_API_NOT_ALLOWED, exception.getMessage());

        return ResponseEntity.status(422)
                .contentType(APPLICATION_JSON)
                .body(errorResponse);
    }
}
