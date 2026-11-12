package uk.gov.pay.connector.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.service.payments.commons.model.ErrorIdentifier;
import uk.gov.pay.connector.common.model.api.ErrorResponse;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import uk.gov.pay.connector.web.ExceptionMapper;
import org.springframework.http.HttpStatus;

import static org.springframework.http.MediaType.APPLICATION_JSON;


@Component
public class UnsupportedOperationExceptionMapper implements ExceptionMapper<UnsupportedOperationException> {
    private static final Logger LOGGER = LoggerFactory.getLogger(UnsupportedOperationExceptionMapper.class);

    @Override
    public ResponseEntity<ErrorResponse> toResponse(UnsupportedOperationException exception) {
        LOGGER.error(exception.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(ErrorIdentifier.GENERIC, List.of(exception.getMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(APPLICATION_JSON)
                .body(errorResponse);
    }
}
