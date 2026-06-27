package io.kronikol.report.tabular;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads {@code @Inputs}/{@code @Outputs}/{@code @HeadOut} (and the supplied {@code @HeadIn} column names) off a
 * test method and constructs the {@link TabularInputs}/{@link TabularOutputs} argument values at the matching
 * parameter positions — one test invocation's args. Java port of the .NET {@code TabularResolver}.
 */
public final class TabularResolver {

    private TabularResolver() {
    }

    /**
     * Resolves tabular parameters for {@code method}.
     *
     * @param method           the test method carrying the tabular annotations
     * @param inputColumnNames the {@code @HeadIn} input column names, or {@code null} to infer from properties
     * @return the argument array for one invocation (tabular positions filled; others {@code null})
     */
    public static Object[] resolve(Method method, String[] inputColumnNames) {
        Parameter[] parameters = method.getParameters();
        Type[] genericTypes = method.getGenericParameterTypes();
        Inputs[] inputs = method.getAnnotationsByType(Inputs.class);
        Outputs[] outputs = method.getAnnotationsByType(Outputs.class);
        HeadOut headOut = method.getAnnotation(HeadOut.class);

        Object[] result = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            Class<?> raw = parameters[i].getType();
            Class<?> elementType = elementType(genericTypes[i]);
            if (raw == TabularInputs.class && elementType != null) {
                String[] columns = inputColumnNames != null && inputColumnNames.length > 0
                    ? inputColumnNames : inferColumnNames(elementType);
                result[i] = createInputs(elementType, columns, inputs);
            } else if (raw == TabularOutputs.class && elementType != null) {
                String[] columns = headOut != null && headOut.value().length > 0
                    ? headOut.value() : inferColumnNames(elementType);
                result[i] = createOutputs(elementType, columns, outputs);
            }
        }
        return result;
    }

    private static Class<?> elementType(Type parameterizedType) {
        if (parameterizedType instanceof ParameterizedType pt
            && pt.getActualTypeArguments()[0] instanceof Class<?> c) {
            return c;
        }
        return null;
    }

    private static <T> TabularInputs<T> createInputs(Class<T> elementType, String[] columns, Inputs[] rows) {
        List<T> items = new ArrayList<>(rows.length);
        for (Inputs row : rows) {
            items.add(TabularDeserializer.deserialize(elementType, columns, row.value()));
        }
        return new TabularInputs<>(items, columns, elementType);
    }

    private static <T> TabularOutputs<T> createOutputs(Class<T> elementType, String[] columns, Outputs[] rows) {
        List<T> items = new ArrayList<>(rows.length);
        for (Outputs row : rows) {
            items.add(TabularDeserializer.deserialize(elementType, columns, row.value()));
        }
        return new TabularOutputs<>(items, columns, elementType);
    }

    /** Infers column names from {@code type}: record component names, else public getter/field names. */
    static String[] inferColumnNames(Class<?> type) {
        List<String> names = new ArrayList<>();
        if (type.isRecord()) {
            for (RecordComponent rc : type.getRecordComponents()) {
                names.add(rc.getName());
            }
        } else {
            for (Method m : type.getMethods()) {
                if (m.getParameterCount() == 0 && m.getName().startsWith("get")
                    && !m.getName().equals("getClass")) {
                    names.add(decapitalize(m.getName().substring(3)));
                }
            }
            for (var f : type.getFields()) {
                if (!names.contains(f.getName())) {
                    names.add(f.getName());
                }
            }
        }
        return names.toArray(new String[0]);
    }

    private static String decapitalize(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}
