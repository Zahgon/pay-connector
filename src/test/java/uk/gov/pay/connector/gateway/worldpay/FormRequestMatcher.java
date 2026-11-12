package uk.gov.pay.connector.gateway.worldpay;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.mockito.ArgumentMatcher;

import java.io.IOException;
import java.net.URISyntaxException;

/**
 * Matches a POST of an {@code application/x-www-form-urlencoded} form to the given URL with the given encoded form.
 */
public class FormRequestMatcher implements ArgumentMatcher<ClassicHttpRequest> {

    private final String url;
    private final String expectedForm;

    public FormRequestMatcher(String url, String expectedForm) {
        this.url = url;
        this.expectedForm = expectedForm;
    }

    @Override
    public boolean matches(ClassicHttpRequest request) {
        if (request == null || !"POST".equals(request.getMethod())) {
            return false;
        }
        HttpEntity entity = request.getEntity();
        try {
            return request.getUri().toString().equals(url)
                    && entity != null
                    && ContentType.APPLICATION_FORM_URLENCODED.getMimeType().equals(ContentType.parse(entity.getContentType()).getMimeType())
                    && EntityUtils.toString(entity).equals(expectedForm);
        } catch (URISyntaxException | IOException | ParseException e) {
            return false;
        }
    }
}
