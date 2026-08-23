package io.kronikol.report.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Computes a deterministic stable id for a scenario — consistent across runs and runtimes, unlike the
 * framework-assigned runtime id. Ports the .NET {@code ScenarioStableId.Compute} exactly:
 * {@code SHA-256("feature::scenario")} (or {@code "feature::outlineId::scenario"}), with the ordered
 * example values appended when the scenario has any, hex-encoded, first 16 chars, lower-cased.
 */
public final class ScenarioStableId {

    private ScenarioStableId() {
    }

    public static String compute(String featureName, String scenarioDisplayName, String outlineId) {
        return compute(featureName, scenarioDisplayName, outlineId, null);
    }

    /**
     * The full form. For a scenario outline the display name is often shared by every example row, so the
     * ordered example values go into the hash too — without them all rows of an outline collapse onto one
     * id and cross-run matching cannot tell row 1 from row 3, which is exactly the case where per-row
     * matching matters.
     */
    public static String compute(String featureName, String scenarioDisplayName, String outlineId,
                                 Map<String, String> exampleValues) {
        String input = outlineId != null
            ? featureName + "::" + outlineId + "::" + scenarioDisplayName
            : featureName + "::" + scenarioDisplayName;

        if (exampleValues != null && !exampleValues.isEmpty()) {
            List<String> keys = new ArrayList<>(exampleValues.keySet());
            keys.sort(null);
            StringBuilder ordered = new StringBuilder();
            for (String key : keys) {
                if (ordered.length() > 0) {
                    ordered.append('|');
                }
                ordered.append(key).append('=').append(exampleValues.get(key));
            }
            input = input + "::" + ordered;
        }

        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.substring(0, 16).toLowerCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required but unavailable", e);
        }
    }
}
