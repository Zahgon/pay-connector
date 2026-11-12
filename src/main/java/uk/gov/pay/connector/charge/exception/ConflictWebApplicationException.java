package uk.gov.pay.connector.charge.exception;

import uk.gov.pay.connector.common.exception.WebApplicationException;

public class ConflictWebApplicationException extends WebApplicationException {

    public ConflictWebApplicationException(String message) {
        super(message);
    }
}
