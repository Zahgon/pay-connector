package uk.gov.pay.connector.web;

import org.springframework.web.bind.annotation.ValueConstants;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds a query parameter to a handler method parameter, or to a setter or field of a {@link QueryParams} object,
 * the way the API has always read its query parameters (see {@link QueryParameterConverter}).
 */
@Documented
@Target({ElementType.PARAMETER, ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface QueryParam {

    /**
     * @return the name of the query parameter
     */
    String value();

    /**
     * @return the value used when the request does not have the parameter (or, for numbers and booleans, leaves it
     * blank)
     */
    String defaultValue() default ValueConstants.DEFAULT_NONE;
}
