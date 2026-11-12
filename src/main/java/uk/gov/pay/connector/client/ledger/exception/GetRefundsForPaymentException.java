package uk.gov.pay.connector.client.ledger.exception;

public class GetRefundsForPaymentException extends LedgerException {
    public GetRefundsForPaymentException(int status, String response) {
        super(status, response);
    }
}
