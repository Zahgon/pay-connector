package uk.gov.pay.connector.web;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The bean validation constraint violations behind Spring's validation errors of handler arguments.
 */
final class ConstraintViolations {

    private ConstraintViolations() {
    }

    static ConstraintViolationException of(MethodArgumentNotValidException exception) {
        return of(exception.getBindingResult().getAllErrors().stream().map(ConstraintViolations::violation));
    }

    static ConstraintViolationException of(HandlerMethodValidationException exception) {
        return of(exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .<ConstraintViolation<?>>map(error -> result.unwrap(error, ConstraintViolation.class))));
    }

    private static ConstraintViolation<?> violation(ObjectError error) {
        return error.unwrap(ConstraintViolation.class);
    }

    private static ConstraintViolationException of(Stream<? extends ConstraintViolation<?>> violations) {
        Set<ConstraintViolation<?>> collected = new LinkedHashSet<>();
        violations.forEach(collected::add);
        return new ConstraintViolationException(collected);
    }
}
