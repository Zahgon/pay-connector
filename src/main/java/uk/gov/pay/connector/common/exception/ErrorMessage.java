package uk.gov.pay.connector.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The entity of an error answer that is not specific to the service: the status code, a message and, for
 * unreadable JSON, the details of what could not be read.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorMessage(@JsonProperty("code") Integer code,
                           @JsonProperty("message") String message,
                           @JsonProperty("details") String details) {

    public ErrorMessage(int code, String message) {
        this(code, message, null);
    }
}
