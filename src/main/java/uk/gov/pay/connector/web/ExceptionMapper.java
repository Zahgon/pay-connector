package uk.gov.pay.connector.web;

import org.springframework.http.ResponseEntity;

/**
 * Translates one kind of exception into the HTTP answer clients get for it. {@link ApiErrorHandler} picks, for an
 * exception a request ends with, the mapper of the exception's nearest class or superclass.
 *
 * @param <E> the kind of exception
 */
public interface ExceptionMapper<E extends Throwable> {

    ResponseEntity<?> toResponse(E exception);
}
