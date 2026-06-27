package io.kronikol.report.tabular;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares one row of expected output data for a tabular test (repeat for multiple rows). Column names come
 * from {@code @HeadOut} (or are inferred). Java port of the .NET {@code [Outputs]}; the row is {@code String[]}
 * (see {@link Inputs}).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(Outputs.List.class)
public @interface Outputs {
    String[] value();

    /** Container for repeated {@link Outputs}. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface List {
        Outputs[] value();
    }
}
