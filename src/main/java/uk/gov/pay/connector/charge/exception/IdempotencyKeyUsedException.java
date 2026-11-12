package uk.gov.pay.connector.charge.exception;

import uk.gov.pay.connector.common.exception.WebApplicationException;

public class IdempotencyKeyUsedException extends WebApplicationException {
    public IdempotencyKeyUsedException() {
        super("The Idempotency-Key has already been used to create a payment");
    }
}
