package com.ghatana.tts.core.api;

/**
 * Data-retention and telemetry privacy level for user profiles.
 *
 * @doc.type enum
 * @doc.purpose Controls how much TTS usage data is retained for adaptation
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum PrivacyLevel {
    /** No telemetry or usage data is retained. */
    HIGH,
    /** Aggregated, anonymised usage data is retained. */
    MEDIUM,
    /** Full session data is retained for adaptation. */
    LOW
}
