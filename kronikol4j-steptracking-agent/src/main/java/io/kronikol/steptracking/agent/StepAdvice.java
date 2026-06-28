package io.kronikol.steptracking.agent;

import java.lang.reflect.Method;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

/**
 * ByteBuddy advice inlined into each step-annotated method: opens the step on entry and completes it on exit
 * (failed when the body threw, async-aware when it returns a {@link java.util.concurrent.CompletableFuture}).
 * Kept tiny because its body is copied into the user's bytecode; all logic lives in {@link StepAgentRecorder}.
 */
public final class StepAdvice {

    private StepAdvice() {
    }

    @Advice.OnMethodEnter
    public static void onEnter(@Advice.Origin Method method, @Advice.AllArguments Object[] args) {
        StepAgentRecorder.enter(method, args);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void onExit(
            @Advice.Return(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object returned,
            @Advice.Thrown Throwable thrown) {
        returned = StepAgentRecorder.exit(returned, thrown);
    }
}
