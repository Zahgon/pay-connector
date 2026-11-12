package uk.gov.pay.connector.token.exception;

import uk.gov.pay.connector.common.exception.WebApplicationException;

public class TokenNotFoundException extends WebApplicationException {

    public TokenNotFoundException(String message) {
        super(message);
    }

}
