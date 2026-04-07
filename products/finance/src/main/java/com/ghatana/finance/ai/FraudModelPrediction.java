package com.ghatana.finance.ai;

import java.util.Objects;

/**
 * Fraud model inference output.
 *
 * @doc.type class
 * @doc.purpose Value object describing fraud model prediction output
 * @doc.layer product
 * @doc.pattern Value Object
 */
public final class FraudModelPrediction {

    private final double fraudScore;
    private final String riskLevel;
    private final boolean fraudulent;
    private final double confidence;
    private final double accuracy;
    private final String fraudType;
    private final String inferenceSource;
    private final String modelVersion;
    private final long latencyMs;

    public FraudModelPrediction(double fraudScore,
                                String riskLevel,
                                boolean fraudulent,
                                double confidence,
                                double accuracy,
                                String fraudType,
                                String inferenceSource,
                                String modelVersion,
                                long latencyMs) {
        this.fraudScore = fraudScore;
        this.riskLevel = Objects.requireNonNull(riskLevel, "riskLevel cannot be null");
        this.fraudulent = fraudulent;
        this.confidence = confidence;
        this.accuracy = accuracy;
        this.fraudType = fraudType;
        this.inferenceSource = Objects.requireNonNull(inferenceSource, "inferenceSource cannot be null");
        this.modelVersion = modelVersion == null || modelVersion.isBlank() ? null : modelVersion;
        if (latencyMs < 0L) {
            throw new IllegalArgumentException("latencyMs must not be negative");
        }
        this.latencyMs = latencyMs;
    }

    public double getFraudScore() {
        return fraudScore;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public boolean isFraudulent() {
        return fraudulent;
    }

    public double getConfidence() {
        return confidence;
    }

    public double getAccuracy() {
        return accuracy;
    }

    public String getFraudType() {
        return fraudType;
    }

    public String getInferenceSource() {
        return inferenceSource;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public long getLatencyMs() {
        return latencyMs;
    }
}