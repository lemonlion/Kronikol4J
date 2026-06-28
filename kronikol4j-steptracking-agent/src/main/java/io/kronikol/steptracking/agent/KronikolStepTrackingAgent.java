package io.kronikol.steptracking.agent;

import java.lang.instrument.Instrumentation;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;

/**
 * Installs the step-tracking agent: instruments every method annotated with a Kronikol4J step annotation
 * ({@code @GivenStep}/{@code @WhenStep}/{@code @ThenStep}/{@code @ButStep}/{@code @Step}) so its body is wrapped
 * in {@code StepCollector.startStep}/{@code completeStep} calls — automatically, with no source change. The
 * runtime ByteBuddy analog of .NET's {@code Kronikol.StepTracking} IL weaver.
 *
 * <p>Use as a {@code -javaagent} (this jar declares {@code Premain-Class}/{@code Agent-Class}) or self-attach
 * in tests via {@link #install()}.
 */
public final class KronikolStepTrackingAgent {

    static {
        // ByteBuddy 1.15 refuses to parse classes whose generic signatures reference JDK 25+ types unless this
        // flag is set; set it before ByteBuddy's class reader initialises (an explicit value is respected).
        if (System.getProperty("net.bytebuddy.experimental") == null) {
            System.setProperty("net.bytebuddy.experimental", "true");
        }
    }

    private static volatile boolean installed;

    private KronikolStepTrackingAgent() {
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
        var stepAnnotation = ElementMatchers.isAnnotatedWith(
            ElementMatchers.<net.bytebuddy.description.type.TypeDescription>nameStartsWith(
                    "io.kronikol.report.step.")
                .and(ElementMatchers.namedOneOf(
                    "io.kronikol.report.step.GivenStep", "io.kronikol.report.step.WhenStep",
                    "io.kronikol.report.step.ThenStep", "io.kronikol.report.step.ButStep",
                    "io.kronikol.report.step.Step")));

        new AgentBuilder.Default()
            .disableClassFormatChanges()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .with(System.getProperty("kronikol.agent.debug") != null
                ? AgentBuilder.Listener.StreamWriting.toSystemError()
                : AgentBuilder.Listener.NoOp.INSTANCE)
            .ignore(ElementMatchers.nameStartsWith("net.bytebuddy.")
                .or(ElementMatchers.nameStartsWith("io.kronikol.steptracking.agent.")))
            // Only transform types that declare at least one step-annotated method (cheap pre-filter).
            .type(ElementMatchers.declaresMethod(stepAnnotation))
            .transform((builder, type, classLoader, module, protectionDomain) ->
                builder.visit(Advice.to(StepAdvice.class).on(ElementMatchers.isMethod()
                    .and(ElementMatchers.not(ElementMatchers.isBridge()))
                    .and(ElementMatchers.not(ElementMatchers.isSynthetic()))
                    .and(stepAnnotation))))
            .installOn(instrumentation);
    }
}
