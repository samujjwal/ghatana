package com.ghatana.platform.plugin;

/**
 * Represents the type of a plugin in the Data-Cloud ecosystem.
 *
 * @doc.type enum
 * @doc.purpose Categorize plugins for discovery and management
 * @doc.layer core
 */
public enum PluginType {
    /**
     * Storage plugins (Hot, Warm, Cool, Cold tiers).
     */
    STORAGE,

    /**
     * Processing plugins (Streaming, Transformation, Analytics).
     */
    PROCESSING,

    /**
     * Streaming plugins (Kafka, Pulsar, EventCloud).
     */
    STREAMING,

    /**
     * AI Model plugins (LLM, Embeddings).
     */
    AI,

    /**
     * Governance plugins (Policy, Schema, Validation).
     */
    GOVERNANCE,

    /**
     * Integration plugins (Connectors, Webhooks).
     */
    INTEGRATION,

    /**
     * Observability plugins (Metrics, Tracing, Alerting).
     */
    OBSERVABILITY,

    /**
     * Enterprise plugins (Lineage, DR, Federation).
     */
    ENTERPRISE,

    /**
     * Routing plugins (Event routing and transformation).
     */
    ROUTING,

    /**
     * Archive plugins (Long-term archive storage).
     */
    ARCHIVE,

    /**
     * Analytics plugins (Time-series, Aggregation, OLAP).
     */
    ANALYTICS,

    /**
     * Schema plugins (Schema registry, validation, evolution).
     */
    SCHEMA,

    /**
     * Authentication / Authorization plugins.
     */
    AUTH,

    /**
     * Custom / uncategorized plugins.
     */
    CUSTOM
}
