package uk.gov.pay.connector.util;

import com.google.common.base.Joiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.service.payments.commons.model.ErrorIdentifier;
import uk.gov.pay.connector.common.model.api.ErrorResponse;

import java.util.List;

import static java.lang.String.format;
import static org.springframework.http.HttpStatus.ACCEPTED;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.PAYMENT_REQUIRED;
import static uk.gov.service.payments.commons.model.ErrorIdentifier.AUTHORISATION_REJECTED;

public class ResponseUtil {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ResponseUtil.class);
    private static final Joiner COMMA_JOINER = Joiner.on(", ");

    public static ResponseEntity<Object> fieldsMissingResponse(List<String> missingFields) {
        String message = format("Field(s) missing: [%s]", COMMA_JOINER.join(missingFields));
        return badRequestResponse(message);
    }

    public static ResponseEntity<Object> fieldsInvalidSizeResponse(List<String> invalidSizeFields) {
        String message = format("Field(s) are too big: [%s]", COMMA_JOINER.join(invalidSizeFields));
        return badRequestResponse(message);
    }

    public static ResponseEntity<Object> responseWithChargeNotFound(String chargeId) {
        String message = format("Charge with id [%s] not found.", chargeId);
        return notFoundResponse(message);
    }

    public static ResponseEntity<Object> responseWithGatewayTransactionNotFound(String gatewayTransactionId) {
        String message = format("Charge with gateway transaction id [%s] not found.", gatewayTransactionId);
        return notFoundResponse(message);
    }

    public static ResponseEntity<Object> responseWithRefundNotFound(String refundId) {
        String message = format("Refund with id [%s] not found.", refundId);
        return notFoundResponse(message);
    }

    public static ResponseEntity<Object> badRequestResponse(String message) {
        return buildErrorResponse(BAD_REQUEST, message);
    }

    public static ResponseEntity<Object> authorisationRejectedResponse(String message) {
        return buildErrorResponse(BAD_REQUEST, AUTHORISATION_REJECTED, message);
    }

    public static ResponseEntity<Object> badRequestResponse(List<String> messages) {
        LOGGER.error(messages.toString());
        return buildErrorResponse(BAD_REQUEST, messages);
    }

    public static ResponseEntity<Object> notFoundResponse(String message) {
        LOGGER.info(message);
        return buildErrorResponse(NOT_FOUND, message);
    }

    public static ResponseEntity<Object> acceptedResponse(String message) {
        return buildErrorResponse(ACCEPTED, message);
    }

    public static ResponseEntity<Object> serviceErrorResponse(String message) {
        LOGGER.info(message);
        return buildErrorResponse(INTERNAL_SERVER_ERROR, message);
    }

    public static ResponseEntity<Object> gatewayErrorResponse(String message) {
        LOGGER.info(message);
        return buildErrorResponse(PAYMENT_REQUIRED, message);
    }
    
    public static ResponseEntity<Object> conflictErrorResponse(String message) {
        LOGGER.info(message);
        return buildErrorResponse(CONFLICT, message);
    }

    public static ResponseEntity<Object> forbiddenErrorResponse() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    public static ResponseEntity<Object> buildErrorResponse(HttpStatus status, String message) {
        ErrorResponse errorResponse = new ErrorResponse(ErrorIdentifier.GENERIC, List.of(message));
        return responseWithEntity(status, errorResponse);
    }

    private static ResponseEntity<Object> buildErrorResponse(HttpStatus status, List<String> messages) {
        ErrorResponse errorResponse = new ErrorResponse(ErrorIdentifier.GENERIC, messages);
        return responseWithEntity(status, errorResponse);
    }

    public static ResponseEntity<Object> buildErrorResponse(HttpStatus status, ErrorIdentifier errorIdentifier, String message) {
        ErrorResponse errorResponse = new ErrorResponse(errorIdentifier, message);
        return responseWithEntity(status, errorResponse);
    }
    public static ResponseEntity<Object> buildErrorResponse(HttpStatus status, ErrorIdentifier errorIdentifier, List<String> messages) {
        ErrorResponse errorResponse = new ErrorResponse(errorIdentifier, messages);
        return responseWithEntity(status, errorResponse);
    }

    public static ResponseEntity<Object> noContentResponse() {
        return ResponseEntity.noContent().build();
    }

    public static ResponseEntity<Object> successResponseWithEntity(Object entity) {
        return responseWithEntity(OK, entity);
    }

    public static ResponseEntity<Object> badRequestResponseWithEntity(Object entity) {
        return responseWithEntity(BAD_REQUEST, entity);
    }

    private static ResponseEntity<Object> responseWithEntity(HttpStatus status, Object entity) {
        return ResponseEntity.status(status).body(entity);
    }
}
