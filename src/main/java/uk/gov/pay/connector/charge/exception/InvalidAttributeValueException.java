package uk.gov.pay.connector.charge.exception;

import uk.gov.pay.connector.common.exception.WebApplicationException;
import org.springframework.http.HttpStatus;

import static java.lang.String.format;

public class InvalidAttributeValueException extends WebApplicationException {
    public InvalidAttributeValueException(String fieldName, String message) {
        super(format("Invalid attribute value: %s. %s", fieldName, message), HttpStatus.BAD_REQUEST);
    }
}
