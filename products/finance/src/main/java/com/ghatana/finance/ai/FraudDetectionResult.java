package com.ghatana.finance.ai;

import java.util.Map;
import java.util.Objects;

/**
 * Fraud detection result.
 *
 * @doc.type class
 * @doc.purpose Data transfer object for fraud detection results
 * @doc.layer product
 * @doc.pattern Data Transfer Object
 * @doc.description Represents the result of a fraud detection analysis.
 * @doc.usage This class is used to transfer the results of a fraud detection analysis between different components of the system.
 */
public class FraudDetectionResult {
    private final String tradeId;
    private final String accountId;
    private final boolean suspicious;
    private final String fraudType;
    private final double confidence;
    private final boolean skipped;
    private final double fraudScore;
    private final String riskLevel;
    private final boolean fraudulent;
    private final double accuracy;
    private final Map<String, Object> features;
    private final long latencyMs;
    private final String inferenceSource;
    private final String modelVersion;
    private final long inferenceLatencyMs;

    private FraudDetectionResult(String tradeId, String accountId, boolean suspicious,
                                 String fraudType, double confidence, boolean skipped,
                                 double fraudScore, String riskLevel, boolean fraudulent,
                                 double accuracy, Map<String, Object> features, long latencyMs,
                                 String inferenceSource, String modelVersion, long inferenceLatencyMs) {
        this.tradeId = tradeId;
        this.accountId = accountId;
        this.suspicious = suspicious;
        this.fraudType = fraudType;
        this.confidence = confidence;
        this.skipped = skipped;
        this.fraudScore = fraudScore;
        this.riskLevel = riskLevel;
        this.fraudulent = fraudulent;
        this.accuracy = accuracy;
        this.features = features;
        this.latencyMs = latencyMs;
        this.inferenceSource = inferenceSource;
        this.modelVersion = modelVersion;
        this.inferenceLatencyMs = inferenceLatencyMs;
    }

    public static FraudDetectionResult skip() {
        return new FraudDetectionResult(
            null,
            null,
            false,
            null,
            0.0,
            true,
            0.0,
            "SKIPPED",
            false,
            0.0,
            Map.of(),
            0L,
            "SKIPPED",
            null,
            0L
        );
    }

    public static FraudDetectionResult clean(String tradeId, String accountId) {
        return new FraudDetectionResult(
            tradeId,
            accountId,
            false,
            null,
            1.0,
            false,
            0.0,
            "LOW",
            false,
            1.0,
            Map.of(),
            0L,
            "LOCAL_RULES",
            null,
            0L
        );
    }

    public static FraudDetectionResult suspicious(String tradeId, String accountId,
                                                  String fraudType, double confidence) {
        return new FraudDetectionResult(
            tradeId,
            accountId,
            true,
            fraudType,
            confidence,
            false,
            confidence,
            confidence >= 0.8 ? "HIGH" : "MEDIUM",
            confidence >= 0.8,
            confidence,
            Map.of(),
            0L,
            "LOCAL_RULES",
            null,
            0L
        );
    }

    public static FraudDetectionResult scored(String tradeId,
                                              String accountId,
                                              String fraudType,
                                              double fraudScore,
                                              String riskLevel,
                                              boolean fraudulent,
                                              double confidence,
                                              double accuracy,
                                              Map<String, Object> features,
                                              long latencyMs,
                                              String inferenceSource,
                                              String modelVersion,
                                              long inferenceLatencyMs) {
        Objects.requireNonNull(riskLevel, "riskLevel cannot be null");
        Objects.requireNonNull(features, "features cannot be null");
        Objects.requireNonNull(inferenceSource, "inferenceSource cannot be null");

        return new FraudDetectionResult(
            tradeId,
            accountId,
            fraudulent,
            fraudType,
            confidence,
            false,
            fraudScore,
            riskLevel,
            fraudulent,
            accuracy,
            Map.copyOf(features),
            latencyMs,
            inferenceSource,
            modelVersion,
            inferenceLatencyMs
        );
    }

    public String getTradeId() { return tradeId; }
    public String getAccountId() { return accountId; }
    public boolean isSuspicious() { return suspicious; }
    public String getFraudType() { return fraudType; }
    public double getConfidence() { return confidence; }
    public boolean isSkipped() { return skipped; }
    public double getFraudScore() { return fraudScore; }
    public String getRiskLevel() { return riskLevel; }
    public boolean isFraudulent() { return fraudulent; }
    public double getAccuracy() { return accuracy; }
    public Map<String, Object> getFeatures() { return features; }
    public long getLatencyMs() { return latencyMs; }
    public String getInferenceSource() { return inferenceSource; }
    public String getModelVersion() { return modelVersion; }
    public long getInferenceLatencyMs() { return inferenceLatencyMs; }
}
