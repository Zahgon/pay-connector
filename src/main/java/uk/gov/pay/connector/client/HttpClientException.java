package uk.gov.pay.connector.client;

/**
 * A call to another service could not be made or its answer could not be read: the connection failed, timed out or
 * broke, or the entity of the answer was not what the service expected.
 */
public class HttpClientException extends RuntimeException {

    public HttpClientException(Throwable cause) {
        super(cause);
    }

    public HttpClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
