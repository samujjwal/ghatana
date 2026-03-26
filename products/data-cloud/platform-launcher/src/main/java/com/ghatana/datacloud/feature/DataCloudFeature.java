package com.ghatana.datacloud.feature;

/**
 * Enumeration of Data-Cloud-specific features that can be enabled/disabled at runtime.
 * 
 * These features are specific to the Data-Cloud product and should not be defined
 * in the platform core to maintain clean architecture boundaries.
 *
 * @doc.type enum
 * @doc.purpose Data-Cloud product feature flags for capability toggling
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum DataCloudFeature {
    
    /** ML-powered data intelligence and insights */
    DATA_CLOUD_ML_INTELLIGENCE,
    
    /** Advanced analytics with Trino integration */
    DATA_CLOUD_ADVANCED_ANALYTICS,
    
    /** Real-time streaming with Kafka */
    DATA_CLOUD_REAL_TIME_STREAMING,
    
    /** Knowledge graph integration */
    DATA_CLOUD_KNOWLEDGE_GRAPH
}
