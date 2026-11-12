package uk.gov.pay.connector.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.filter.UrlHandlerFilter;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import uk.gov.pay.connector.filters.LoggingMDCInterceptor;
import uk.gov.service.payments.logging.LoggingFilter;

import java.io.IOException;
import java.util.List;

/**
 * Spring MVC settings that keep the API's request handling as clients know it: OPTIONS answers, media type
 * negotiation and checks, query parameters, trailing slashes and request logging.
 */
@Configuration(proxyBeanMethods = false)
public class WebConfiguration implements WebMvcConfigurer {

    private final ResourceMethods resourceMethods;
    private final LoggingMDCInterceptor loggingMDCInterceptor;

    public WebConfiguration(ResourceMethods resourceMethods, LoggingMDCInterceptor loggingMDCInterceptor) {
        this.resourceMethods = resourceMethods;
        this.loggingMDCInterceptor = loggingMDCInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new EncodedPathInterceptor());
        registry.addInterceptor(new OptionsInterceptor(resourceMethods));
        registry.addInterceptor(new MediaTypeInterceptor());
        registry.addInterceptor(loggingMDCInterceptor);
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new PathParameterConverter());
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new QueryParameterResolver());
    }

    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer.strategies(List.of(new MediaTypeNegotiation()));
    }

    /**
     * OPTIONS requests are answered by the API alone: an OPTIONS request for a path the API does not know is not
     * found, without an {@code Allow} header listing every HTTP method.
     */
    @Bean(name = DispatcherServletAutoConfiguration.DEFAULT_DISPATCHER_SERVLET_BEAN_NAME)
    public DispatcherServlet dispatcherServlet() {
        DispatcherServlet dispatcherServlet = new DispatcherServlet() {
            @Override
            protected void doOptions(HttpServletRequest request, HttpServletResponse response)
                    throws ServletException, IOException {
                processRequest(request, response);
            }
        };
        dispatcherServlet.setDispatchOptionsRequest(true);
        return dispatcherServlet;
    }

    @Bean
    public FilterRegistrationBean<LoggingFilter> loggingFilter() {
        FilterRegistrationBean<LoggingFilter> registration = new FilterRegistrationBean<>(new LoggingFilter());
        registration.addUrlPatterns("/v1/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<EntitylessErrorFilter> entitylessErrorFilter() {
        FilterRegistrationBean<EntitylessErrorFilter> registration = new FilterRegistrationBean<>(new EntitylessErrorFilter());
        registration.setOrder(Ordered.LOWEST_PRECEDENCE);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<RequestMethodFilter> requestMethodFilter() {
        FilterRegistrationBean<RequestMethodFilter> registration = new FilterRegistrationBean<>(new RequestMethodFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<CorsRequestFilter> corsRequestFilter() {
        FilterRegistrationBean<CorsRequestFilter> registration = new FilterRegistrationBean<>(new CorsRequestFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 6);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<MalformedContentTypeFilter> malformedContentTypeFilter() {
        FilterRegistrationBean<MalformedContentTypeFilter> registration =
                new FilterRegistrationBean<>(new MalformedContentTypeFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<UrlHandlerFilter> trailingSlashFilter() {
        FilterRegistrationBean<UrlHandlerFilter> registration =
                new FilterRegistrationBean<>(UrlHandlerFilter.trailingSlashHandler("/**").wrapRequest().build());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 3);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<EntityBufferingFilter> entityBufferingFilter() {
        FilterRegistrationBean<EntityBufferingFilter> registration = new FilterRegistrationBean<>(new EntityBufferingFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 4);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<UnknownCharsetFilter> unknownCharsetFilter() {
        FilterRegistrationBean<UnknownCharsetFilter> registration = new FilterRegistrationBean<>(new UnknownCharsetFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
        return registration;
    }
}
