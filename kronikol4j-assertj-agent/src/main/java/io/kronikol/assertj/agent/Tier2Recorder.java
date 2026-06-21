package io.kronikol.assertj.agent;

import io.kronikol.core.support.SourceExpression;
import io.kronikol.core.tracking.Track;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Tier-2 capture logic (called from {@link Tier2Advice}). For each AssertJ comparison it reads
 * the assertion's {@code actual} value (the {@code AbstractAssert.actual} field), the {@code expected}
 * argument, the pass/fail outcome, and the source expression, then records a note via {@link Track}.
 * Records only when a test identity is in scope (so {@link Track} attributes it); otherwise no-ops.
 */
public final class Tier2Recorder {

    // Frames to skip when locating the user's assertion source line.
    private static final Set<String> EXCLUDE_PREFIXES = Set.of(
        "org.assertj.", "net.bytebuddy.", "io.kronikol.core.", "io.kronikol.assertj.agent.");

    // Per-thread nesting depth of instrumented assertion calls. A single user assertion can pass
    // through several instrumented frames (a subclass override delegating to super); we record only
    // the outermost so each logical assertion yields exactly one note.
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);
    private static final ConcurrentHashMap<Class<?>, Field> ACTUAL_FIELD = new ConcurrentHashMap<>();
    private static final Object ABSENT = new Object();

    private Tier2Recorder() {
    }

    /** Called on entry to an instrumented assertion; returns {@code true} iff this is the outermost. */
    public static boolean enter() {
        return ++DEPTH.get()[0] == 1;
    }

    /** Called on exit; records the assertion only for the outermost frame, then unwinds the depth. */
    public static void exit(boolean outermost, Object self, String method, Object[] args, Throwable thrown) {
        try {
            if (outermost) {
                record(self, method, args, thrown);
            }
        } finally {
            DEPTH.get()[0]--;
        }
    }

    private static void record(Object self, String method, Object[] args, Throwable thrown) {
        try {
            Object actual = readActual(self);
            Object expected = (args != null && args.length == 1) ? args[0] : ABSENT;
            boolean passed = thrown == null;
            String source = SourceExpression.forCallerOutsidePackages(EXCLUDE_PREFIXES);
            String description = describe(source, method, actual, expected);
            Track.record(description, passed, passed ? null : thrown.getMessage());
        } catch (Throwable ignored) {
            // Tracking must never break a test.
        }
    }

    private static String describe(String source, String method, Object actual, Object expected) {
        String base = (source != null && !source.isBlank())
            ? source
            : method + (expected == ABSENT ? "()" : "(" + str(expected) + ")");
        String values = "actual: " + str(actual)
            + (expected == ABSENT ? "" : ", expected: " + str(expected));
        return base + "  [" + values + "]";
    }

    private static Object readActual(Object self) {
        Field field = ACTUAL_FIELD.computeIfAbsent(self.getClass(), Tier2Recorder::findActualField);
        if (field == null) {
            return ABSENT;
        }
        try {
            return field.get(self);
        } catch (IllegalAccessException e) {
            return ABSENT;
        }
    }

    private static Field findActualField(Class<?> type) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField("actual");
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // try the superclass
            }
        }
        return null;
    }

    private static String str(Object value) {
        if (value == ABSENT) {
            return "?";
        }
        try {
            return String.valueOf(value);
        } catch (Throwable t) {
            return "<unprintable>";
        }
    }
}
