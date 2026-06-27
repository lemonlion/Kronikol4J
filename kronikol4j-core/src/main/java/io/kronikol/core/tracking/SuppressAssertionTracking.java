package io.kronikol.core.tracking;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test method (or class) whose assertions should <em>not</em> be auto-tracked by the build-time
 * assertion weaver. Java analog of the .NET {@code [SuppressAssertionTracking]}. The annotation is the
 * opt-out marker the weaver honours; explicit {@link Track#that} calls are unaffected.
 *
 * <p>Retained at runtime so the weaver/agent (the Tier-5 assertion-rewriter) and reflective tooling can read
 * it. Applying it to a class suppresses tracking for all its test methods.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface SuppressAssertionTracking {
}
