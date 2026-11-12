package uk.gov.pay.connector.charge.exception;

import uk.gov.pay.connector.common.exception.WebApplicationException;
import org.springframework.http.HttpStatus;

import static java.lang.String.format;

public class UnexpectedAttributeException extends WebApplicationException {
    public UnexpectedAttributeException(String fieldName) {
        super(format("Unexpected attribute: %s", fieldName), HttpStatus.BAD_REQUEST);
    }
}
