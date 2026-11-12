package uk.gov.pay.connector.charge.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.common.model.api.ErrorResponse;
import uk.gov.service.payments.commons.model.ErrorIdentifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import uk.gov.pay.connector.web.ExceptionMapper;
import org.springframework.http.HttpStatus;


import static org.springframework.http.MediaType.APPLICATION_JSON;

@Component
public class IncorrectAuthorisationModeForSavePaymentToAgreementExceptionMapper
        implements ExceptionMapper<IncorrectAuthorisationModeForSavePaymentToAgreementException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(IncorrectAuthorisationModeForSavePaymentToAgreementExceptionMapper.class);
    
    @Override
    public ResponseEntity<ErrorResponse> toResponse(IncorrectAuthorisationModeForSavePaymentToAgreementException exception) {
        LOGGER.info(exception.getMessage());

        var errorResponse = new ErrorResponse(ErrorIdentifier.INCORRECT_AUTHORISATION_MODE_FOR_SAVE_PAYMENT_INSTRUMENT_TO_AGREEMENT, exception.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(APPLICATION_JSON)
                .body(errorResponse);
    }
}
