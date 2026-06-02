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
    DATA_CLOUD_ANOMALY_DETECTION(false),

    /**
     * Data Fabric live metrics surface (opt-in, preview only).
     *
     * <p>DC-P1-002: Disabled by default in all profiles. Enable only when a real
     * {@link com.ghatana.datacloud.fabric.DataFabricConnector} implementation is
     * wired and live fabric metrics are available. When disabled, the /fabric route
     * returns a preview-gated response with {@code capability=unavailable}.
     */
    DATA_CLOUD_DATA_FABRIC(false),

    /**
     * Connector lifecycle management: create, update, delete, test, enable/disable,
     * credential rotation, and sync operations (on by default; DC-P2-006).
     *
     * <p>When disabled, all {@code /api/v1/connectors} and {@code /data-fabric/connectors}
     * routes return {@code 503 Service Unavailable}.
     */
    DATA_CLOUD_CONNECTORS(true),

    /**
     * Release-readiness route surface (off by default).
     *
     * <p>Non-default to keep release-readiness/evidence workflows inert unless
     * explicitly enabled in controlled environments.
     */
    DATA_CLOUD_RELEASE_READINESS(false),

    /**
     * Legacy Action Plane route aliases at root level (off by default; P1-01).
     *
     * <p>When disabled, only canonical {@code /api/v1/action/*} routes are registered.
     * Legacy routes at {@code /api/v1/pipelines}, {@code /api/v1/executions},
     * {@code /api/v1/learning}, and {@code /api/v1/memory} return {@code 404 Not Found}.
     * This encourages migration to the canonical Action Plane namespace.
     *
     * <p>Enable temporarily for backward compatibility during migration:
     * {@code DC_FEATURE_LEGACY_ACTION_ROUTES=true}
     */
    LEGACY_ACTION_ROUTES(false);

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
