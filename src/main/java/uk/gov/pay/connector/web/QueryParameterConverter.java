package uk.gov.pay.connector.web;

import org.springframework.core.ResolvableType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ValueConstants;
import uk.gov.pay.connector.common.exception.WebApplicationException;
import uk.gov.pay.connector.util.Enums;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.lang.String.format;

/**
 * Converts the values of a query parameter to the type that receives them, as the API has always done:
 * <ul>
 *     <li>a parameter the request repeats counts with its first value, except for lists, which get every value;</li>
 *     <li>absent numbers and booleans take the default value (else {@code null}, or zero/false for primitives), as do
 *     blank primitives and empty boxed numbers; booleans are {@code true} only for "true" in any case (an empty boxed
 *     boolean is {@code false});</li>
 *     <li>enum constants are matched forgivingly (see {@link Enums#fromStringFuzzy}), an empty value counting as
 *     absent and an unknown value refused with 400 and "{@code query param <name> must be one of [<constants>]}";</li>
 *     <li>other types are constructed from the value (including an empty one) with their string constructor;</li>
 *     <li>a value that cannot be converted makes the resource not found (404);</li>
 *     <li>an {@code Optional} holds the converted value, and is empty when there is none; a value its type refuses
 *     makes the resource not found (404).</li>
 * </ul>
 */
final class QueryParameterConverter {

    private static final Map<Class<?>, Object> PRIMITIVE_DEFAULTS = Map.of(
            int.class, 0, long.class, 0L, boolean.class, false);

    private QueryParameterConverter() {
    }

    static Object convert(String name, String defaultValue, ResolvableType type, Map<String, List<String>> parameters) {
        List<String> values = parameters.getOrDefault(name, List.of());
        boolean hasDefault = !ValueConstants.DEFAULT_NONE.equals(defaultValue);
        Class<?> rawType = type.toClass();

        if (Optional.class.equals(rawType)) {
            ResolvableType elementType = type.getGeneric(0);
            try {
                return Optional.ofNullable(convert(name, defaultValue, elementType, parameters));
            } catch (WebApplicationException e) {
                // a value an optional parameter's type refuses names no resource
                throw new WebApplicationException(HttpStatus.NOT_FOUND);
            }
        }

        if (List.class.isAssignableFrom(rawType)) {
            if (values.isEmpty()) {
                return hasDefault ? List.of(defaultValue) : List.of();
            }
            return List.copyOf(values);
        }

        String value = values.isEmpty() ? null : values.getFirst();
        if (rawType.isPrimitive()) {
            if (value != null && !value.trim().isEmpty()) {
                return valueOf(rawType, value);
            }
            if (hasDefault) {
                return valueOf(rawType, defaultValue);
            }
            return PRIMITIVE_DEFAULTS.get(rawType);
        }
        if (isWrapper(rawType)) {
            if (value == null) {
                return hasDefault ? valueOf(rawType, defaultValue) : null;
            }
            if (value.isEmpty() && !Boolean.class.equals(rawType)) {
                return hasDefault ? valueOf(rawType, defaultValue) : null;
            }
            return valueOf(rawType, value);
        }
        if (value == null) {
            return hasDefault ? fromString(name, rawType, defaultValue) : null;
        }
        Object converted = fromString(name, rawType, value);
        if (converted == null && hasDefault) {
            return fromString(name, rawType, defaultValue);
        }
        return converted;
    }

    private static boolean isWrapper(Class<?> type) {
        return type == Integer.class || type == Long.class || type == Boolean.class;
    }

    private static Object valueOf(Class<?> type, String value) {
        try {
            if (type == int.class || type == Integer.class) {
                return Integer.valueOf(value);
            }
            if (type == long.class || type == Long.class) {
                return Long.valueOf(value);
            }
            return Boolean.valueOf(value);
        } catch (NumberFormatException e) {
            throw new WebApplicationException(HttpStatus.NOT_FOUND);
        }
    }

    private static Object fromString(String name, Class<?> type, String value) {
        if (type == String.class) {
            return value;
        }
        if (type.isEnum()) {
            return enumConstant(name, type, value);
        }
        try {
            Constructor<?> constructor = type.getConstructor(String.class);
            return constructor.newInstance(value);
        } catch (InvocationTargetException e) {
            if (value.isEmpty()) {
                return null;
            }
            if (e.getCause() instanceof WebApplicationException webApplicationException) {
                throw webApplicationException;
            }
            throw new WebApplicationException(HttpStatus.NOT_FOUND);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(format("%s cannot be read from query parameter %s", type.getName(), name), e);
        }
    }

    private static Object enumConstant(String name, Class<?> type, String value) {
        return PathParameterConverter.enumConstant("query param " + name, type, value);
    }
}
