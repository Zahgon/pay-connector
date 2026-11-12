package uk.gov.pay.connector.wallets.applepay;

import org.springframework.http.HttpStatus;
import uk.gov.pay.connector.common.exception.WebApplicationException;

public class InvalidKeyException extends WebApplicationException {
    public InvalidKeyException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
