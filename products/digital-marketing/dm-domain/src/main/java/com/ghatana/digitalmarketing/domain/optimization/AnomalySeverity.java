package com.ghatana.digitalmarketing.domain.optimization;

/**
 * Severity level of a detected anomaly.
 *
 * @doc.type enum
 * @doc.purpose Defines the severity levels for anomaly detection results (P3-004)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum AnomalySeverity {
    /** Low severity: minor deviation, informational only */
    LOW,
    
    /** Medium severity: notable deviation requiring attention */
    MEDIUM,
    
    /** High severity: significant deviation requiring immediate action */
    HIGH,
    
    /** Critical severity: extreme deviation indicating serious issue */
    CRITICAL
}
