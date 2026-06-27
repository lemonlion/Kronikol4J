package io.kronikol.core.tracking;

import io.kronikol.core.context.TestPhaseContext;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Injects custom PlantUML fragments into a test's sequence diagram and marks the Setup→Action phase
 * boundary. Java port of the .NET {@code DefaultTrackingDiagramOverride}. Each method emits a marker
 * {@link RequestResponseLog} (carrying {@code overrideStart}/{@code overrideEnd}/{@code actionStart} +
 * {@code plantUml}) through {@link RequestResponseLogger}; the diagram renderer (already byte-complete)
 * inserts the buffered fragment between override markers and renders the action-start boundary.
 */
public final class TrackingDiagramOverride {

    private static final URI OVERRIDE_URI = URI.create("http://override.com");

    private TrackingDiagramOverride() {
    }

    /** Opens an override block for {@code testId}, optionally emitting a PlantUML fragment. */
    public static void startOverride(String testId, String plantUml) {
        RequestResponseLogger.log(marker(testId)
            .overrideStart(true).plantUml(toBufferedPlantUml(plantUml)));
    }

    public static void startOverride(String testId) {
        startOverride(testId, null);
    }

    /** Closes an override block for {@code testId}, optionally emitting a trailing PlantUML fragment. */
    public static void endOverride(String testId, String plantUml) {
        RequestResponseLogger.log(marker(testId)
            .overrideEnd(true).plantUml(toBufferedPlantUml(plantUml)));
    }

    public static void endOverride(String testId) {
        endOverride(testId, null);
    }

    /** Inserts a self-contained PlantUML fragment (a start+end override pair) into {@code testId}'s diagram. */
    public static void insertPlantUml(String testId, String plantUml) {
        startOverride(testId, plantUml);
        endOverride(testId);
    }

    /** Inserts a full-width black header note delimiting a test in a merged/whole-run diagram. */
    public static void insertTestDelimiter(String testRuntimeId, String testIdentifier) {
        startOverride(testRuntimeId, "hnote across #black:<color:white>Test " + testIdentifier);
        endOverride(testRuntimeId);
    }

    /** Marks the Setup→Action boundary: sets the ambient phase to Action and emits an action-start marker. */
    public static void startAction(String testId) {
        TestPhaseContext.set(TestPhase.ACTION);
        RequestResponseLogger.log(marker(testId).actionStart(true));
    }

    /** Sets the ambient phase back to Setup (no diagram marker, matching .NET). */
    public static void startSetup(String testId) {
        TestPhaseContext.set(TestPhase.SETUP);
    }

    // --- Supplier<String> overloads for framework adapters that resolve the test id lazily ---

    public static void startOverride(Supplier<String> getTestId, String plantUml) {
        startOverride(getTestId.get(), plantUml);
    }

    public static void endOverride(Supplier<String> getTestId, String plantUml) {
        endOverride(getTestId.get(), plantUml);
    }

    public static void insertPlantUml(Supplier<String> getTestId, String plantUml) {
        insertPlantUml(getTestId.get(), plantUml);
    }

    public static void insertTestDelimiter(Supplier<String> getTestId, String testIdentifier) {
        insertTestDelimiter(getTestId.get(), testIdentifier);
    }

    public static void startAction(Supplier<String> getTestId) {
        startAction(getTestId.get());
    }

    public static void startSetup(Supplier<String> getTestId) {
        startSetup(getTestId.get());
    }

    /** A bare marker log for {@code testId} (empty method/content/service, the override URI, random ids). */
    private static RequestResponseLog marker(String testId) {
        return RequestResponseLog.builder()
            .testName(testId).testId(testId)
            .method(Method.of("")).content("")
            .uri(OVERRIDE_URI).headers(List.of())
            .serviceName("").callerName("")
            .type(RequestResponseType.REQUEST)
            .traceId(UUID.randomUUID()).requestResponseId(UUID.randomUUID())
            .trackingIgnore(false).build();
    }

    /** Buffers a fragment with surrounding blank lines (.NET raw-string form: {@code "\n" + p + "\n\n"}). */
    private static String toBufferedPlantUml(String plantUml) {
        return plantUml == null ? null : "\n" + plantUml + "\n\n";
    }
}
