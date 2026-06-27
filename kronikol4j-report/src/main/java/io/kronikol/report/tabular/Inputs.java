package io.kronikol.report.tabular;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares one row of input data for a tabular test (repeat for multiple rows). Column names come from
 * {@code @HeadIn} (or are inferred from the input type's properties). Java port of the .NET {@code [Inputs]};
 * because Java annotations cannot carry arbitrary boxed values, the row is {@code String[]} and each cell is
 * converted to its target property type by {@link TabularDeserializer}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(Inputs.List.class)
public @interface Inputs {
    String[] value();

    /** Container for repeated {@link Inputs}. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface List {
        Inputs[] value();
    }
}
