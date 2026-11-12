package uk.gov.pay.connector.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static java.lang.String.format;

/**
 * Ends the handling of a request with an HTTP answer. An exception that carries an answer with an entity is answered
 * with exactly that answer; otherwise the shared error handling answers with the status (500 unless given) and the
 * standard error entity, whose message is the exception's message or, if none is given, names the status
 * ({@code HTTP 404 Not Found}).
 */
public class WebApplicationException extends RuntimeException {

    private static final Map<Integer, String> REASON_PHRASES = Map.ofEntries(
            Map.entry(200, "OK"), Map.entry(201, "Created"), Map.entry(202, "Accepted"), Map.entry(204, "No Content"),
            Map.entry(205, "Reset Content"), Map.entry(206, "Partial Content"), Map.entry(301, "Moved Permanently"),
            Map.entry(302, "Found"), Map.entry(303, "See Other"), Map.entry(304, "Not Modified"),
            Map.entry(305, "Use Proxy"), Map.entry(307, "Temporary Redirect"), Map.entry(308, "Permanent Redirect"),
            Map.entry(400, "Bad Request"), Map.entry(401, "Unauthorized"), Map.entry(402, "Payment Required"),
            Map.entry(403, "Forbidden"), Map.entry(404, "Not Found"), Map.entry(405, "Method Not Allowed"),
            Map.entry(406, "Not Acceptable"), Map.entry(407, "Proxy Authentication Required"),
            Map.entry(408, "Request Timeout"), Map.entry(409, "Conflict"), Map.entry(410, "Gone"),
            Map.entry(411, "Length Required"), Map.entry(412, "Precondition Failed"),
            Map.entry(413, "Request Entity Too Large"), Map.entry(414, "Request-URI Too Long"),
            Map.entry(415, "Unsupported Media Type"), Map.entry(416, "Requested Range Not Satisfiable"),
            Map.entry(417, "Expectation Failed"), Map.entry(418, "I'm a teapot"), Map.entry(421, "Misdirected Request"),
            Map.entry(426, "Upgrade Required"), Map.entry(428, "Precondition Required"),
            Map.entry(429, "Too Many Requests"), Map.entry(431, "Request Header Fields Too Large"),
            Map.entry(451, "Unavailable For Legal Reasons"), Map.entry(500, "Internal Server Error"),
            Map.entry(501, "Not Implemented"), Map.entry(502, "Bad Gateway"), Map.entry(503, "Service Unavailable"),
            Map.entry(504, "Gateway Timeout"), Map.entry(505, "HTTP Version Not Supported"),
            Map.entry(511, "Network Authentication Required"));

    private final transient ResponseEntity<?> response;

    public WebApplicationException() {
        this((String) null, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public WebApplicationException(String message) {
        this(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public WebApplicationException(HttpStatusCode status) {
        this((String) null, status);
    }

    public WebApplicationException(int status) {
        this((String) null, HttpStatusCode.valueOf(status));
    }

    public WebApplicationException(String message, int status) {
        this(message, HttpStatusCode.valueOf(status));
    }

    public WebApplicationException(String message, HttpStatusCode status) {
        this(message, null, ResponseEntity.status(status).build());
    }

    public WebApplicationException(ResponseEntity<?> response) {
        this(null, null, response);
    }

    public WebApplicationException(String message, ResponseEntity<?> response) {
        this(message, null, response);
    }

    public WebApplicationException(Throwable cause) {
        this(null, cause, ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
    }

    public WebApplicationException(String message, Throwable cause) {
        this(message, cause, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public WebApplicationException(String message, Throwable cause, int status) {
        this(message, cause, HttpStatusCode.valueOf(status));
    }

    public WebApplicationException(String message, Throwable cause, HttpStatusCode status) {
        this(message, cause, ResponseEntity.status(status).build());
    }

    private WebApplicationException(String message, Throwable cause, ResponseEntity<?> response) {
        super(message == null ? statusMessage(response.getStatusCode().value()) : message, cause);
        this.response = response;
    }

    /**
     * @return the answer this exception ends the request with: its status, and the entity if it has one
     */
    public ResponseEntity<?> getResponse() {
        return response;
    }

    /**
     * @return the message naming a status the way error entities always have, such as {@code HTTP 404 Not Found}
     */
    public static String statusMessage(int status) {
        return format("HTTP %d %s", status, REASON_PHRASES.getOrDefault(status, "")).stripTrailing();
    }
}
