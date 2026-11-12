package uk.gov.pay.connector.token.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.agreement.exception.AgreementNotFoundException;
import uk.gov.pay.connector.agreement.exception.AgreementNotFoundExceptionMapper;
import uk.gov.pay.connector.common.model.api.ErrorResponse;
import uk.gov.service.payments.commons.model.ErrorIdentifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import uk.gov.pay.connector.web.ExceptionMapper;
import org.springframework.http.HttpStatus;


import static org.springframework.http.MediaType.APPLICATION_JSON;

@Component
public class TokenNotFoundExceptionMapper implements ExceptionMapper<TokenNotFoundException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenNotFoundExceptionMapper.class);

    @Override
    public ResponseEntity<ErrorResponse> toResponse(TokenNotFoundException exception) {
        LOGGER.info(exception.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(ErrorIdentifier.GENERIC, exception.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(APPLICATION_JSON)
                .body(errorResponse);
    }

}
