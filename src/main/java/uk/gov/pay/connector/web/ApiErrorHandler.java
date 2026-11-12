package uk.gov.pay.connector.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import uk.gov.pay.connector.common.exception.WebApplicationException;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Translates every exception a request ends with into its answer, the way the API always has:
 * <ul>
 *     <li>a {@link WebApplicationException} that carries an answer with an entity is answered with exactly that;</li>
 *     <li>otherwise the {@link ExceptionMapper} of the exception's nearest class or superclass answers;</li>
 *     <li>failures of the request handling itself (no such resource or method, media types, path and query
 *     parameters that do not convert, unreadable or invalid entities) are first turned into the exceptions the
 *     mappers know: a {@link WebApplicationException} with the status, the JSON reader's exception, or a
 *     {@link ConstraintViolationException}.</li>
 * </ul>
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class ApiErrorHandler {

    private final List<MapperEntry> mappers;
    private final ResourceMethods resourceMethods;

    public ApiErrorHandler(List<ExceptionMapper<?>> mappers, ResourceMethods resourceMethods) {
        this.mappers = mappers.stream().map(MapperEntry::of).toList();
        this.resourceMethods = resourceMethods;
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<?> notFound() {
        return handle(new WebApplicationException(HttpStatus.NOT_FOUND));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<?> methodNotAllowed(HttpServletRequest request) {
        Set<String> allowed = AllowedMethods.forUnsupportedMethod(resourceMethods.declaredFor(request));
        return handle(new WebApplicationException(ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .header(HttpHeaders.ALLOW, String.join(",", allowed)).build()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<?> unsupportedMediaType() {
        return handle(new WebApplicationException(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<?> notAcceptable() {
        return handle(new WebApplicationException(HttpStatus.NOT_ACCEPTABLE));
    }

    /**
     * A path parameter that does not convert to its type names no resource, unless its conversion says otherwise.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<?> pathParameterMismatch(MethodArgumentTypeMismatchException exception) {
        for (Throwable cause = exception.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof WebApplicationException webApplicationException) {
                return handle(webApplicationException);
            }
        }
        return handle(new WebApplicationException(HttpStatus.NOT_FOUND));
    }

    /**
     * An entity the JSON reader cannot read is answered by the mapper of the reader's exception.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> unreadableEntity(HttpMessageNotReadableException exception) {
        if (exception.getCause() instanceof JsonProcessingException jsonProcessingException) {
            return handle(jsonProcessingException);
        }
        return handle(new WebApplicationException(HttpStatus.BAD_REQUEST));
    }

    /**
     * An entity or parameter that breaks its constraints is a constraint violation.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> invalidEntity(MethodArgumentNotValidException exception) {
        return handle(ConstraintViolations.of(exception));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<?> invalidArguments(HandlerMethodValidationException exception) {
        return handle(ConstraintViolations.of(exception));
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<?> handle(Throwable exception) {
        if (exception instanceof WebApplicationException webApplicationException
                && webApplicationException.getResponse().hasBody()) {
            return webApplicationException.getResponse();
        }
        return mapperFor(exception)
                .map(mapper -> mapper.toResponse(exception))
                .orElseThrow(() -> new IllegalStateException("No exception mapper for " + exception.getClass(), exception));
    }

    private Optional<MapperEntry> mapperFor(Throwable exception) {
        return mappers.stream()
                .filter(mapper -> mapper.type().isInstance(exception))
                .min(Comparator.comparingInt(mapper -> distance(exception.getClass(), mapper.type())));
    }

    private static int distance(Class<?> exceptionType, Class<?> mapperType) {
        int distance = 0;
        for (Class<?> type = exceptionType; type != null && !type.equals(mapperType); type = type.getSuperclass()) {
            distance++;
        }
        return distance;
    }

    private record MapperEntry(Class<?> type, ExceptionMapper<Throwable> mapper) {

        @SuppressWarnings("unchecked")
        static MapperEntry of(ExceptionMapper<?> mapper) {
            Class<?> type = ResolvableType.forClass(mapper.getClass()).as(ExceptionMapper.class).resolveGeneric(0);
            return new MapperEntry(type == null ? Throwable.class : type, (ExceptionMapper<Throwable>) mapper);
        }

        ResponseEntity<?> toResponse(Throwable exception) {
            return mapper.toResponse(exception);
        }
    }
}
