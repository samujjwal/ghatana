package com.ghatana.core.pattern.learning;

/**
 * Types of anomalies that can be detected.
 *
 * @doc.type enum
 * @doc.purpose Anomaly type classification
 * @doc.layer core
 * @doc.pattern Learning
 */
public enum AnomalyType {
    /**
     * Temporal anomalies - unusual timing patterns.
     */
    TEMPORAL("Temporal", "Unusual event timing or intervals"),
    
    /**
     * Frequency anomalies - unusual occurrence rates.
     */
    FREQUENCY("Frequency", "Unusual event frequency or rate"),
    
    /**
     * Feature anomalies - unusual feature values.
     */
    FEATURE("Feature", "Unusual feature values or combinations"),
    
    /**
     * Sequence anomalies - unusual event sequences.
     */
    SEQUENCE("Sequence", "Unusual event sequences or order"),
    
    /**
     * Distribution anomalies - unusual event distributions.
     */
    DISTRIBUTION("Distribution", "Unusual event type distributions"),
    
    /**
     * Behavioral anomalies - unusual behavior patterns.
     */
    BEHAVIORAL("Behavioral", "Unusual behavioral patterns"),
    
    /**
     * Statistical anomalies - statistical outliers.
     */
    STATISTICAL("Statistical", "Statistical outliers in event data"),
    
    /**
     * Contextual anomalies - context-specific anomalies.
     */
    CONTEXTUAL("Contextual", "Context-specific unusual patterns");

    private final String displayName;
    private final String description;

    AnomalyType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }

    @Override
    public String toString() {
        return displayName;
    }
}
