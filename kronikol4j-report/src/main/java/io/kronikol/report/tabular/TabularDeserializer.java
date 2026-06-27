package io.kronikol.report.tabular;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Creates instances of {@code T} from column names + string row values, matching columns to a record's
 * components or a bean's writable properties / public fields via {@link #sanitizeName}. Java port of the .NET
 * {@code TabularDeserializer}. Because Java annotations cannot carry arbitrary boxed values, the
 * {@code @Inputs}/{@code @Outputs} rows are {@code String[]}; {@link #convertValue} parses each string to the
 * target type (string/enum/primitive/BigInteger/BigDecimal).
 */
public final class TabularDeserializer {

    private TabularDeserializer() {
    }

    /** Sanitizes a column/property name for matching: drop spaces, {@code &}→{@code And}, lower-cased. */
    public static String sanitizeName(String name) {
        return name.replace(" ", "").replace("&", "And").toLowerCase(Locale.ROOT);
    }

    /** Builds a {@code T} from {@code columnNames} → {@code values} (records via canonical ctor; beans via
     *  no-arg ctor + setters/public fields). */
    @SuppressWarnings("unchecked")
    public static <T> T deserialize(Class<T> type, String[] columnNames, Object[] values) {
        Map<String, Object> valueMap = new LinkedHashMap<>();
        for (int i = 0; i < columnNames.length && i < values.length; i++) {
            valueMap.put(sanitizeName(columnNames[i]), values[i]);
        }

        if (type.isRecord()) {
            RecordComponent[] components = type.getRecordComponents();
            Class<?>[] paramTypes = new Class<?>[components.length];
            Object[] args = new Object[components.length];
            for (int i = 0; i < components.length; i++) {
                paramTypes[i] = components[i].getType();
                Object raw = valueMap.get(sanitizeName(components[i].getName()));
                args[i] = convertValue(raw, components[i].getType());
            }
            try {
                Constructor<T> ctor = type.getDeclaredConstructor(paramTypes);
                ctor.setAccessible(true);
                return ctor.newInstance(args);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot deserialize record " + type.getName(), e);
            }
        }

        try {
            Constructor<T> ctor = type.getDeclaredConstructor();
            ctor.setAccessible(true);
            T instance = ctor.newInstance();
            for (Map.Entry<String, Object> e : valueMap.entrySet()) {
                applyToBean(instance, type, e.getKey(), e.getValue());
            }
            return instance;
        } catch (NoSuchMethodException noNoArg) {
            // Fall back to the longest constructor (parameter names need -parameters; else positional).
            Constructor<?> best = null;
            for (Constructor<?> c : type.getDeclaredConstructors()) {
                if (best == null || c.getParameterCount() > best.getParameterCount()) {
                    best = c;
                }
            }
            if (best == null) {
                throw new IllegalStateException("No usable constructor for " + type.getName());
            }
            Class<?>[] pt = best.getParameterTypes();
            java.lang.reflect.Parameter[] ps = best.getParameters();
            Object[] args = new Object[pt.length];
            for (int i = 0; i < pt.length; i++) {
                Object raw = valueMap.get(sanitizeName(ps[i].getName()));
                args[i] = convertValue(raw, pt[i]);
            }
            try {
                best.setAccessible(true);
                return (T) best.newInstance(args);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot deserialize " + type.getName(), e);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot deserialize " + type.getName(), e);
        }
    }

    /** Sets a single sanitized-named property on a bean via a {@code setX} setter or a public field. */
    private static void applyToBean(Object instance, Class<?> type, String sanitizedName, Object value) {
        for (Method m : type.getMethods()) {
            if (m.getParameterCount() == 1 && m.getName().startsWith("set")
                && sanitizeName(m.getName().substring(3)).equals(sanitizedName)) {
                try {
                    m.invoke(instance, convertValue(value, m.getParameterTypes()[0]));
                    return;
                } catch (ReflectiveOperationException ignored) {
                    // fall through to field
                }
            }
        }
        for (Field f : type.getFields()) {
            if (sanitizeName(f.getName()).equals(sanitizedName)) {
                try {
                    f.set(instance, convertValue(value, f.getType()));
                    return;
                } catch (ReflectiveOperationException ignored) {
                    // unsettable — skip
                }
            }
        }
    }

    /** Converts a (string) cell value to {@code targetType}. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static Object convertValue(Object value, Class<?> targetType) {
        if (value == null) {
            return defaultFor(targetType);
        }
        Class<?> t = wrap(targetType);
        String s = value.toString();
        if (t == String.class) {
            return s;
        }
        if (t.isEnum()) {
            for (Object constant : t.getEnumConstants()) {
                if (((Enum<?>) constant).name().equalsIgnoreCase(s)) {
                    return constant;
                }
            }
            return Enum.valueOf((Class<? extends Enum>) t, s); // throws with a clear message if unmatched
        }
        if (t.isInstance(value)) {
            return value;
        }
        if (t == Boolean.class) {
            return Boolean.parseBoolean(s);
        }
        if (t == Integer.class) {
            return Integer.parseInt(s.trim());
        }
        if (t == Long.class) {
            return Long.parseLong(s.trim());
        }
        if (t == Short.class) {
            return Short.parseShort(s.trim());
        }
        if (t == Byte.class) {
            return Byte.parseByte(s.trim());
        }
        if (t == Double.class) {
            return Double.parseDouble(s.trim());
        }
        if (t == Float.class) {
            return Float.parseFloat(s.trim());
        }
        if (t == Character.class) {
            return s.isEmpty() ? '\0' : s.charAt(0);
        }
        if (t == BigInteger.class) {
            return new BigInteger(s.trim());
        }
        if (t == BigDecimal.class) {
            return new BigDecimal(s.trim());
        }
        return s; // best effort
    }

    private static Object defaultFor(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0d;
        }
        if (type == float.class) {
            return 0f;
        }
        return 0; // int/short/byte
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        return Character.class;
    }

    // --- read side (used by the typed carriers to build report rows) ---

    /** Per-column readers for {@code type} (record component / getter / public field), keyed by column name. */
    static Map<String, java.util.function.Function<Object, String>> readers(Class<?> type, String[] columns) {
        Map<String, java.util.function.Function<Object, String>> byName = new LinkedHashMap<>();
        if (type.isRecord()) {
            for (RecordComponent rc : type.getRecordComponents()) {
                byName.put(sanitizeName(rc.getName()), item -> invokeRead(rc.getAccessor(), item));
            }
        } else {
            for (Method m : type.getMethods()) {
                if (m.getParameterCount() == 0 && m.getName().startsWith("get")
                    && !m.getName().equals("getClass")) {
                    byName.put(sanitizeName(m.getName().substring(3)), item -> invokeRead(m, item));
                }
            }
            for (Field f : type.getFields()) {
                byName.putIfAbsent(sanitizeName(f.getName()), item -> readField(f, item));
            }
        }
        Map<String, java.util.function.Function<Object, String>> result = new LinkedHashMap<>();
        for (String col : columns) {
            java.util.function.Function<Object, String> reader = byName.get(sanitizeName(col));
            if (reader != null) {
                result.put(col, reader);
            }
        }
        return result;
    }

    private static String invokeRead(Method accessor, Object item) {
        try {
            accessor.setAccessible(true);
            Object v = accessor.invoke(item);
            return v == null ? "null" : v.toString();
        } catch (ReflectiveOperationException e) {
            return "null";
        }
    }

    private static String readField(Field f, Object item) {
        try {
            Object v = f.get(item);
            return v == null ? "null" : v.toString();
        } catch (ReflectiveOperationException e) {
            return "null";
        }
    }
}
