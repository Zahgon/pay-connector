package uk.gov.pay.connector.web;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Reads and writes the API's JSON entities with the service's object mapper. A request entity is one JSON value
 * (anything after it makes the entity unreadable) of any content type whose subtype is {@code json},
 * {@code x-json}, {@code javascript}, {@code x-javascript} or ends with {@code +json}.
 */
public class JsonMessageConverter extends MappingJackson2HttpMessageConverter {

    private static final String RFD_DISPOSITION = "inline;filename=f.txt";
    private static final Set<String> READABLE_SUBTYPES = Set.of("json", "x-json", "javascript", "x-javascript");

    public JsonMessageConverter(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    public static boolean isReadable(MediaType mediaType) {
        String subtype = mediaType.getSubtype().toLowerCase();
        return READABLE_SUBTYPES.contains(subtype) || subtype.endsWith("+json");
    }

    @Override
    protected ObjectReader customizeReader(ObjectReader reader, JavaType javaType) {
        return reader.with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    /**
     * A request entity that holds nothing but white space reads as no entity at all, as an empty one does.
     */
    @Override
    public Object read(Type type, Class<?> contextClass, HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {
        byte[] entity = inputMessage.getBody().readAllBytes();
        if (isWhiteSpace(entity)) {
            return null;
        }
        return super.read(type, contextClass, new HttpInputMessage() {
            @Override
            public InputStream getBody() {
                return new ByteArrayInputStream(entity);
            }

            @Override
            public HttpHeaders getHeaders() {
                return inputMessage.getHeaders();
            }
        });
    }

    private static boolean isWhiteSpace(byte[] entity) {
        for (byte b : entity) {
            if (b != ' ' && b != '\t' && b != '\r' && b != '\n') {
                return false;
            }
        }
        return true;
    }

    /**
     * A request entity is parsed from its bytes, in the Unicode encoding (UTF-8, UTF-16 or UTF-32) the parser detects
     * from them; the character set its media type names is not used.
     */
    @Override
    protected Charset getCharset(MediaType contentType) {
        return StandardCharsets.UTF_8;
    }

    /**
     * Error answers carry no reflected-file-download {@code Content-Disposition}, whatever the request path ends in.
     */
    @Override
    protected void writeInternal(Object object, Type type, HttpOutputMessage outputMessage)
            throws IOException, HttpMessageNotWritableException {
        if (RFD_DISPOSITION.equals(outputMessage.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))) {
            outputMessage.getHeaders().remove(HttpHeaders.CONTENT_DISPOSITION);
        }
        super.writeInternal(object, type, outputMessage);
    }

    /**
     * JSON is always written in UTF-8, whatever character set the response's media type names.
     */
    @Override
    protected JsonEncoding getJsonEncoding(MediaType contentType) {
        return JsonEncoding.UTF8;
    }

    /**
     * JSON can be written for any JSON media type, whatever character set it names.
     */
    @Override
    public boolean canWrite(Class<?> clazz, MediaType mediaType) {
        return super.canWrite(clazz, mediaType == null ? null : withoutCharset(mediaType));
    }

    private static MediaType withoutCharset(MediaType mediaType) {
        Map<String, String> parameters = new LinkedHashMap<>(mediaType.getParameters());
        parameters.keySet().removeIf("charset"::equalsIgnoreCase);
        return new MediaType(mediaType.getType(), mediaType.getSubtype(), parameters);
    }

    @Override
    protected boolean canRead(MediaType mediaType) {
        return mediaType == null || isReadable(mediaType);
    }
}
