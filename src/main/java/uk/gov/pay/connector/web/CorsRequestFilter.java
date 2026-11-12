package uk.gov.pay.connector.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Set;

/**
 * The API has no cross-origin resource sharing: a CORS preflight request (OPTIONS with {@code Origin} and
 * {@code Access-Control-Request-Method}) is an ordinary OPTIONS request, answered like any other. The preflight
 * headers are hidden from Spring MVC, which would otherwise refuse the request for want of a CORS configuration.
 */
public class CorsRequestFilter extends OncePerRequestFilter {

    private static final Set<String> PREFLIGHT_HEADERS = Set.of(
            HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD.toLowerCase(),
            HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS.toLowerCase());

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD) == null) {
            chain.doFilter(request, response);
            return;
        }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override
            public String getHeader(String name) {
                return isPreflightHeader(name) ? null : super.getHeader(name);
            }

            @Override
            public Enumeration<String> getHeaders(String name) {
                return isPreflightHeader(name) ? Collections.emptyEnumeration() : super.getHeaders(name);
            }

            @Override
            public Enumeration<String> getHeaderNames() {
                return Collections.enumeration(Collections.list(super.getHeaderNames()).stream()
                        .filter(name -> !isPreflightHeader(name))
                        .toList());
            }
        }, response);
    }

    private static boolean isPreflightHeader(String name) {
        return name != null && PREFLIGHT_HEADERS.contains(name.toLowerCase());
    }
}
