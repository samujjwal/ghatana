package com.ghatana.validation.ai.anomaly;

import java.util.Map;

/**
 * Anomaly detection result.
 * 
 * @doc.type class
 * @doc.purpose Anomaly detection output
 * @doc.layer product
 * @doc.pattern Value Object
 */
public class AnomalyResult {
    
    private final String eventId;
    private final boolean isAnomaly;
    private final double anomalyScore;
    private final double confidence;
    private final String anomalyType;
    private final Map<String, Object> details;
    private final long detectionTime;
    
    public AnomalyResult(String eventId, boolean isAnomaly, double anomalyScore,
                        double confidence, String anomalyType, Map<String, Object> details) {
        this.eventId = eventId;
        this.isAnomaly = isAnomaly;
        this.anomalyScore = anomalyScore;
        this.confidence = confidence;
        this.anomalyType = anomalyType;
        this.details = details;
        this.detectionTime = System.currentTimeMillis();
    }
    
    public String getEventId() { return eventId; }
    public boolean isAnomaly() { return isAnomaly; }
    public double getAnomalyScore() { return anomalyScore; }
    public double getConfidence() { return confidence; }
    public String getAnomalyType() { return anomalyType; }
    public Map<String, Object> getDetails() { return details; }
    public long getDetectionTime() { return detectionTime; }
    
    /**
     * Creates a non-anomaly result.
     */
    public static AnomalyResult normal(String eventId) {
        return new AnomalyResult(eventId, false, 0.0, 1.0, "NORMAL", Map.of());
    }
    
    /**
     * Creates an anomaly result.
     */
    public static AnomalyResult anomaly(String eventId, double score, String type, Map<String, Object> details) {
        return new AnomalyResult(eventId, true, score, score, type, details);
    }
}
