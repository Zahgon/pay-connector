package uk.gov.pay.connector.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Finds the settings an Apache HTTP client was built with (its default request configuration, the SSL context of its
 * connection manager), which the client does not expose, for tests that check how a factory configured it.
 */
public final class HttpClientInspector {

    private HttpClientInspector() {
    }

    public static <T> Optional<T> find(Object client, Class<T> type) {
        try {
            return find(client, type, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static <T> Optional<T> find(Object node, Class<T> type, Set<Object> seen, int depth) throws IllegalAccessException {
        if (type.isInstance(node)) {
            return Optional.of(type.cast(node));
        }
        if (node == null || depth > 8 || !seen.add(node)) {
            return Optional.empty();
        }
        if (node instanceof Map<?, ?> map) {
            return findIn(map.values(), type, seen, depth);
        }
        if (node instanceof Collection<?> collection) {
            return findIn(collection, type, seen, depth);
        }
        if (!node.getClass().getName().startsWith("org.apache.hc.")) {
            return Optional.empty();
        }
        for (Class<?> declaring = node.getClass(); declaring != null && declaring.getName().startsWith("org.apache.hc.");
             declaring = declaring.getSuperclass()) {
            for (Field field : declaring.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) {
                    continue;
                }
                field.setAccessible(true);
                Optional<T> found = find(field.get(node), type, seen, depth + 1);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    private static <T> Optional<T> findIn(Collection<?> values, Class<T> type, Set<Object> seen, int depth)
            throws IllegalAccessException {
        for (Object value : values) {
            Optional<T> found = find(value, type, seen, depth + 1);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }
}
