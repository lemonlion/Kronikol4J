package io.kronikol.core.serialization;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * Safely serialises captured values to JSON for diagram note content, handling edge cases such as mock
 * proxies, pending/completed futures, skip-types, circular references and a maximum depth. Java port of the
 * .NET {@code TrackingSafeSerializer}.
 *
 * <p>.NET delegates the actual object→JSON to {@code System.Text.Json}. The JDK ships no reflective JSON
 * serialiser and {@code kronikol4j-core} has zero runtime dependencies, so this class includes a small
 * dependency-free reflective JSON writer (maps, collections, arrays, records, public-getter POJOs and
 * primitives) with {@code IgnoreCycles}-style circular-reference handling and a max-depth guard. On any
 * serialisation failure it falls back to the value's quoted {@code toString()} — exactly as the .NET
 * {@code catch} branch does.
 *
 * <p><strong>Determinism note.</strong> POJO getters have no reflection-stable order, so getter-derived
 * properties are emitted in alphabetical order (records and maps keep their declared/insertion order). When
 * this is wired into golden-proven rendered output, explicit ordering can be revisited.
 */
public final class TrackingSafeSerializer {

    private TrackingSafeSerializer() {
    }

    /** Marker exception used to trigger the {@code toString()} fallback on depth overflow. */
    private static final class SerializationLimitException extends RuntimeException {
        SerializationLimitException(String message) {
            super(message);
        }
    }

    /** Serialises {@code value} to JSON, returning {@code null} for a null input. */
    public static String serialize(Object value, TrackingSerializerOptions options) {
        if (value == null) {
            return null;
        }
        TrackingSerializerOptions opts = options != null ? options : TrackingSerializerOptions.defaults();

        if (opts.skipMockProxies() && isMockProxy(value, opts.mockProxyMarkers())) {
            return "\"<mock proxy>\"";
        }
        if (opts.unwrapFutures() && value instanceof Future<?> future) {
            return serializeFuture(future, opts);
        }
        if (value instanceof Object[] array) {
            return serializeFilteredArray(array, opts);
        }
        return serializeValue(value, opts);
    }

    private static String serializeFuture(Future<?> future, TrackingSerializerOptions options) {
        // Only a future that is done, not cancelled, and not completed exceptionally counts as "successful".
        if (!future.isDone() || future.isCancelled()) {
            return "\"<pending Task>\"";
        }
        if (future instanceof CompletableFuture<?> cf && cf.isCompletedExceptionally()) {
            return "\"<pending Task>\"";
        }
        try {
            Object result = future.get();
            return result == null ? null : serializeValue(result, options);
        } catch (Exception e) {
            return "\"<pending Task>\"";
        }
    }

    private static String serializeFilteredArray(Object[] array, TrackingSerializerOptions options) {
        Set<Class<?>> skip = options.skipTypes();
        List<Object> filtered = new ArrayList<>(array.length);
        for (Object item : array) {
            if (item == null) {
                filtered.add(null); // null elements are kept
                continue;
            }
            Class<?> type = item.getClass();
            if (!skip.isEmpty() && skip.contains(type)) {
                continue;
            }
            if (options.skipMockProxies() && isMockProxy(item, options.mockProxyMarkers())) {
                continue;
            }
            filtered.add(item);
        }
        return serializeValue(filtered, options);
    }

    private static boolean isMockProxy(Object value, List<String> markers) {
        String name = value.getClass().getName();
        for (String marker : markers) {
            if (name.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    // --- reflective JSON writer ---------------------------------------------------------------------

    private static String serializeValue(Object value, TrackingSerializerOptions options) {
        try {
            StringBuilder sb = new StringBuilder();
            Set<Object> ancestors = Collections.newSetFromMap(new IdentityHashMap<>());
            writeValue(value, sb, 0, options, ancestors);
            return sb.toString();
        } catch (RuntimeException failure) {
            return "\"" + value + "\"";
        }
    }

    private static void writeValue(Object value, StringBuilder sb, int depth,
                                   TrackingSerializerOptions options, Set<Object> ancestors) {
        if (depth > options.maxDepth()) {
            throw new SerializationLimitException("max depth " + options.maxDepth() + " exceeded");
        }
        if (value == null) {
            sb.append("null");
        } else if (value instanceof CharSequence || value instanceof Character || value instanceof Enum<?>) {
            writeString(value.toString(), sb);
        } else if (value instanceof Boolean b) {
            sb.append(b.booleanValue() ? "true" : "false");
        } else if (value instanceof Number n) {
            sb.append(String.valueOf(n));
        } else if (value instanceof Map<?, ?> map) {
            writeObject(toOrderedMap(map), value, sb, depth, options, ancestors);
        } else if (value instanceof Iterable<?> iterable) {
            writeArray(iterableToList(iterable), value, sb, depth, options, ancestors);
        } else if (value.getClass().isArray()) {
            writeArray(arrayToList(value), value, sb, depth, options, ancestors);
        } else if (value.getClass().isRecord()) {
            writeObject(recordProperties(value), value, sb, depth, options, ancestors);
        } else {
            Map<String, Object> props = getterProperties(value);
            if (props == null) {
                writeString(value.toString(), sb); // no JSON-able properties → its string form
            } else {
                writeObject(props, value, sb, depth, options, ancestors);
            }
        }
    }

    private static void writeObject(Map<String, Object> properties, Object owner, StringBuilder sb, int depth,
                                    TrackingSerializerOptions options, Set<Object> ancestors) {
        if (!ancestors.add(owner)) {
            sb.append("null"); // IgnoreCycles: a back-reference is written as null
            return;
        }
        try {
            List<Map.Entry<String, Object>> kept = new ArrayList<>();
            for (Map.Entry<String, Object> e : properties.entrySet()) {
                if (e.getValue() != null) { // strip null object-properties (WhenWritingNull)
                    kept.add(e);
                }
            }
            if (kept.isEmpty()) {
                sb.append("{}");
                return;
            }
            boolean indent = options.writeIndented();
            sb.append('{').append(indent ? "\n" : "");
            for (int i = 0; i < kept.size(); i++) {
                if (indent) {
                    indent(sb, depth + 1);
                }
                writeString(kept.get(i).getKey(), sb);
                sb.append(':').append(indent ? " " : "");
                writeValue(kept.get(i).getValue(), sb, depth + 1, options, ancestors);
                sb.append(i < kept.size() - 1 ? "," : "").append(indent ? "\n" : "");
            }
            if (indent) {
                indent(sb, depth);
            }
            sb.append('}');
        } finally {
            ancestors.remove(owner);
        }
    }

    private static void writeArray(List<Object> elements, Object owner, StringBuilder sb, int depth,
                                   TrackingSerializerOptions options, Set<Object> ancestors) {
        if (!ancestors.add(owner)) {
            sb.append("null");
            return;
        }
        try {
            if (elements.isEmpty()) {
                sb.append("[]");
                return;
            }
            boolean indent = options.writeIndented();
            sb.append('[').append(indent ? "\n" : "");
            for (int i = 0; i < elements.size(); i++) {
                if (indent) {
                    indent(sb, depth + 1);
                }
                writeValue(elements.get(i), sb, depth + 1, options, ancestors); // null elements kept
                sb.append(i < elements.size() - 1 ? "," : "").append(indent ? "\n" : "");
            }
            if (indent) {
                indent(sb, depth);
            }
            sb.append(']');
        } finally {
            ancestors.remove(owner);
        }
    }

    private static Map<String, Object> toOrderedMap(Map<?, ?> map) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            out.put(String.valueOf(e.getKey()), e.getValue());
        }
        return out;
    }

    private static Map<String, Object> recordProperties(Object value) {
        Map<String, Object> props = new LinkedHashMap<>();
        for (RecordComponent rc : value.getClass().getRecordComponents()) {
            try {
                props.put(rc.getName(), rc.getAccessor().invoke(value));
            } catch (ReflectiveOperationException e) {
                throw new SerializationLimitException("record accessor failed: " + rc.getName());
            }
        }
        return props;
    }

    /** Public-getter properties in alphabetical order, or null if the type exposes none. */
    private static Map<String, Object> getterProperties(Object value) {
        Map<String, Object> props = new java.util.TreeMap<>();
        for (Method m : value.getClass().getMethods()) {
            if (m.getParameterCount() != 0 || m.getDeclaringClass() == Object.class
                || java.lang.reflect.Modifier.isStatic(m.getModifiers()) || m.getReturnType() == void.class) {
                continue;
            }
            String name = m.getName();
            String property;
            if (name.startsWith("get") && name.length() > 3) {
                property = decapitalize(name.substring(3));
            } else if (name.startsWith("is") && name.length() > 2 && m.getReturnType() == boolean.class) {
                property = decapitalize(name.substring(2));
            } else {
                continue;
            }
            if (property.equals("class")) {
                continue;
            }
            try {
                props.put(property, m.invoke(value));
            } catch (ReflectiveOperationException e) {
                throw new SerializationLimitException("getter failed: " + name);
            }
        }
        return props.isEmpty() ? null : new LinkedHashMap<>(props);
    }

    private static List<Object> iterableToList(Iterable<?> iterable) {
        List<Object> list = new ArrayList<>();
        for (Object o : iterable) {
            list.add(o);
        }
        return list;
    }

    private static List<Object> arrayToList(Object array) {
        int len = Array.getLength(array);
        List<Object> list = new ArrayList<>(len);
        for (int i = 0; i < len; i++) {
            list.add(Array.get(array, i));
        }
        return list;
    }

    private static String decapitalize(String s) {
        if (s.isEmpty()) {
            return s;
        }
        // Match JavaBeans: leave names whose first two chars are upper case (e.g. "URL") unchanged.
        if (s.length() > 1 && Character.isUpperCase(s.charAt(1))) {
            return s;
        }
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private static void writeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c); // UnsafeRelaxed: < > & + and non-ASCII pass through
                    }
                }
            }
        }
        sb.append('"');
    }

    private static void indent(StringBuilder sb, int level) {
        sb.append("  ".repeat(level));
    }
}
