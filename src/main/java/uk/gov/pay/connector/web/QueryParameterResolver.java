package uk.gov.pay.connector.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.BeanUtils;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves handler method parameters annotated with {@link QueryParam} or {@link QueryParams} from the request's
 * query string. The query string is decoded leniently: {@code +} is a space and a {@code %} that does not start an
 * escape sequence stands for itself.
 */
public class QueryParameterResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(QueryParam.class) || parameter.hasParameterAnnotation(QueryParams.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        Map<String, List<String>> parameters = parse(request.getQueryString());
        QueryParam queryParam = parameter.getParameterAnnotation(QueryParam.class);
        if (queryParam != null) {
            return QueryParameterConverter.convert(queryParam.value(), queryParam.defaultValue(),
                    ResolvableType.forMethodParameter(parameter), parameters);
        }
        return bind(parameter.getParameterType(), parameters);
    }

    private static Object bind(Class<?> type, Map<String, List<String>> parameters) {
        Object target = BeanUtils.instantiateClass(type);
        ReflectionUtils.doWithFields(type, field -> {
            QueryParam queryParam = field.getAnnotation(QueryParam.class);
            ReflectionUtils.makeAccessible(field);
            ReflectionUtils.setField(field, target, QueryParameterConverter.convert(queryParam.value(),
                    queryParam.defaultValue(), ResolvableType.forField(field), parameters));
        }, field -> field.isAnnotationPresent(QueryParam.class));
        for (Method method : type.getMethods()) {
            QueryParam queryParam = method.getAnnotation(QueryParam.class);
            if (queryParam != null && method.getParameterCount() == 1) {
                ReflectionUtils.invokeMethod(method, target, QueryParameterConverter.convert(queryParam.value(),
                        queryParam.defaultValue(), ResolvableType.forMethodParameter(method, 0), parameters));
            }
        }
        return target;
    }

    static Map<String, List<String>> parse(String query) {
        Map<String, List<String>> parameters = new LinkedHashMap<>();
        if (query == null || query.isEmpty()) {
            return parameters;
        }
        for (String pair : query.split("&")) {
            int equals = pair.indexOf('=');
            if (pair.isEmpty() || equals == 0) {
                continue;
            }
            String name = decode(equals < 0 ? pair : pair.substring(0, equals));
            String value = equals < 0 ? "" : decode(pair.substring(equals + 1));
            parameters.computeIfAbsent(name, key -> new ArrayList<>()).add(value);
        }
        return parameters;
    }

    private static String decode(String encoded) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int i = 0; i < encoded.length(); i++) {
            char c = encoded.charAt(i);
            if (c == '+') {
                bytes.write(' ');
            } else if (c == '%' && i + 2 < encoded.length() && isHex(encoded.charAt(i + 1)) && isHex(encoded.charAt(i + 2))) {
                bytes.write(Integer.parseInt(encoded.substring(i + 1, i + 3), 16));
                i += 2;
            } else {
                bytes.writeBytes(String.valueOf(c).getBytes(StandardCharsets.UTF_8));
            }
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private static boolean isHex(char c) {
        return Character.digit(c, 16) >= 0;
    }
}
