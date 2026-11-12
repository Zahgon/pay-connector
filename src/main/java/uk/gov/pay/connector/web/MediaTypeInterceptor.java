package uk.gov.pay.connector.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Enforces {@link MediaTypes} on the handler chosen for a request, before its arguments are read, in the order the
 * service always has:
 * <ol>
 *     <li>a request whose {@code Content-Type} is not one of the media types the endpoint consumes is refused (415);
 *     a request without a {@code Content-Type} passes;</li>
 *     <li>the media type of the response is negotiated (see {@link MediaTypeNegotiation}) among the media types the
 *     endpoint produces (JSON if it declares none); a request whose {@code Accept} header excludes all of them is
 *     refused (406);</li>
 *     <li>an endpoint that reads a request entity needs, for a request that sends one, a content type the JSON
 *     reader handles: {@code json}, {@code x-json}, {@code javascript}, {@code x-javascript} or a {@code +json}
 *     subtype (415 otherwise, also when the request declares none).</li>
 * </ol>
 */
public class MediaTypeInterceptor implements HandlerInterceptor {

    private static final List<MediaType> DEFAULT_PRODUCES = List.of(MediaType.APPLICATION_JSON);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws HttpMediaTypeNotSupportedException, HttpMediaTypeNotAcceptableException {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        MediaTypes mediaTypes = handlerMethod.getMethodAnnotation(MediaTypes.class);
        String contentTypeHeader = request.getHeader(HttpHeaders.CONTENT_TYPE);
        Optional<MediaType> contentType = parseContentType(contentTypeHeader);
        if (mediaTypes != null && mediaTypes.consumes().length > 0 && contentTypeHeader != null
                && contentType.map(type -> consumes(mediaTypes, type)).orElse(false) == Boolean.FALSE) {
            throw new HttpMediaTypeNotSupportedException(contentTypeHeader);
        }
        List<MediaType> produces = mediaTypes == null || mediaTypes.produces().length == 0
                ? DEFAULT_PRODUCES
                : Arrays.stream(mediaTypes.produces()).map(MediaType::parseMediaType).toList();
        MediaTypeNegotiation.negotiate(request, produces);
        if (readsEntity(handlerMethod) && hasEntity(request)
                && !contentType.map(JsonMessageConverter::isReadable).orElse(false)) {
            throw new HttpMediaTypeNotSupportedException(contentTypeHeader);
        }
        return true;
    }

    /**
     * @return whether the request carries an entity: a request without one is read as no entity, whatever its type
     */
    private static boolean hasEntity(HttpServletRequest request) {
        return request.getContentLengthLong() > 0 || request.getHeader(HttpHeaders.TRANSFER_ENCODING) != null;
    }

    private static boolean consumes(MediaTypes mediaTypes, MediaType contentType) {
        return Arrays.stream(mediaTypes.consumes()).map(MediaType::parseMediaType)
                .anyMatch(consumed -> consumed.isCompatibleWith(contentType));
    }

    /**
     * @return the media type of the request entity; a content type that cannot be parsed at all
     * ({@link MalformedContentTypeFilter} refuses malformed ones) is not one the JSON reader handles
     */
    private static Optional<MediaType> parseContentType(String contentType) {
        if (contentType == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(MediaType.parseMediaType(contentType));
        } catch (InvalidMediaTypeException e) {
            return Optional.empty();
        }
    }

    private static boolean readsEntity(HandlerMethod handlerMethod) {
        return Arrays.stream(handlerMethod.getMethodParameters()).anyMatch(MediaTypeInterceptor::isRequestBody);
    }

    /**
     * A JSON request entity; an entity received as text is read whatever its media type.
     */
    private static boolean isRequestBody(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(RequestBody.class) && parameter.getParameterType() != String.class;
    }
}
