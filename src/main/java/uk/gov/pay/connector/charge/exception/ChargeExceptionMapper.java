package uk.gov.pay.connector.charge.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.common.model.api.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import uk.gov.pay.connector.web.ExceptionMapper;


import static org.springframework.http.MediaType.APPLICATION_JSON;

@Component
public class ChargeExceptionMapper implements ExceptionMapper<ChargeException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChargeExceptionMapper.class);
    
    @Override
    public ResponseEntity<ErrorResponse> toResponse(ChargeException exception) {
        LOGGER.info(exception.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(exception.getErrorIdentifier(), exception.getMessage());

        return ResponseEntity.status(exception.getHttpResponseStatusCode())
                .contentType(APPLICATION_JSON)
                .body(errorResponse);
    }
}
