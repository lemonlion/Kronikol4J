package io.kronikol.report.step;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a BDD <strong>ButStep</strong> step. Java analog of the .NET {@code [ButStep]} attribute:
 * the build-time step weaver wraps the method body in {@link StepCollector#startStep}/{@link
 * StepCollector#completeStep} calls. {@link #value()} supplies explicit step text (otherwise the weaver
 * humanises the method name). Runtime-retained so the weaver and reflective tooling can read it.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ButStep {
    /** Explicit step text; empty = derive from the method name. */
    String value() default "";
}
