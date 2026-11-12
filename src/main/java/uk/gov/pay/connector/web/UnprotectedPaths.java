package uk.gov.pay.connector.web;

import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.springframework.boot.web.embedded.jetty.JettyServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

/**
 * Every path reaches the API, {@code /WEB-INF} and {@code /META-INF} included: the service serves no web application
 * files for the servlet container to protect.
 */
@Component
public class UnprotectedPaths implements WebServerFactoryCustomizer<JettyServletWebServerFactory> {

    @Override
    public void customize(JettyServletWebServerFactory factory) {
        factory.addServerCustomizers(server -> {
            ServletContextHandler context = server.getDescendant(ServletContextHandler.class);
            if (context != null) {
                context.setProtectedTargets(new String[0]);
            }
        });
    }
}
