package uk.gov.pay.connector.charge.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.common.model.api.ErrorResponse;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import uk.gov.pay.connector.web.ExceptionMapper;
import org.springframework.http.HttpStatus;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.HttpStatus.FORBIDDEN;

@Component
public class GatewayAccountDisabledExceptionMapper implements ExceptionMapper<GatewayAccountDisabledException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayAccountDisabledExceptionMapper.class);

    private static final String RESPONSE_ERROR_MESSAGE = "This gateway account is disabled";

    @Override
    public ResponseEntity<ErrorResponse> toResponse(GatewayAccountDisabledException exception) {
        LOGGER.info(exception.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(ErrorIdentifier.ACCOUNT_DISABLED, List.of(RESPONSE_ERROR_MESSAGE));

        return ResponseEntity.status(FORBIDDEN)
                .contentType(APPLICATION_JSON)
                .body(errorResponse);
    }
}
