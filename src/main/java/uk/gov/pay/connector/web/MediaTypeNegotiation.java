package uk.gov.pay.connector.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.accept.ContentNegotiationStrategy;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Chooses the media type of a response from the request's {@code Accept} header and the media types the handlers of
 * a request produce, each with its quality of source ({@code qs}), as the service always has. The acceptable media
 * types are taken in order of preference (highest {@code q} first, then the most specific; a bare type {@code text}
 * stands for {@code text/*}). Each compatible pair of an acceptable and a produced media type makes a media type (the
 * acceptable one, with its parameters except {@code q}, unless the produced one is more specific); of all pairs, the
 * one making the most specific media type wins, then the one the client prefers most (highest {@code q}), then the
 * one the endpoint prefers (highest {@code qs}), then the one matched with fewer wildcards, then the first. An
 * endpoint that produces a single media type answers with the first acceptable media type compatible with it. A
 * missing {@code Accept} header accepts anything; one that cannot be parsed accepts nothing.
 * <p>
 * The media type chosen for an endpoint's response is kept as a request attribute and used for the response by
 * Spring's content negotiation.
 */
public final class MediaTypeNegotiation implements ContentNegotiationStrategy {

    static final int DEFAULT_QUALITY_OF_SOURCE = 1000;

    private static final String NEGOTIATED_MEDIA_TYPE_ATTRIBUTE = MediaTypeNegotiation.class.getName() + ".mediaType";
    private static final String NEGOTIATED_CONTENT_TYPE_ATTRIBUTE = MediaTypeNegotiation.class.getName() + ".contentType";

    /**
     * A media type a handler produces, with its quality of source.
     */
    record Produced(Object handler, MediaType mediaType, int qualityOfSource) {
    }

    /**
     * The handler chosen for a request, the media type made by the winning pair and the acceptable media types in
     * order of preference.
     */
    record Selection(Object handler, MediaType mediaType, List<MediaType> acceptable) {
    }

    static void negotiate(HttpServletRequest request, List<MediaType> producedTypes)
            throws HttpMediaTypeNotAcceptableException {
        List<Produced> produced = producedTypes.stream()
                .map(mediaType -> new Produced(mediaType, mediaType, DEFAULT_QUALITY_OF_SOURCE))
                .toList();
        String accept = accept(request);
        MediaType mediaType = select(accept, produced, produced.size() == 1)
                .map(Selection::mediaType)
                .orElseThrow(() -> new HttpMediaTypeNotAcceptableException(producedTypes));
        request.setAttribute(NEGOTIATED_MEDIA_TYPE_ATTRIBUTE, mediaType);
        request.setAttribute(NEGOTIATED_CONTENT_TYPE_ATTRIBUTE, spelledAsAccepted(mediaType, accept));
    }

    /**
     * @return the request's {@code Accept} headers as one list, in the order they were sent, or null when it has none
     */
    static String accept(HttpServletRequest request) {
        List<String> values = Collections.list(request.getHeaders(HttpHeaders.ACCEPT));
        return values.isEmpty() ? null : String.join(",", values);
    }

    /**
     * @return the media type chosen for the response to the request, with its type and subtype spelled as the
     * {@code Accept} header spells them
     */
    public static String negotiatedContentType(HttpServletRequest request) {
        return (String) request.getAttribute(NEGOTIATED_CONTENT_TYPE_ATTRIBUTE);
    }

    private static String spelledAsAccepted(MediaType mediaType, String accept) {
        String name = mediaType.getType() + "/" + mediaType.getSubtype();
        String spelling = accept == null ? name : Arrays.stream(accept.split(","))
                .map(range -> range.split(";", 2)[0].strip())
                .filter(name::equalsIgnoreCase)
                .findFirst()
                .orElse(name);
        return spelling + mediaType.toString().substring(name.length());
    }

    /**
     * @return the media type chosen for the response to the request
     */
    public static MediaType negotiated(HttpServletRequest request) {
        return (MediaType) request.getAttribute(NEGOTIATED_MEDIA_TYPE_ATTRIBUTE);
    }

    @Override
    public List<MediaType> resolveMediaTypes(NativeWebRequest webRequest) {
        Object negotiated = webRequest.getAttribute(NEGOTIATED_MEDIA_TYPE_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        return negotiated instanceof MediaType mediaType ? List.of(mediaType) : MEDIA_TYPE_ALL_LIST;
    }

    /**
     * @param accept         the request's {@code Accept} header
     * @param produced       the media types the handlers produce, in the order handlers are preferred on a tie
     * @param firstMatchWins whether the first acceptable media type compatible with a produced one is chosen, rather
     *                       than the best pair
     * @return the chosen handler and media type, if any produced media type is acceptable
     */
    static Optional<Selection> select(String accept, List<Produced> produced, boolean firstMatchWins) {
        Optional<List<MediaType>> parsed = acceptable(accept);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        List<MediaType> acceptable = parsed.get();
        Candidate best = null;
        for (MediaType acceptableType : acceptable) {
            for (Produced producedType : produced) {
                if (!acceptableType.isCompatibleWith(producedType.mediaType())) {
                    continue;
                }
                Candidate candidate = new Candidate(producedType.handler(),
                        combined(acceptableType, producedType.mediaType()), quality(acceptableType),
                        producedType.qualityOfSource(), matchedWildcards(acceptableType, producedType.mediaType()));
                if (firstMatchWins) {
                    return Optional.of(new Selection(candidate.handler(), candidate.mediaType(), acceptable));
                }
                if (best == null || candidate.isBetterThan(best)) {
                    best = candidate;
                }
            }
        }
        return Optional.ofNullable(best).map(winner -> new Selection(winner.handler(), winner.mediaType(), acceptable));
    }

    private static Optional<List<MediaType>> acceptable(String accept) {
        if (accept == null || accept.isBlank()) {
            return Optional.of(List.of(MediaType.ALL));
        }
        try {
            String withSubtypes = Arrays.stream(accept.split(","))
                    .map(MediaTypeNegotiation::withSubtype)
                    .collect(Collectors.joining(","));
            return Optional.of(MediaType.parseMediaTypes(withSubtypes).stream()
                    .sorted(Comparator.comparingInt(MediaTypeNegotiation::quality).reversed()
                            .thenComparingInt(MediaTypeNegotiation::wildcards))
                    .toList());
        } catch (InvalidMediaTypeException e) {
            return Optional.empty();
        }
    }

    private static String withSubtype(String mediaRange) {
        int parameters = mediaRange.indexOf(';');
        String type = parameters < 0 ? mediaRange : mediaRange.substring(0, parameters);
        if (type.isBlank() || type.contains("/")) {
            return mediaRange;
        }
        return type.strip() + "/*" + (parameters < 0 ? "" : mediaRange.substring(parameters));
    }

    private static MediaType combined(MediaType acceptable, MediaType produced) {
        if (acceptable.isWildcardSubtype() && !produced.isWildcardSubtype()
                || acceptable.isWildcardType() && !produced.isWildcardType()) {
            return produced;
        }
        Map<String, String> parameters = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        acceptable.getParameters().forEach((name, value) -> {
            if (!"q".equalsIgnoreCase(name)) {
                parameters.putIfAbsent(name, value);
            }
        });
        return new MediaType(acceptable.getType(), acceptable.getSubtype(), parameters);
    }

    private static int quality(MediaType mediaType) {
        return (int) Math.round(mediaType.getQualityValue() * 1000);
    }

    private static int wildcards(MediaType mediaType) {
        return (mediaType.isWildcardType() ? 1 : 0) + (mediaType.isWildcardSubtype() ? 1 : 0);
    }

    private static int matchedWildcards(MediaType acceptable, MediaType produced) {
        return (acceptable.isWildcardType() ^ produced.isWildcardType() ? 1 : 0)
                + (acceptable.isWildcardSubtype() ^ produced.isWildcardSubtype() ? 1 : 0);
    }

    private record Candidate(Object handler, MediaType mediaType, int quality, int qualityOfSource, int matchedWildcards) {

        boolean isBetterThan(Candidate other) {
            if (wildcards(mediaType) != wildcards(other.mediaType)) {
                return wildcards(mediaType) < wildcards(other.mediaType);
            }
            if (quality != other.quality) {
                return quality > other.quality;
            }
            if (qualityOfSource != other.qualityOfSource) {
                return qualityOfSource > other.qualityOfSource;
            }
            return matchedWildcards < other.matchedWildcards;
        }
    }
}
