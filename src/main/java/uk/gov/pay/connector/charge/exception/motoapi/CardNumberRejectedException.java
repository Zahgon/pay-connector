package uk.gov.pay.connector.charge.exception.motoapi;

import uk.gov.pay.connector.common.exception.WebApplicationException;

public class CardNumberRejectedException extends WebApplicationException {
    public CardNumberRejectedException(String message) {
        super(message);
    }
}
