package uk.gov.pay.connector.charge.exception;

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
public class ZeroAmountNotAllowedForGatewayAccountExceptionMapper implements ExceptionMapper<ZeroAmountNotAllowedForGatewayAccountException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ZeroAmountNotAllowedForGatewayAccountExceptionMapper.class);

    private static final String RESPONSE_ERROR_MESSAGE = "Zero amount charges are not enabled for this gateway account";

    @Override
    public ResponseEntity<ErrorResponse> toResponse(ZeroAmountNotAllowedForGatewayAccountException exception) {
        LOGGER.info(exception.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(ErrorIdentifier.ZERO_AMOUNT_NOT_ALLOWED, List.of(RESPONSE_ERROR_MESSAGE));

        return ResponseEntity.status(422)
                .contentType(APPLICATION_JSON)
                .body(errorResponse);
    }

}
