package uk.gov.pay.connector.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import uk.gov.pay.connector.common.exception.WebApplicationException;

/**
 * The literal segments of a resource's path match a request only as they are written: a request that names them
 * percent-encoded ({@code /v1/%74ransaction}) is for a resource that does not exist (404). Path parameters may be
 * encoded.
 */
public class EncodedPathInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) instanceof String pattern) {
            String[] templateSegments = pattern.split("/");
            String[] requestSegments = rawPath(request).split("/");
            for (int i = 0; i < templateSegments.length && i < requestSegments.length; i++) {
                String templateSegment = templateSegments[i];
                String requestSegment = requestSegments[i];
                int parameters = requestSegment.indexOf(';');
                String segment = parameters < 0 ? requestSegment : requestSegment.substring(0, parameters);
                if (!templateSegment.contains("{") && !templateSegment.equals(segment)) {
                    throw new WebApplicationException(HttpStatus.NOT_FOUND);
                }
            }
        }
        return true;
    }

    private static String rawPath(HttpServletRequest request) {
        while (request instanceof HttpServletRequestWrapper wrapper
                && wrapper.getRequest() instanceof HttpServletRequest wrapped) {
            request = wrapped;
        }
        return request.getRequestURI();
    }
}
