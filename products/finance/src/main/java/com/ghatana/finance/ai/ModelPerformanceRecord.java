/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.finance.ai;

import java.time.Instant;

/**
 * Component for entity
 *
 * @doc.type record
 * @doc.purpose Component for entity
 * @doc.layer product
 * @doc.pattern Service
 */
public class ModelPerformanceRecord {
    
    private String modelId;
    private double confidence;
    private double accuracy;
    private long latency;
    private Instant timestamp;
    
    public String getModelId() {
        return modelId;
    }
    
    public void setModelId(String modelId) {
        this.modelId = modelId;
    }
    
    public double getConfidence() {
        return confidence;
    }
    
    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }
    
    public double getAccuracy() {
        return accuracy;
    }
    
    public void setAccuracy(double accuracy) {
        this.accuracy = accuracy;
    }
    
    public long getLatency() {
        return latency;
    }
    
    public void setLatency(long latency) {
        this.latency = latency;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
