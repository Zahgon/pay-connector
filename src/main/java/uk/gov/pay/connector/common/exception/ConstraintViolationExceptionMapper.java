package uk.gov.pay.connector.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.service.payments.commons.model.ErrorIdentifier;
import uk.gov.pay.connector.common.model.api.ErrorResponse;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import uk.gov.pay.connector.web.ExceptionMapper;

import static org.springframework.http.MediaType.APPLICATION_JSON;


@Component
public class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConstraintViolationExceptionMapper.class);

    @Override
    public ResponseEntity<ErrorResponse> toResponse(ConstraintViolationException exception) {
        LOGGER.info(exception.getMessage());
        List<String> constraintViolationMessages = exception
                .getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toList());

        ErrorResponse errorResponse = new ErrorResponse(ErrorIdentifier.GENERIC, constraintViolationMessages);
        return ResponseEntity.status(422)
                .contentType(APPLICATION_JSON)
                .body(errorResponse);
    }
}
