package io.kronikol.report.model;

/** An inline step-parameter value with an optional expectation and verification status
 *  (mirrors the .NET {@code InlineParameterValue}). */
public record InlineParameterValue(String value, String expectation, VerificationStatus status) {
}
