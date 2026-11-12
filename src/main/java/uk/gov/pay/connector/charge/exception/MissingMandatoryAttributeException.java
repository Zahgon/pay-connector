package uk.gov.pay.connector.charge.exception;

import uk.gov.pay.connector.common.exception.WebApplicationException;
import org.springframework.http.HttpStatus;

import static java.lang.String.format;

public class MissingMandatoryAttributeException extends WebApplicationException {
    public MissingMandatoryAttributeException(String fieldName) {
        super(format("Missing mandatory attribute: %s", fieldName), HttpStatus.BAD_REQUEST);
    }
}
