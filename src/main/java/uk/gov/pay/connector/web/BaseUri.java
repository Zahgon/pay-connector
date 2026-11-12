package uk.gov.pay.connector.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The address of the API as a client reached it, from which the links in answers are built: the host and port the
 * request names, always with the {@code https} scheme (the service runs behind a proxy that terminates TLS).
 */
public final class BaseUri {

    private final String baseUri;

    private BaseUri(String baseUri) {
        this.baseUri = baseUri;
    }

    public static BaseUri of(String baseUri) {
        return new BaseUri(baseUri.endsWith("/") ? baseUri : baseUri + "/");
    }

    public static BaseUri from(HttpServletRequest request) {
        while (request instanceof HttpServletRequestWrapper wrapper
                && wrapper.getRequest() instanceof HttpServletRequest wrapped) {
            request = wrapped;
        }
        String requestUrl = request.getRequestURL().toString();
        String origin = requestUrl.substring(0, requestUrl.length() - request.getRequestURI().length());
        return of("https" + origin.substring(origin.indexOf(':')));
    }

    /**
     * @return a builder for a link below the API's address; its {@code path} segments are appended and its
     * {@code build} expands and encodes the template variables
     */
    public UriComponentsBuilder getBaseUriBuilder() {
        return UriComponentsBuilder.fromUriString(baseUri);
    }

    @Override
    public String toString() {
        return baseUri;
    }
}
