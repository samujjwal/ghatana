package com.ghatana.digitalmarketing.domain.optimization;

/**
 * Status of an anomaly detection result.
 *
 * @doc.type enum
 * @doc.purpose Defines the lifecycle states of anomaly detection results (P3-004)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum AnomalyStatus {
    /** Anomaly detected and awaiting review */
    DETECTED,
    
    /** Anomaly acknowledged by human reviewer */
    ACKNOWLEDGED,
    
    /** Anomaly resolved with mitigation action */
    RESOLVED,
    
    /** Anomaly dismissed as false positive */
    DISMISSED
}
