package uk.gov.pay.connector.charge.exception.motoapi;

import uk.gov.pay.connector.common.exception.WebApplicationException;
import org.springframework.http.HttpStatus;

public class OneTimeTokenInvalidException extends WebApplicationException {
    public OneTimeTokenInvalidException() {
        super("The one_time_token is not valid", HttpStatus.BAD_REQUEST);
    }
}
