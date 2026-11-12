package uk.gov.pay.connector.client.ledger.exception;

public class LedgerException extends RuntimeException {
    private Integer status;

    public LedgerException(int status, String response) {
        super(response);
        this.status = status;
    }

    public LedgerException(Exception exception) {
        super(exception);
    }

    @Override
    public String toString() {
        return "LedgerException{" +
                "status=" + status +
                ", message=" + getMessage() +
                '}';
    }
}
