package com.ghatana.finance.ai;

import java.util.Map;
import java.util.Objects;

/**
 * Fraud model inference response payload.
 *
 * @doc.type class
 * @doc.purpose Validates and normalizes inbound finance fraud model inference responses
 * @doc.layer product
 * @doc.pattern DTO
 */
public final class FraudModelInferenceResponse {

    private final double fraudScore;
    private final String riskLevel;
    private final boolean fraudulent;
    private final double confidence;
    private final double accuracy;
    private final String fraudType;
    private final String modelVersion;
    private final long latencyMs;

    public FraudModelInferenceResponse(double fraudScore,
                                       String riskLevel,
                                       boolean fraudulent,
                                       double confidence,
                                       double accuracy,
                                       String fraudType,
                                       String modelVersion,
                                       long latencyMs) {
        if (Double.isNaN(fraudScore) || fraudScore < 0.0 || fraudScore > 1.0) {
            throw new IllegalArgumentException("fraudScore must be between 0.0 and 1.0");
        }
        if (latencyMs < 0L) {
            throw new IllegalArgumentException("latencyMs must not be negative");
        }
        this.fraudScore = fraudScore;
        this.riskLevel = normalizeRiskLevel(riskLevel, fraudScore);
        this.fraudulent = fraudulent;
        this.confidence = confidence;
        this.accuracy = accuracy;
        this.fraudType = fraudType == null || fraudType.isBlank() ? null : fraudType;
        this.modelVersion = modelVersion == null || modelVersion.isBlank() ? null : modelVersion;
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

    public String getModelVersion() {
        return modelVersion;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public FraudModelPrediction toPrediction(String defaultModelVersion, long measuredLatencyMs) {
        String resolvedModelVersion = modelVersion != null ? modelVersion : defaultModelVersion;
        long resolvedLatencyMs = latencyMs > 0L ? latencyMs : Math.max(0L, measuredLatencyMs);
        return new FraudModelPrediction(
            fraudScore,
            riskLevel,
            fraudulent,
            confidence,
            accuracy,
            fraudType,
            "REMOTE",
            resolvedModelVersion,
            resolvedLatencyMs
        );
    }

    public static FraudModelInferenceResponse fromPayload(Map<String, Object> payload) {
        Objects.requireNonNull(payload, "payload cannot be null");

        double fraudScore = requireDouble(first(payload, "fraudScore", "fraud_score", "score"), "fraudScore");
        String riskLevel = payloadValueAsString(first(payload, "riskLevel", "risk_level"));
        Boolean fraudulent = asBoolean(first(payload, "fraudulent", "isFraudulent", "is_fraudulent"));
        double confidence = optionalDouble(payload.get("confidence"), 0.9);
        double accuracy = optionalDouble(payload.get("accuracy"), 0.92);
        String fraudType = payloadValueAsString(first(payload, "fraudType", "fraud_type"));
        Map<String, Object> metadata = nestedMap(payload.get("metadata"));
        String modelVersion = payloadValueAsString(first(payload, "modelVersion", "model_version", "version"));
        if (modelVersion == null) {
            modelVersion = payloadValueAsString(first(metadata, "modelVersion", "model_version", "version"));
        }
        long latencyMs = optionalLong(
            first(payload, "latencyMs", "latency_ms", "modelLatencyMs", "model_latency_ms"),
            optionalLong(first(metadata, "latencyMs", "latency_ms", "modelLatencyMs", "model_latency_ms"), 0L)
        );

        return new FraudModelInferenceResponse(
            fraudScore,
            riskLevel,
            fraudulent != null ? fraudulent : fraudScore >= 0.75,
            confidence,
            accuracy,
            fraudType,
            modelVersion,
            latencyMs
        );
    }

    private static Object first(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            if (payload.containsKey(key)) {
                return payload.get(key);
            }
        }
        return null;
    }

    private static double requireDouble(Object value, String fieldName) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Double.parseDouble(stringValue);
            } catch (NumberFormatException ignored) {
                throw new IllegalArgumentException(fieldName + " must be numeric");
            }
        }
        throw new IllegalArgumentException(fieldName + " is required");
    }

    private static double optionalDouble(Object value, double fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Double.parseDouble(stringValue);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static long optionalLong(Object value, long fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return Math.max(number.longValue(), 0L);
        }
        if (value instanceof String stringValue) {
            try {
                return Math.max(Long.parseLong(stringValue), 0L);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static Boolean asBoolean(Object value) {
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        if (value instanceof String stringValue) {
            if ("true".equalsIgnoreCase(stringValue)) {
                return true;
            }
            if ("false".equalsIgnoreCase(stringValue)) {
                return false;
            }
        }
        return null;
    }

    private static String payloadValueAsString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nestedMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static String normalizeRiskLevel(String riskLevel, double fraudScore) {
        if (riskLevel == null || riskLevel.isBlank()) {
            return fraudScore >= 0.75 ? "HIGH" : fraudScore >= 0.45 ? "MEDIUM" : "LOW";
        }

        String normalized = riskLevel.trim().toUpperCase();
        return switch (normalized) {
            case "HIGH", "MEDIUM", "LOW" -> normalized;
            default -> throw new IllegalArgumentException("riskLevel must be HIGH, MEDIUM, or LOW");
        };
    }
}
