package uk.gov.pay.connector.agreement.exception;

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
public class AgreementNotFoundExceptionMapper implements ExceptionMapper<AgreementNotFoundException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgreementNotFoundExceptionMapper.class);

    @Override
    public ResponseEntity<ErrorResponse> toResponse(AgreementNotFoundException exception) {
        LOGGER.info(exception.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(ErrorIdentifier.AGREEMENT_NOT_FOUND, exception.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(APPLICATION_JSON)
                .body(errorResponse);
    }

}

