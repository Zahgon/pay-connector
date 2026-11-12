package uk.gov.pay.connector.agreement.exception;
import uk.gov.pay.connector.common.exception.WebApplicationException;

public class AgreementNotFoundException extends WebApplicationException {

    public AgreementNotFoundException(String message) {
        super(message);
    }

}
