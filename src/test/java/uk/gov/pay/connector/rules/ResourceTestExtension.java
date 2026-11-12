package uk.gov.pay.connector.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.GenericWebApplicationContext;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.filters.LoggingMDCInterceptor;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.util.ObjectMapperFactory;
import uk.gov.pay.connector.web.ExceptionMapper;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.mock;

/**
 * Serves the given resources (controllers whose collaborators the test mocks) through the service's own request
 * handling: its interceptors, filters, parameter conversion, JSON mapper and exception mappers. Requests go through
 * {@link MockMvc}, so no port is opened. Registered on a static field, the resources are served for the whole test
 * class; on an instance field (whose resources use the test instance's mocks), for one test.
 */
public class ResourceTestExtension implements BeforeAllCallback, AfterAllCallback, AfterEachCallback {

    private final List<Object> resources;
    private GenericWebApplicationContext context;
    private MockMvc mockMvc;
    private boolean perClass;

    private ResourceTestExtension(List<Object> resources) {
        this.resources = resources;
    }

    public static ResourceTestExtension withResources(Object... resources) {
        return new ResourceTestExtension(List.of(resources));
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        perClass = true;
    }

    private MockMvc mockMvc() {
        if (mockMvc != null) {
            return mockMvc;
        }
        context = new GenericWebApplicationContext(new MockServletContext());
        AnnotationConfigUtils.registerAnnotationConfigProcessors(context);
        context.registerBean(ResourceTestConfiguration.class);
        for (Object resource : resources) {
            registerResource(resource);
        }
        context.refresh();
        DefaultMockMvcBuilder builder = MockMvcBuilders.webAppContextSetup(context);
        List<FilterRegistrationBean<?>> filters = new ArrayList<>();
        context.getBeansOfType(FilterRegistrationBean.class).values().forEach(filters::add);
        filters.sort(AnnotationAwareOrderComparator.INSTANCE);
        for (FilterRegistrationBean<?> registration : filters) {
            Filter filter = registration.getFilter();
            builder.addFilter(filter, registration.getUrlPatterns().toArray(String[]::new));
        }
        mockMvc = builder.build();
        return mockMvc;
    }

    @SuppressWarnings("unchecked")
    private <T> void registerResource(T resource) {
        context.registerBean(resource.getClass().getName(), (Class<T>) resource.getClass(), () -> resource);
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) {
        if (!perClass) {
            close();
        }
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        if (extensionContext.getRequiredTestClass().getEnclosingClass() == null) {
            close();
        }
    }

    private void close() {
        if (context != null) {
            context.close();
            context = null;
            mockMvc = null;
        }
    }

    public ResourceTestTarget target(String path) {
        return new ResourceTestTarget(mockMvc(), path);
    }

    /**
     * The service's request handling without the rest of the service: the web package, every exception mapper, the
     * service's JSON mapper and a request logging context that finds no charge or account.
     */
    @TestConfiguration(proxyBeanMethods = false)
    @ImportAutoConfiguration({WebMvcAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class,
            ValidationAutoConfiguration.class})
    @ComponentScan(basePackages = "uk.gov.pay.connector.web")
    @ComponentScan(basePackages = "uk.gov.pay.connector", useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ExceptionMapper.class))
    static class ResourceTestConfiguration {

        @Bean
        @Primary
        public ObjectMapper objectMapper() {
            return ObjectMapperFactory.newObjectMapper();
        }

        @Bean
        public LoggingMDCInterceptor loggingMDCInterceptor() {
            return new LoggingMDCInterceptor(mock(ChargeService.class), mock(GatewayAccountService.class));
        }
    }
}
