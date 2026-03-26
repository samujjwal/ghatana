/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.finance.ai.agents;

import java.util.Map;

/**
 * Result object for asynchronous operations
 *
 * @doc.type class
 * @doc.purpose Result object for asynchronous operations
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public class FraudDetectionResult {
    
    private final double fraudScore;
    private final String riskLevel;
    private final boolean isFraudulent;
    private final double confidence;
    private final double accuracy;
    private final Map<String, Object> features;
    
    public FraudDetectionResult(
            double fraudScore,
            String riskLevel,
            boolean isFraudulent,
            double confidence,
            double accuracy,
            Map<String, Object> features) {
        this.fraudScore = fraudScore;
        this.riskLevel = riskLevel;
        this.isFraudulent = isFraudulent;
        this.confidence = confidence;
        this.accuracy = accuracy;
        this.features = features;
    }
    
    public double getFraudScore() {
        return fraudScore;
    }
    
    public String getRiskLevel() {
        return riskLevel;
    }
    
    public boolean isFraudulent() {
        return isFraudulent;
    }
    
    public double getConfidence() {
        return confidence;
    }
    
    public double getAccuracy() {
        return accuracy;
    }
    
    public long getLatencyMs() {
        return 50; // Mock latency
    }
    
    public Map<String, Object> getFeatures() {
        return features;
    }
}
