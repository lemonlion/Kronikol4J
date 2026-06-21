package io.kronikol.report.model;

/** The verification outcome of a step parameter value (mirrors the .NET {@code VerificationStatus}). */
public enum VerificationStatus {
    NOT_APPLICABLE,
    SUCCESS,
    FAILURE,
    EXCEPTION,
    NOT_PROVIDED
}
