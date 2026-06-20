package io.kronikol.core.context;

import java.util.Objects;

/**
 * The identity of the test that "owns" a tracked interaction: a display name and a unique id.
 * The Java-idiomatic replacement for the .NET {@code (string Name, string Id)} tuple.
 */
public record TestInfo(String name, String id) {

    public TestInfo {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(id, "id");
    }

    /** The sentinel used when a framework reports "no current test". */
    public static final TestInfo UNKNOWN = new TestInfo("Unknown", "Unknown");

    public boolean isUnknown() {
        return UNKNOWN.equals(this);
    }
}
