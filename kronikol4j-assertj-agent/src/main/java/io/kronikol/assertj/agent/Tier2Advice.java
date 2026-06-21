package io.kronikol.assertj.agent;

import net.bytebuddy.asm.Advice;

/**
 * ByteBuddy advice inlined into each instrumented AssertJ comparison method. A single user assertion
 * can run through several instrumented frames (e.g. {@code AbstractStringAssert.isEqualTo} delegating
 * to {@code AbstractAssert.isEqualTo}); the enter advice marks the outermost frame so
 * {@link Tier2Recorder} records the assertion exactly once. Runs on exit including when an
 * {@link AssertionError} is thrown. Kept tiny because its body is copied into AssertJ's bytecode.
 */
public final class Tier2Advice {

    private Tier2Advice() {
    }

    @Advice.OnMethodEnter
    public static boolean onEnter() {
        return Tier2Recorder.enter();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void onExit(@Advice.Enter boolean outermost,
                              @Advice.This Object self,
                              @Advice.Origin("#m") String method,
                              @Advice.AllArguments Object[] args,
                              @Advice.Thrown Throwable thrown) {
        Tier2Recorder.exit(outermost, self, method, args, thrown);
    }
}
