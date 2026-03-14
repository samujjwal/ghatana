package com.ghatana.aep.feature;

/**
 * Enumeration of AEP-specific features that can be enabled/disabled at runtime.
 * 
 * These features are specific to the AEP product and should not be defined
 * in the platform core to maintain clean architecture boundaries.
 *
 * @doc.type enum
 * @doc.purpose AEP product feature flags for capability toggling
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum AEPFeature {
    
    /** Advanced pattern matching with ML-based detection */
    AEP_ADVANCED_PATTERNS,
    
    /** Machine learning integration for pattern discovery */
    AEP_MACHINE_LEARNING,
    
    /** Real-time event processing with sub-millisecond latency */
    AEP_REAL_TIME_PROCESSING,
    
    /** Pattern learning from historical data */
    AEP_PATTERN_LEARNING
}
