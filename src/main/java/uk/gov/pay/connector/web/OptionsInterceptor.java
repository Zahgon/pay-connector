package uk.gov.pay.connector.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;
import uk.gov.pay.connector.web.MediaTypeNegotiation.Produced;
import uk.gov.pay.connector.web.MediaTypeNegotiation.Selection;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.springframework.http.MediaType.TEXT_PLAIN;
import static uk.gov.pay.connector.web.MediaTypeNegotiation.DEFAULT_QUALITY_OF_SOURCE;

/**
 * Answers OPTIONS requests for the API's resources: 200 with an {@code Allow} header and, from one of two handlers
 * (see {@link MediaTypeNegotiation}), either the same list as a {@code text/plain} entity or an empty entity of the
 * media type the client prefers most. The empty entity answers any media type, and in particular the media types the
 * API's entity writers produce.
 */
public class OptionsInterceptor implements HandlerInterceptor {

    private static final String PLAIN_TEXT_HANDLER = "plain text";
    private static final String EMPTY_ENTITY_HANDLER = "empty entity";
    private static final List<MediaType> EMPTY_ENTITY_TYPES = Stream.of(
                    "application/json", "text/json", "application/xml", "text/xml", "application/octet-stream",
                    "application/x-www-form-urlencoded", "*/*")
            .map(MediaType::parseMediaType)
            .toList();

    private final ResourceMethods resourceMethods;

    public OptionsInterceptor(ResourceMethods resourceMethods) {
        this.resourceMethods = resourceMethods;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!"OPTIONS".equals(request.getMethod())) {
            return true;
        }
        List<String> declared = resourceMethods.declaredFor(request);
        Set<String> allowed = AllowedMethods.forOptions(declared);

        response.setStatus(HttpServletResponse.SC_OK);
        response.setHeader(HttpHeaders.ALLOW, String.join(",", allowed));
        Optional<Selection> selection = MediaTypeNegotiation.select(MediaTypeNegotiation.accept(request), handlers(), false);
        if (selection.isEmpty() || PLAIN_TEXT_HANDLER.equals(selection.get().handler())) {
            byte[] entity = String.join(", ", allowed).getBytes(StandardCharsets.UTF_8);
            response.setContentType(TEXT_PLAIN.toString());
            response.setContentLength(entity.length);
            response.getOutputStream().write(entity);
        } else {
            response.setContentType(selection.get().acceptable().getFirst().toString());
            response.setContentLength(0);
        }
        return false;
    }

    private static List<Produced> handlers() {
        List<Produced> handlers = new ArrayList<>();
        handlers.add(new Produced(PLAIN_TEXT_HANDLER, TEXT_PLAIN, DEFAULT_QUALITY_OF_SOURCE));
        EMPTY_ENTITY_TYPES.forEach(mediaType ->
                handlers.add(new Produced(EMPTY_ENTITY_HANDLER, mediaType, DEFAULT_QUALITY_OF_SOURCE)));
        return handlers;
    }
}
