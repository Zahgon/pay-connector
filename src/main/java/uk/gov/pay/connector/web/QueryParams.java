package uk.gov.pay.connector.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds the query parameters of a request to a new instance of the annotated handler method parameter's type: every
 * setter and field of the type annotated with {@link QueryParam} receives its parameter's value, also when the
 * request does not have the parameter.
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface QueryParams {
}
