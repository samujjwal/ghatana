package com.ghatana.validation.ai.anomaly;

import java.util.Map;

/**
 * Event features for anomaly detection.
 * 
 * @doc.type class
 * @doc.purpose Feature extraction result
 * @doc.layer product
 * @doc.pattern Value Object
 */
public class EventFeatures {
    
    private final String eventId;
    private final String eventType;
    private final Map<String, Double> numericFeatures;
    private final Map<String, String> categoricalFeatures;
    private final long timestamp;
    
    public EventFeatures(String eventId, String eventType, 
                        Map<String, Double> numericFeatures,
                        Map<String, String> categoricalFeatures,
                        long timestamp) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.numericFeatures = numericFeatures;
        this.categoricalFeatures = categoricalFeatures;
        this.timestamp = timestamp;
    }
    
    public String getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public Map<String, Double> getNumericFeatures() { return numericFeatures; }
    public Map<String, String> getCategoricalFeatures() { return categoricalFeatures; }
    public long getTimestamp() { return timestamp; }
    
    /**
     * Gets a numeric feature value.
     */
    public double getNumericFeature(String name) {
        return numericFeatures.getOrDefault(name, 0.0);
    }
    
    /**
     * Gets a categorical feature value.
     */
    public String getCategoricalFeature(String name) {
        return categoricalFeatures.getOrDefault(name, "");
    }
    
    /**
     * Gets the feature dimension.
     */
    public int getDimension() {
        return numericFeatures.size() + categoricalFeatures.size();
    }
}
