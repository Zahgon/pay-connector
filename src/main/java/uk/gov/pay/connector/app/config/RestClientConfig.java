package uk.gov.pay.connector.app.config;


public class RestClientConfig {

    private String disabledSecureConnection;

    public RestClientConfig() {
    }

    public Boolean isDisabledSecureConnection() {
        return "true".equals(disabledSecureConnection);
    }
}

