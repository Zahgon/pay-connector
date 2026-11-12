package uk.gov.pay.connector.web;

import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.springframework.boot.web.embedded.jetty.JettyServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

/**
 * Compresses the responses to GET and POST requests only (other responses do not vary by {@code Accept-Encoding}),
 * whatever their media type, unless it is already compressed (images, audio, video and archives), and inflates
 * request entities sent with {@code Content-Encoding: gzip}.
 */
@Component
public class ResponseCompression implements WebServerFactoryCustomizer<JettyServletWebServerFactory> {

    private static final int INFLATE_BUFFER_SIZE = 8 * 1024;

    @Override
    public void customize(JettyServletWebServerFactory factory) {
        factory.addServerCustomizers(server -> {
            GzipHandler gzipHandler = server.getDescendant(GzipHandler.class);
            if (gzipHandler != null) {
                gzipHandler.setIncludedMethods("GET", "POST");
                gzipHandler.setIncludedMimeTypes();
                gzipHandler.setInflateBufferSize(INFLATE_BUFFER_SIZE);
            }
        });
    }
}
