package uk.gov.pay.connector.gateway.worldpay.exception;

import org.springframework.http.HttpStatus;
import uk.gov.pay.connector.client.HttpClientException;
import uk.gov.pay.connector.common.exception.WebApplicationException;

public class ThreeDsFlexDdcServiceUnavailableException extends WebApplicationException {

    public ThreeDsFlexDdcServiceUnavailableException(HttpClientException exception) {
        super(null, exception, HttpStatus.SERVICE_UNAVAILABLE);
    }

    public ThreeDsFlexDdcServiceUnavailableException() {
        super(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
