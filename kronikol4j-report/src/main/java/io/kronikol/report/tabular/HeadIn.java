package io.kronikol.report.tabular;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the column names for input rows of a tabular test (maps each column to a property of the input
 * type {@code T} in {@code TabularInputs<T>}). Java port of the .NET {@code [HeadIn]}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface HeadIn {
    String[] value();
}
