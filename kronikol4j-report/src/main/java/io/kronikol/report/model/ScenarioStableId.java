package io.kronikol.report.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Computes a deterministic stable id for a scenario — consistent across runs and runtimes, unlike the
 * framework-assigned runtime id. Ports the .NET {@code ScenarioStableId.Compute} exactly:
 * {@code SHA-256("feature::scenario")} (or {@code "feature::outlineId::scenario"}), hex-encoded, first
 * 16 chars, lower-cased.
 */
public final class ScenarioStableId {

    private ScenarioStableId() {
    }

    public static String compute(String featureName, String scenarioDisplayName, String outlineId) {
        String input = outlineId != null
            ? featureName + "::" + outlineId + "::" + scenarioDisplayName
            : featureName + "::" + scenarioDisplayName;
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
