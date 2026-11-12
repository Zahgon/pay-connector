package uk.gov.pay.connector.app.config;


public class AuthorisationConfig {
    private int asynchronousAuthTimeoutInMilliseconds;
    private int synchronousAuthTimeoutInMilliseconds;

    public int getAsynchronousAuthTimeoutInMilliseconds() {
        return asynchronousAuthTimeoutInMilliseconds;
    }

    public int getSynchronousAuthTimeoutInMilliseconds() {
        return synchronousAuthTimeoutInMilliseconds;
    }
}
