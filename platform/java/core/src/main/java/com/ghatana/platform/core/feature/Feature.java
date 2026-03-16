package com.ghatana.platform.core.feature;

/**
 * Enumeration of all features that can be enabled/disabled at runtime.
 * 
 * Features are organized by product/domain for clarity.
 * Use {@link FeatureService} to check if a feature is enabled.
 *
 * @doc.type enum
 * @doc.purpose Platform feature flags for capability toggling
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public enum Feature {
    
    // ==========================================================================
    // Platform Features
    // ==========================================================================
    
    /** Advanced observability with distributed tracing */
    PLATFORM_ADVANCED_OBSERVABILITY,
    
    /** Behavioral authentication and risk-based access */
    PLATFORM_BEHAVIORAL_AUTH,
    
    /** OAuth 2.1 authentication */
    SECURITY_GATEWAY_OAUTH,
    
    /** Role-based access control */
    SECURITY_GATEWAY_RBAC,
    
    /** Attribute-based access control */
    SECURITY_GATEWAY_ABAC,
    
    /** Zero-trust security model */
    SECURITY_GATEWAY_ZERO_TRUST,

    // ==========================================================================
    // Product Features
    // ==========================================================================

    /** AEP advanced event-processing patterns */
    AEP_ADVANCED_PATTERNS,

    /** AEP machine-learning pipelines */
    AEP_MACHINE_LEARNING,

    /** YAPPC code scaffolding */
    YAPPC_SCAFFOLDING,

    /** Data-Cloud knowledge graph */
    DATA_CLOUD_KNOWLEDGE_GRAPH
}
