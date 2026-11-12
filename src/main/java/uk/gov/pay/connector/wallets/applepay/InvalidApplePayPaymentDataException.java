package uk.gov.pay.connector.wallets.applepay;

import org.springframework.http.HttpStatus;
import uk.gov.pay.connector.common.exception.WebApplicationException;

public class InvalidApplePayPaymentDataException extends WebApplicationException {
    public InvalidApplePayPaymentDataException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
