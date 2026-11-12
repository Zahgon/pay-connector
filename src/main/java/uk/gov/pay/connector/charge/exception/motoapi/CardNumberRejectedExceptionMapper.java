package uk.gov.pay.connector.charge.exception.motoapi;

import uk.gov.pay.connector.common.model.api.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import uk.gov.pay.connector.web.ExceptionMapper;


import static org.springframework.http.MediaType.APPLICATION_JSON;
import static uk.gov.service.payments.commons.model.ErrorIdentifier.CARD_NUMBER_REJECTED;

@Component
public class CardNumberRejectedExceptionMapper implements ExceptionMapper<CardNumberRejectedException> {
    
    @Override
    public ResponseEntity<ErrorResponse> toResponse(CardNumberRejectedException exception) {
        ErrorResponse errorResponse = new ErrorResponse(CARD_NUMBER_REJECTED, exception.getMessage());

        return ResponseEntity.status(402)
                .contentType(APPLICATION_JSON)
                .body(errorResponse);
    }
}
