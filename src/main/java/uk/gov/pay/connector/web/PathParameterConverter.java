package uk.gov.pay.connector.web;

import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalGenericConverter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import uk.gov.pay.connector.common.exception.WebApplicationException;
import uk.gov.pay.connector.util.Enums;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.String.format;

/**
 * Converts path parameters to the types of the handler method parameters that receive them, as the API always has:
 * <ul>
 *     <li>numbers with their {@code valueOf} (a value that is not a number, white space included, names no resource:
 *     404);</li>
 *     <li>enums with their {@code fromString} method if they have one (a value it refuses is a bad request: "Failed to
 *     convert path param &lt;name&gt; to &lt;type&gt;", or "path param &lt;name&gt; is not a valid &lt;type&gt;"
 *     when it finds none), otherwise by their constants, matched forgivingly (see {@link Enums#fromStringFuzzy}; "path
 *     param &lt;name&gt; must be one of [&lt;constants&gt;]" when none matches).</li>
 * </ul>
 */
public class PathParameterConverter implements ConditionalGenericConverter {

    @Override
    public Set<ConvertiblePair> getConvertibleTypes() {
        return Set.of(new ConvertiblePair(String.class, Enum.class), new ConvertiblePair(String.class, Long.class),
                new ConvertiblePair(String.class, Integer.class));
    }

    @Override
    public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
        Class<?> type = targetType.getObjectType();
        return targetType.hasAnnotation(PathVariable.class)
                && (type.isEnum() || type == Long.class || type == Integer.class);
    }

    @Override
    public Object convert(Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
        String value = (String) source;
        Class<?> type = targetType.getObjectType();
        PathVariable pathVariable = targetType.getAnnotation(PathVariable.class);
        String parameterName = "path param " + (pathVariable.value().isEmpty() ? pathVariable.name() : pathVariable.value());
        try {
            if (type == Long.class) {
                return Long.valueOf(value);
            }
            if (type == Integer.class) {
                return Integer.valueOf(value);
            }
        } catch (NumberFormatException e) {
            throw new WebApplicationException(HttpStatus.NOT_FOUND);
        }
        return enumConstant(parameterName, type, value);
    }

    /**
     * Converts a path or query parameter's value to an enum constant (see the class description); the parameter name
     * names its kind ({@code path param id}, {@code query param state}).
     */
    static Object enumConstant(String parameterName, Class<?> type, String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        Method fromString = fromStringMethod(type);
        if (fromString != null) {
            try {
                Object constant = fromString.invoke(null, value);
                if (constant != null) {
                    return constant;
                }
                throw new WebApplicationException(format("%s is not a valid %s", parameterName, type.getSimpleName()),
                        HttpStatus.BAD_REQUEST);
            } catch (IllegalAccessException e) {
                throw new WebApplicationException("Not permitted to call fromString on " + type.getSimpleName(),
                        HttpStatus.BAD_REQUEST);
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof WebApplicationException webApplicationException) {
                    throw webApplicationException;
                }
                throw new WebApplicationException("Failed to convert " + parameterName + " to " + type.getSimpleName(),
                        HttpStatus.BAD_REQUEST);
            }
        }
        Enum<?>[] constants = (Enum<?>[]) type.getEnumConstants();
        Enum<?> constant = Enums.fromStringFuzzy(value, constants);
        if (constant != null) {
            return constant;
        }
        String constantsList = Arrays.stream(constants).map(Enum::toString).collect(Collectors.joining(", "));
        throw new WebApplicationException(format("%s must be one of [%s]", parameterName, constantsList),
                HttpStatus.BAD_REQUEST);
    }

    private static Method fromStringMethod(Class<?> type) {
        try {
            Method method = type.getDeclaredMethod("fromString", String.class);
            return Modifier.isStatic(method.getModifiers()) ? method : null;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
