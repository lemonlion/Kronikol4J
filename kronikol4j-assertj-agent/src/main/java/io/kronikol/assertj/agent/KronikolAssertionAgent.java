package io.kronikol.assertj.agent;

import java.lang.instrument.Instrumentation;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;

/**
 * Installs the Tier-2 assertion-tracking agent (plan §3.9): instruments AssertJ's
 * {@code AbstractAssert} so each comparison ({@code isEqualTo}, {@code isNotNull}, …) is recorded
 * with its <strong>actual</strong> and <strong>expected</strong> values and source expression —
 * automatically, with no wrapper and no {@code .as()}. The Java analog of .NET's IL weaving.
 *
 * <p>Use as a {@code -javaagent} (this jar declares {@code Premain-Class}) or self-attach in tests
 * via {@link #install()}.
 */
public final class KronikolAssertionAgent {

    static {
        // ByteBuddy 1.15 officially supports class files up to Java 24; on a newer JVM (e.g. 25) it
        // refuses to parse AssertJ types whose generic signatures reference JDK classes unless this
        // flag is set. Set it here — before ByteBuddy's class reader initializes — so the real
        // -javaagent/install() path works on JDK 25+ without callers needing the VM property.
        // An explicit caller-supplied value is respected.
        if (System.getProperty("net.bytebuddy.experimental") == null) {
            System.setProperty("net.bytebuddy.experimental", "true");
        }
    }

    private static volatile boolean installed;

    private KronikolAssertionAgent() {
    }

    /** Self-attaches to the current JVM and installs the instrumentation (idempotent). */
    public static synchronized void install() {
        if (installed) {
            return;
        }
        installOn(ByteBuddyAgent.install());
        installed = true;
    }

    /** {@code -javaagent} entry point. */
    public static void premain(String args, Instrumentation instrumentation) {
        installOn(instrumentation);
        installed = true;
    }

    /** Dynamic-attach entry point. */
    public static void agentmain(String args, Instrumentation instrumentation) {
        installOn(instrumentation);
        installed = true;
    }

    private static void installOn(Instrumentation instrumentation) {
        new AgentBuilder.Default()
            .disableClassFormatChanges()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .with(System.getProperty("kronikol.agent.debug") != null
                ? AgentBuilder.Listener.StreamWriting.toSystemError()
                : AgentBuilder.Listener.NoOp.INSTANCE)
            .ignore(ElementMatchers.nameStartsWith("net.bytebuddy.")
                .or(ElementMatchers.nameStartsWith("io.kronikol.assertj.agent.")))
            // Match AbstractAssert AND its AssertJ subtypes: primitive-specialised overloads such as
            // AbstractIntegerAssert.isEqualTo(int) are declared on the subclass, not inherited from
            // AbstractAssert, so they're missed by a bare named() match. Scope the (expensive)
            // hasSuperType check to the assertj api package so it never runs on unrelated classes.
            .type(ElementMatchers.nameStartsWith("org.assertj.core.api.")
                .and(ElementMatchers.hasSuperType(ElementMatchers.named("org.assertj.core.api.AbstractAssert"))))
            .transform((builder, type, classLoader, module, protectionDomain) ->
                builder.visit(Advice.to(Tier2Advice.class).on(ElementMatchers.isMethod()
                    // Exclude compiler-generated bridge/synthetic overloads: a generic assertion's
                    // covariant override produces a bridge isEqualTo(Object) that just delegates to
                    // the real method — instrumenting both would record the same assertion twice.
                    .and(ElementMatchers.not(ElementMatchers.isBridge()))
                    .and(ElementMatchers.not(ElementMatchers.isSynthetic()))
                    .and(ElementMatchers.named("isEqualTo")
                        .or(ElementMatchers.named("isNotEqualTo"))
                        .or(ElementMatchers.named("isSameAs"))
                        .or(ElementMatchers.named("isNotSameAs"))
                        .or(ElementMatchers.named("isNull"))
                        .or(ElementMatchers.named("isNotNull"))))))
            .installOn(instrumentation);
    }
}
