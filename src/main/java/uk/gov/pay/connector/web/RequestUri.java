package uk.gov.pay.connector.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static java.lang.String.format;

/**
 * The address a request was sent to (scheme, host and port as the request names them, and path), from which links to
 * other pages of a resource are built.
 */
public final class RequestUri {

    private static final String UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~";
    private static final String PATH_CHARACTERS = UNRESERVED + "!$&'()*+,;=:@/";
    private static final String QUERY_CHARACTERS = UNRESERVED + "&+=";

    private final String baseUri;
    private final String path;

    private RequestUri(String baseUri, String path) {
        this.baseUri = baseUri;
        this.path = path;
    }

    /**
     * @param request the request, whose address is the one it was sent to even when filters present it differently
     *                (without a trailing slash, say)
     */
    public static RequestUri from(HttpServletRequest request) {
        while (request instanceof HttpServletRequestWrapper wrapper
                && wrapper.getRequest() instanceof HttpServletRequest wrapped) {
            request = wrapped;
        }
        String requestUrl = request.getRequestURL().toString();
        String rawPath = request.getRequestURI();
        String baseUri = requestUrl.substring(0, requestUrl.length() - rawPath.length());
        return new RequestUri(baseUri, encode(percentDecode(rawPath), PATH_CHARACTERS));
    }

    /**
     * @param query the query, whose characters that may not appear in a query are percent-encoded (escape sequences
     *              are kept as they are)
     * @return the address of the request with the given query instead of its own
     * @throws IllegalArgumentException when the query contains a brace, which cannot appear in a link
     */
    public String withQuery(String query) {
        String encodedQuery = encode(query, QUERY_CHARACTERS);
        if (encodedQuery.indexOf('{') >= 0 || encodedQuery.indexOf('}') >= 0) {
            throw new IllegalArgumentException(format("The link query [%s] contains a template variable", query));
        }
        return baseUri + path + "?" + encodedQuery;
    }

    private static String encode(String value, String allowed) {
        StringBuilder encoded = new StringBuilder();
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            if (codePoint < 0x80 && allowed.indexOf(codePoint) >= 0 || codePoint == '{' || codePoint == '}') {
                encoded.append((char) codePoint);
            } else if (codePoint == '%' && offset + 2 < value.length()
                    && isHex(value.charAt(offset + 1)) && isHex(value.charAt(offset + 2))) {
                encoded.append(value, offset, offset + 3);
                offset += 2;
            } else {
                for (byte b : new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8)) {
                    encoded.append(format("%%%02X", b & 0xFF));
                }
            }
            offset += Character.charCount(codePoint);
        }
        return encoded.toString();
    }

    private static String percentDecode(String encoded) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int i = 0; i < encoded.length(); i++) {
            char c = encoded.charAt(i);
            if (c == '%' && i + 2 < encoded.length() && isHex(encoded.charAt(i + 1)) && isHex(encoded.charAt(i + 2))) {
                bytes.write(Integer.parseInt(encoded.substring(i + 1, i + 3), 16));
                i += 2;
            } else {
                bytes.writeBytes(String.valueOf(c).getBytes(StandardCharsets.UTF_8));
            }
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private static boolean isHex(char c) {
        return Character.digit(c, 16) >= 0;
    }
}
