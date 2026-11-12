package uk.gov.pay.connector.app.config;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheckRegistry;
import io.dropwizard.metrics.servlets.AdminServlet;
import io.dropwizard.metrics.servlets.HealthCheckServlet;
import io.dropwizard.metrics.servlets.MetricsServlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.dropwizard.DropwizardExports;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextType;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import uk.gov.pay.connector.web.ContainerErrorPages;
import uk.gov.pay.connector.web.RequestMethodFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * The admin port (the management server on {@code ADMIN_PORT}): the operational menu with {@code /ping},
 * {@code /healthcheck} (the service's health checks), {@code /threads}, the operational tasks under {@code /tasks} and the metrics as JSON, and {@code /metrics}
 * in the Prometheus text format. Any other path is not found, with the servlet container's error page; TRACE and
 * extension methods are not allowed.
 */
@ManagementContextConfiguration(value = ManagementContextType.CHILD, proxyBeanMethods = false)
public class AdminContextConfiguration {

    @Bean
    public ServletContextInitializer adminServletRegistries(MetricRegistry metricRegistry,
                                                            HealthCheckRegistry healthCheckRegistry) {
        return servletContext -> {
            servletContext.setAttribute(MetricsServlet.METRICS_REGISTRY, metricRegistry);
            servletContext.setAttribute(HealthCheckServlet.HEALTH_CHECK_REGISTRY, healthCheckRegistry);
        };
    }

    @Bean
    public ServletRegistrationBean<AdminServlet> adminServlet() {
        return new ServletRegistrationBean<>(new AdminMenuServlet(), "/*");
    }

    @Bean
    public ServletRegistrationBean<io.prometheus.client.servlet.jakarta.exporter.MetricsServlet> prometheusMetricsServlet(
            MetricRegistry metricRegistry) {
        CollectorRegistry collectorRegistry = CollectorRegistry.defaultRegistry;
        collectorRegistry.register(new DropwizardExports(metricRegistry));
        return new ServletRegistrationBean<>(
                new io.prometheus.client.servlet.jakarta.exporter.MetricsServlet(collectorRegistry), "/metrics");
    }

    @Bean
    public ServletRegistrationBean<AdminTaskServlet> adminTaskServlet() {
        return new ServletRegistrationBean<>(new AdminTaskServlet(), "/tasks/*");
    }

    @Bean
    public FilterRegistrationBean<RequestMethodFilter> adminRequestMethodFilter() {
        return new FilterRegistrationBean<>(new RequestMethodFilter());
    }

    @Bean
    public ContainerErrorPages adminContainerErrorPages() {
        return new ContainerErrorPages();
    }

    /**
     * The admin servlet, whose operational menu is an HTML page in UTF-8.
     */
    private static class AdminMenuServlet extends AdminServlet {

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
            super.doGet(req, resp);
        }
    }
}
