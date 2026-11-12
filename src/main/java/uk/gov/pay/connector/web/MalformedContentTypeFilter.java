package uk.gov.pay.connector.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A request whose {@code Content-Type} header is not a media type ({@code type/subtype} followed by
 * {@code ;name=value} parameters, each value a token or a quoted string) is a bad request (400, the servlet container's
 * error page), whatever resource it is for and whatever its method. Repeated {@code Content-Type} headers are read as
 * one comma-separated value, which is never a media type.
 */
public class MalformedContentTypeFilter extends OncePerRequestFilter {

    private static final String TOKEN = "[!#$%&'*+\\-.^_`|~0-9A-Za-z]+";
    private static final String QUOTED_STRING = "\"(?:[^\"\\\\]|\\\\.)*\"";
    private static final Pattern MEDIA_TYPE = Pattern.compile(
            "\\s*" + TOKEN + "/" + TOKEN + "(?:\\s*;\\s*(?:" + TOKEN + "=(?:" + TOKEN + "|" + QUOTED_STRING + "))?)*\\s*");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        List<String> values = Collections.list(request.getHeaders(HttpHeaders.CONTENT_TYPE));
        String contentType = values.isEmpty() ? null : String.join(",", values);
        if (contentType != null && !MEDIA_TYPE.matcher(contentType).matches()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        chain.doFilter(request, response);
    }
}
