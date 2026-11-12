package uk.gov.pay.connector.gateway.worldpay.exception;

import org.eclipse.jetty.http.HttpStatus;

import uk.gov.pay.connector.common.exception.WebApplicationException;

import static uk.gov.pay.connector.util.ResponseUtil.serviceErrorResponse;

public class UnexpectedValidateCredentialsResponse extends WebApplicationException {
    
    public UnexpectedValidateCredentialsResponse() {
        super(serviceErrorResponse("Worldpay returned an unexpected response when validating credentials"));
    }
}
