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
    
    // ==========================================================================
    // AEP Features
    // ==========================================================================
    
    /** Advanced pattern matching with ML-based detection */
    AEP_ADVANCED_PATTERNS,
    
    /** Machine learning integration for pattern discovery */
    AEP_MACHINE_LEARNING,
    
    /** Real-time event processing with sub-millisecond latency */
    AEP_REAL_TIME_PROCESSING,
    
    /** Pattern learning from historical data */
    AEP_PATTERN_LEARNING,
    
    // ==========================================================================
    // Data-Cloud Features
    // ==========================================================================
    
    /** ML-powered data intelligence and insights */
    DATA_CLOUD_ML_INTELLIGENCE,
    
    /** Advanced analytics with Trino integration */
    DATA_CLOUD_ADVANCED_ANALYTICS,
    
    /** Real-time streaming with Kafka */
    DATA_CLOUD_REAL_TIME_STREAMING,
    
    /** Knowledge graph integration */
    DATA_CLOUD_KNOWLEDGE_GRAPH,
    
    // ==========================================================================
    // YAPPC Features
    // ==========================================================================
    
    /** Code scaffolding and generation */
    YAPPC_SCAFFOLDING,
    
    /** Code refactoring and analysis */
    YAPPC_REFACTORING,
    
    /** AI-powered requirements analysis */
    YAPPC_AI_REQUIREMENTS,
    
    /** Canvas-based visual programming */
    YAPPC_CANVAS,
    
    // ==========================================================================
    // Flashit Features
    // ==========================================================================
    
    /** AI-powered content classification */
    FLASHIT_AI_CLASSIFICATION,
    
    /** Voice transcription and processing */
    FLASHIT_VOICE_TRANSCRIPTION,
    
    /** Semantic search across content */
    FLASHIT_SEMANTIC_SEARCH,
    
    /** AI-powered reflection and insights */
    FLASHIT_AI_REFLECTION,
    
    // ==========================================================================
    // Software-Org Features
    // ==========================================================================
    
    /** Department simulation modules */
    SOFTWARE_ORG_DEPARTMENTS,
    
    /** Workflow automation */
    SOFTWARE_ORG_WORKFLOWS,
    
    /** Agent-based simulation */
    SOFTWARE_ORG_AGENTS,
    
    // ==========================================================================
    // Virtual-Org Features
    // ==========================================================================
    
    /** Virtual agent framework */
    VIRTUAL_ORG_AGENTS,
    
    /** Organization simulation */
    VIRTUAL_ORG_SIMULATION,
    
    /** Integration with external systems */
    VIRTUAL_ORG_INTEGRATION,
    
    // ==========================================================================
    // Security-Gateway Features
    // ==========================================================================
    
    /** OAuth 2.1 authentication */
    SECURITY_GATEWAY_OAUTH,
    
    /** Role-based access control */
    SECURITY_GATEWAY_RBAC,
    
    /** Attribute-based access control */
    SECURITY_GATEWAY_ABAC,
    
    /** Zero-trust security model */
    SECURITY_GATEWAY_ZERO_TRUST
}
