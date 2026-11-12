package uk.gov.pay.connector.charge.exception.motoapi;

import uk.gov.pay.connector.common.exception.WebApplicationException;
import org.springframework.http.HttpStatus;

public class OneTimeTokenUsageInvalidForMotoApiException extends WebApplicationException {
    public OneTimeTokenUsageInvalidForMotoApiException() {
        super("The one_time_token is not a valid moto api token", HttpStatus.BAD_REQUEST);
    }
}
