package uk.gov.pay.connector.web;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

/**
 * Text entities as the API has always read and written them: a request entity received as text is the empty string
 * when the request has none, and a text answer names the media type it was negotiated as, without adding a charset
 * (the text itself is UTF-8).
 */
@ControllerAdvice
public class TextEntities extends RequestBodyAdviceAdapter {

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return targetType == String.class;
    }

    @Override
    public Object handleEmptyBody(Object body, HttpInputMessage inputMessage, MethodParameter parameter, Type targetType,
                                  Class<? extends HttpMessageConverter<?>> converterType) {
        return body == null ? "" : body;
    }

    /**
     * Writes text answers in UTF-8 under the negotiated media type as it is.
     */
    public static class TextMessageConverter extends StringHttpMessageConverter {

        public TextMessageConverter() {
            super(StandardCharsets.UTF_8);
        }

        @Override
        protected void addDefaultHeaders(HttpHeaders headers, String text, MediaType contentType) {
            if (headers.getContentType() == null && contentType != null) {
                headers.setContentType(contentType);
            }
            if (headers.getContentLength() < 0) {
                headers.setContentLength(text.getBytes(StandardCharsets.UTF_8).length);
            }
        }
    }
}
