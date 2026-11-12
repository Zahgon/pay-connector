package uk.gov.pay.connector.web;

import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

import java.beans.PropertyEditorSupport;
import java.util.function.Function;

/**
 * Reads whole-number path parameters with the number type's own {@code valueOf}, as the API always has: a value that
 * is not a decimal number exactly (white space, a hexadecimal prefix, a fraction) names no resource.
 */
@ControllerAdvice
public class PathParameterBinding {

    @InitBinder
    public void numbers(WebDataBinder binder) {
        binder.registerCustomEditor(Long.class, new ValueOfEditor(Long::valueOf));
        binder.registerCustomEditor(Integer.class, new ValueOfEditor(Integer::valueOf));
    }

    private static final class ValueOfEditor extends PropertyEditorSupport {

        private final Function<String, Object> valueOf;

        private ValueOfEditor(Function<String, Object> valueOf) {
            this.valueOf = valueOf;
        }

        @Override
        public void setAsText(String text) {
            setValue(text == null ? null : valueOf.apply(text));
        }
    }
}
