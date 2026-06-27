package io.kronikol.report.tabular;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the column names for expected output rows of a tabular test (maps each column to a property of the
 * output type {@code T} in {@code TabularOutputs<T>}). Java port of the .NET {@code [HeadOut]}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface HeadOut {
    String[] value();
}
