package com.example.componenttests;

import io.kronikol.junit5.KronikolExtension;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Base class for component tests. {@link KronikolExtension} scopes a Kronikol4J test identity for the
 * duration of each test (and clears it afterwards), so every dependency call the trackers observe on the
 * test thread is attributed to the right test — and records each test's pass/fail outcome for the report.
 *
 * <p>The report itself is finalized once per JVM by the auto-registered {@code KronikolReportListener}
 * (no setup needed); by default it is written to {@code build/kronikol-report/TestRunReport.html}.
 */
@ExtendWith(KronikolExtension.class)
public abstract class BaseComponentTest {
}
