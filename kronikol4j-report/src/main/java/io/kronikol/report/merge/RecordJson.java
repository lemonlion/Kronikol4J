package io.kronikol.report.merge;

import io.kronikol.diagram.json.Json;
import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generic record &harr; JSON-tree converter used by {@link FragmentJson} to round-trip the full merge
 * fragment. Reflectively maps any record to a {@link Map} of component-name &rarr; value and back, so the
 * entire (deeply nested) report model — {@code Feature}/{@code Scenario}/{@code ScenarioStep} with their
 * parameters/segments/attachments, plus component relationships, CI metadata and flow segments — survives
 * {@code toJson}/{@code fromJson} without per-field code.
 *
 * <p>Handles records, enums, {@link Instant}, {@code String}/{@code Boolean}/{@code Integer}/{@code Long}/
 * {@code Double}, {@link Collection} (List/Set) and {@code Map<String,?>}, recursively. Numbers ride as
 * the canonical {@link Json.RawNumber}; {@code null} map values are dropped by {@link Json} and read back
 * as {@code null}. This is a Java-internal transport (round-trip), not a byte-match of the .NET fragment.
 */
final class RecordJson {

    private RecordJson() {
    }

    /** Converts an object graph into a {@link Json}-writable tree (Map/List/String/Boolean/RawNumber). */
    static Object toTree(Object v) {
        if (v == null || v instanceof String || v instanceof Boolean) {
            return v;
        }
        if (v instanceof Integer || v instanceof Long || v instanceof Short || v instanceof Byte) {
            return Json.number(((Number) v).longValue());
        }
        if (v instanceof Double || v instanceof Float) {
            return new Json.RawNumber(Double.toString(((Number) v).doubleValue()));
        }
        if (v instanceof Enum<?> e) {
            return e.name();
        }
        if (v instanceof Instant i) {
            return i.toString();
        }
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                out.put(String.valueOf(e.getKey()), toTree(e.getValue()));
            }
            return out;
        }
        if (v instanceof Collection<?> c) {
            List<Object> out = new ArrayList<>(c.size());
            for (Object e : c) {
                out.add(toTree(e));
            }
            return out;
        }
        if (v instanceof Record r) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (RecordComponent rc : r.getClass().getRecordComponents()) {
                out.put(rc.getName(), toTree(invokeAccessor(rc, r)));
            }
            return out;
        }
        throw new IllegalArgumentException("RecordJson cannot serialize " + v.getClass());
    }

    /** Reconstructs a value of the (possibly generic) {@code type} from a {@link Json}-parsed tree. */
    @SuppressWarnings("unchecked")
    static <T> T fromTree(Object node, Type type) {
        Class<?> raw = rawClass(type);
        if (raw == boolean.class) {
            return (T) (Boolean) (node != null && (Boolean) node);
        }
        if (raw == int.class) {
            return (T) (Integer) (node == null ? 0 : (int) parseLong(node));
        }
        if (raw == long.class) {
            return (T) (Long) (node == null ? 0L : parseLong(node));
        }
        if (raw == double.class) {
            return (T) (Double) (node == null ? 0.0 : parseDouble(node));
        }
        if (node == null) {
            return null;
        }
        if (raw == String.class) {
            return (T) node.toString();
        }
        if (raw == Boolean.class) {
            return (T) node;
        }
        if (raw == Integer.class) {
            return (T) (Integer) (int) parseLong(node);
        }
        if (raw == Long.class) {
            return (T) (Long) parseLong(node);
        }
        if (raw == Double.class) {
            return (T) (Double) parseDouble(node);
        }
        if (raw == Instant.class) {
            return (T) Instant.parse(node.toString());
        }
        if (raw.isEnum()) {
            return (T) Enum.valueOf(raw.asSubclass(Enum.class), node.toString());
        }
        if (Map.class.isAssignableFrom(raw)) {
            Type valueType = typeArg(type, 1);
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) node).entrySet()) {
                out.put(String.valueOf(e.getKey()), fromTree(e.getValue(), valueType));
            }
            return (T) out;
        }
        if (Set.class.isAssignableFrom(raw)) {
            Type elem = typeArg(type, 0);
            Set<Object> out = new LinkedHashSet<>();
            for (Object e : (List<?>) node) {
                out.add(fromTree(e, elem));
            }
            return (T) out;
        }
        if (Collection.class.isAssignableFrom(raw)) {
            Type elem = typeArg(type, 0);
            List<Object> out = new ArrayList<>();
            for (Object e : (List<?>) node) {
                out.add(fromTree(e, elem));
            }
            return (T) out;
        }
        if (raw.isRecord()) {
            return (T) fromRecord(node, raw);
        }
        throw new IllegalArgumentException("RecordJson cannot deserialize " + raw);
    }

    private static Object fromRecord(Object node, Class<?> raw) {
        RecordComponent[] comps = raw.getRecordComponents();
        Class<?>[] paramTypes = new Class<?>[comps.length];
        Object[] args = new Object[comps.length];
        Map<?, ?> m = (Map<?, ?>) node;
        for (int i = 0; i < comps.length; i++) {
            paramTypes[i] = comps[i].getType();
            args[i] = fromTree(m.get(comps[i].getName()), comps[i].getGenericType());
        }
        try {
            Constructor<?> ctor = raw.getDeclaredConstructor(paramTypes);
            ctor.setAccessible(true);
            return ctor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("RecordJson cannot construct " + raw.getName(), e);
        }
    }

    private static Object invokeAccessor(RecordComponent rc, Object owner) {
        try {
            rc.getAccessor().setAccessible(true);
            return rc.getAccessor().invoke(owner);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("RecordJson cannot read " + rc, e);
        }
    }

    private static Class<?> rawClass(Type type) {
        if (type instanceof Class<?> c) {
            return c;
        }
        if (type instanceof ParameterizedType pt) {
            return (Class<?>) pt.getRawType();
        }
        throw new IllegalArgumentException("Unsupported type " + type);
    }

    private static Type typeArg(Type type, int index) {
        if (type instanceof ParameterizedType pt) {
            return pt.getActualTypeArguments()[index];
        }
        return Object.class;
    }

    private static long parseLong(Object node) {
        return Long.parseLong(((Json.RawNumber) node).literal());
    }

    private static double parseDouble(Object node) {
        return Double.parseDouble(((Json.RawNumber) node).literal());
    }
}
