package uk.gov.pay.connector.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import uk.gov.pay.connector.common.exception.ErrorMessage;
import uk.gov.pay.connector.common.exception.WebApplicationException;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import static java.lang.String.format;

/**
 * The answer to any exception no more specific mapper handles. A {@link WebApplicationException} keeps its status
 * and headers (a redirection keeps its answer as it is) with the standard error entity naming its message; anything
 * else is a 500 whose message names the id under which the exception was logged.
 */
@Component
public class LoggingExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingExceptionMapper.class);

    @Override
    public ResponseEntity<?> toResponse(Throwable exception) {
        if (exception instanceof WebApplicationException webApplicationException) {
            ResponseEntity<?> response = webApplicationException.getResponse();
            if (response.getStatusCode().is3xxRedirection()) {
                return response;
            }
            if (response.getStatusCode().is5xxServerError()) {
                logException(exception);
            }
            HttpHeaders headers = new HttpHeaders();
            headers.putAll(response.getHeaders());
            headers.setContentType(MediaType.APPLICATION_JSON);
            return ResponseEntity.status(response.getStatusCode()).headers(headers)
                    .body(new ErrorMessage(response.getStatusCode().value(), exception.getLocalizedMessage()));
        }
        long id = logException(exception);
        return ResponseEntity.internalServerError().contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorMessage(500, format(Locale.ROOT,
                        "There was an error processing your request. It has been logged (ID %016x).", id)));
    }

    private static long logException(Throwable exception) {
        long id = ThreadLocalRandom.current().nextLong();
        LOGGER.error(format(Locale.ROOT, "Error handling a request: %016x", id), exception);
        return id;
    }
}
