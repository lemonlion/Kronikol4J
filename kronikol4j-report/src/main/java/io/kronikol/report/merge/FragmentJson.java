package io.kronikol.report.merge;

import io.kronikol.diagram.json.Json;

/**
 * Serializes a {@link ReportFragment} to / from the enriched JSON fragment (the {@code
 * TestRunReport.json} that runners emit and the CLI merges). Delegates the whole (deeply nested) model to
 * the generic {@link RecordJson} converter, so the full feature/scenario/step detail plus component
 * relationships, internal-flow segments, CI metadata and whole-test-flow content all round-trip. This is
 * a Java-internal transport format (round-trip), not a byte-match of the .NET fragment JSON.
 */
public final class FragmentJson {

    private FragmentJson() {
    }

    public static String toJson(ReportFragment fragment) {
        return Json.write(RecordJson.toTree(fragment));
    }

    public static ReportFragment fromJson(String json) {
        return RecordJson.fromTree(Json.parse(json), ReportFragment.class);
    }
}
