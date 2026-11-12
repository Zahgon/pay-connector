package uk.gov.pay.connector.charge.exception.motoapi;

import uk.gov.pay.connector.common.exception.WebApplicationException;

public class AuthorisationRejectedException extends WebApplicationException {
    public AuthorisationRejectedException() {
        super("The payment was rejected");
    }
}
