package com.ghatana.validation.ai.anomaly;

/**
 * Model performance metrics.
 * 
 * @doc.type class
 * @doc.purpose Model performance tracking
 * @doc.layer product
 * @doc.pattern Value Object
 */
public class ModelPerformance {
    
    private final String modelId;
    private final double accuracy;
    private final double precision;
    private final double recall;
    private final double f1Score;
    private final long totalPredictions;
    private final long correctPredictions;
    private final long falsePositives;
    private final long falseNegatives;
    private final double averageLatencyMs;
    private final long timestamp;
    
    public ModelPerformance(String modelId, double accuracy, double precision,
                           double recall, double f1Score, long totalPredictions,
                           long correctPredictions, long falsePositives,
                           long falseNegatives, double averageLatencyMs) {
        this.modelId = modelId;
        this.accuracy = accuracy;
        this.precision = precision;
        this.recall = recall;
        this.f1Score = f1Score;
        this.totalPredictions = totalPredictions;
        this.correctPredictions = correctPredictions;
        this.falsePositives = falsePositives;
        this.falseNegatives = falseNegatives;
        this.averageLatencyMs = averageLatencyMs;
        this.timestamp = System.currentTimeMillis();
    }
    
    public String getModelId() { return modelId; }
    public double getAccuracy() { return accuracy; }
    public double getPrecision() { return precision; }
    public double getRecall() { return recall; }
    public double getF1Score() { return f1Score; }
    public long getTotalPredictions() { return totalPredictions; }
    public long getCorrectPredictions() { return correctPredictions; }
    public long getFalsePositives() { return falsePositives; }
    public long getFalseNegatives() { return falseNegatives; }
    public double getAverageLatencyMs() { return averageLatencyMs; }
    public long getTimestamp() { return timestamp; }
    
    /**
     * Creates a default performance with zero metrics.
     */
    public static ModelPerformance empty(String modelId) {
        return new ModelPerformance(modelId, 0.0, 0.0, 0.0, 0.0, 0, 0, 0, 0, 0.0);
    }
}
