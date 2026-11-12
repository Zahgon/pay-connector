package uk.gov.pay.connector.charge.exception.motoapi;

import uk.gov.pay.connector.common.exception.WebApplicationException;
import org.springframework.http.HttpStatus;

public class OneTimeTokenAlreadyUsedException extends WebApplicationException {
    public OneTimeTokenAlreadyUsedException() {
        super("The one_time_token has already been used", HttpStatus.BAD_REQUEST);
    }
}
