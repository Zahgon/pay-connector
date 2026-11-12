package uk.gov.pay.connector.charge.exception.motoapi;

import uk.gov.pay.connector.common.exception.WebApplicationException;

public class AuthorisationErrorException extends WebApplicationException {
    public AuthorisationErrorException() {
        super("There was an error authorising the payment");
    }
}
