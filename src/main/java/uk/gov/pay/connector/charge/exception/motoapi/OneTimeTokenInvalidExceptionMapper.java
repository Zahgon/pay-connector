package uk.gov.pay.connector.charge.exception.motoapi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.common.model.api.ErrorResponse;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import uk.gov.pay.connector.web.ExceptionMapper;
import org.springframework.http.HttpStatus;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static uk.gov.service.payments.commons.model.ErrorIdentifier.ONE_TIME_TOKEN_INVALID;

@Component
public class OneTimeTokenInvalidExceptionMapper implements ExceptionMapper<OneTimeTokenInvalidException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(OneTimeTokenInvalidExceptionMapper.class);

    @Override
    public ResponseEntity<ErrorResponse> toResponse(OneTimeTokenInvalidException exception) {
        LOGGER.info(exception.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(ONE_TIME_TOKEN_INVALID, List.of(exception.getMessage()));

        return ResponseEntity.status(BAD_REQUEST)
                .contentType(APPLICATION_JSON)
                .body(errorResponse);
    }
}
