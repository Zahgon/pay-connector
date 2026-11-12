package uk.gov.pay.connector.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The media types a handler method answers with and reads, enforced by {@link MediaTypeInterceptor} the way the API
 * has always enforced them: the answer's media type is negotiated with the request's {@code Accept} header among
 * {@link #produces()} (406 when none is acceptable), and a request entity must have one of the {@link #consumes()}
 * media types (415 otherwise). An empty list leaves that side open, as for an endpoint that never declared it.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MediaTypes {

    /**
     * @return the media types of the answers, in order of preference
     */
    String[] produces() default {};

    /**
     * @return the media types of request entities the endpoint reads
     */
    String[] consumes() default {};
}
