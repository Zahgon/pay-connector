package uk.gov.pay.connector.charge.exception.motoapi;

import uk.gov.pay.connector.common.exception.WebApplicationException;

public class AuthorisationTimedOutException extends WebApplicationException {
    public AuthorisationTimedOutException() {
        super("Authorising the payment timed out");
    }
}
