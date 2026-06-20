package io.kronikol.report.merge;

import io.kronikol.diagram.json.Json;
import io.kronikol.report.merge.ReportFragment.FeatureFragment;
import io.kronikol.report.merge.ReportFragment.ScenarioFragment;
import io.kronikol.report.model.ExecutionStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serializes a {@link ReportFragment} to / from the enriched JSON fragment (the {@code
 * TestRunReport.json} that runners emit and the CLI merges). Uses the canonical {@link Json} writer
 * (null object-properties are stripped, so absent {@code error}/{@code diagram} round-trip as null).
 */
public final class FragmentJson {

    private FragmentJson() {
    }

    public static String toJson(ReportFragment fragment) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("title", fragment.title());
        root.put("startTime", fragment.startTime());
        root.put("endTime", fragment.endTime());
        List<Object> features = new ArrayList<>();
        for (FeatureFragment feature : fragment.features()) {
            Map<String, Object> fm = new LinkedHashMap<>();
            fm.put("displayName", feature.displayName());
            List<Object> scenarios = new ArrayList<>();
            for (ScenarioFragment s : feature.scenarios()) {
                Map<String, Object> sm = new LinkedHashMap<>();
                sm.put("name", s.name());
                sm.put("testId", s.testId());
                sm.put("status", s.status().name());
                sm.put("durationMs", Json.number(s.durationMs()));
                sm.put("error", s.error());
                sm.put("diagram", s.diagram());
                scenarios.add(sm);
            }
            fm.put("scenarios", scenarios);
            features.add(fm);
        }
        root.put("features", features);
        return Json.write(root);
    }

    public static ReportFragment fromJson(String json) {
        Map<?, ?> root = (Map<?, ?>) Json.parse(json);
        List<FeatureFragment> features = new ArrayList<>();
        for (Object fo : (List<?>) root.get("features")) {
            Map<?, ?> fm = (Map<?, ?>) fo;
            List<ScenarioFragment> scenarios = new ArrayList<>();
            for (Object so : (List<?>) fm.get("scenarios")) {
                Map<?, ?> sm = (Map<?, ?>) so;
                scenarios.add(new ScenarioFragment(
                    str(sm.get("name")),
                    str(sm.get("testId")),
                    ExecutionStatus.valueOf(str(sm.get("status"))),
                    longOf(sm.get("durationMs")),
                    str(sm.get("error")),
                    str(sm.get("diagram"))));
            }
            features.add(new FeatureFragment(str(fm.get("displayName")), scenarios));
        }
        return new ReportFragment(str(root.get("title")), str(root.get("startTime")),
            str(root.get("endTime")), features);
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static long longOf(Object o) {
        return o instanceof Json.RawNumber rn ? Long.parseLong(rn.literal()) : 0L;
    }
}
