package com.ghatana.datacloud.feature;

/**
 * Enumeration of Data-Cloud-specific features that can be enabled/disabled at runtime.
 *
 * <p>Each constant maps to an environment variable {@code DC_FEATURE_<NAME>}.
 * The constructor argument is the safe default when the variable is absent.
 *
 * @doc.type enum
 * @doc.purpose Data-Cloud product feature flags for capability toggling
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum DataCloudFeature {

    /** ML-powered data intelligence and insights (opt-in). */
    DATA_CLOUD_ML_INTELLIGENCE(false),

    /** Advanced analytics with Trino federation (on by default). */
    DATA_CLOUD_ADVANCED_ANALYTICS(true),

    /** Real-time streaming with Kafka (on by default). */
    DATA_CLOUD_REAL_TIME_STREAMING(true),

    /** Knowledge graph integration (opt-in). */
    DATA_CLOUD_KNOWLEDGE_GRAPH(false),

    /** AI-assisted entity, analytics, pipeline, and brain hints (opt-in; DC-E3). */
    DATA_CLOUD_AI_ASSIST(false),

    /** Voice gateway intent resolution and classification (opt-in; DC-E4). */
    DATA_CLOUD_VOICE_GATEWAY(false),

    /** Data lifecycle, retention, PII redaction, and compliance posture (on by default; DC-E5). */
    DATA_CLOUD_GOVERNANCE(true),

    /** Bulk CSV / NDJSON entity export (on by default). */
    DATA_CLOUD_EXPORT(true),

    /** Statistical anomaly detection (opt-in). */
    DATA_CLOUD_ANOMALY_DETECTION(false);

    private final boolean defaultEnabled;

    DataCloudFeature(boolean defaultEnabled) {
        this.defaultEnabled = defaultEnabled;
    }

    /**
     * Returns the safe default value used when the corresponding environment variable
     * ({@code DC_FEATURE_<NAME>}) is absent.
     *
     * @return {@code true} if the feature is on by default, {@code false} otherwise
     */
    public boolean defaultEnabled() {
        return defaultEnabled;
    }
}
