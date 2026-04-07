package com.ghatana.finance.ai;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @doc.type class
 * @doc.purpose Verifies fraud model inference response validation and normalization
 * @doc.layer product
 * @doc.pattern Test
 */
class FraudModelInferenceResponseTest {

    @Test
    void parsesAliasesAndDerivesBooleans() {
        FraudModelInferenceResponse response = FraudModelInferenceResponse.fromPayload(Map.of(
            "fraud_score", "0.81",
            "risk_level", "high",
            "confidence", 0.95,
            "accuracy", 0.96,
            "fraud_type", "remote-anomaly",
            "model_version", "2026.04",
            "latency_ms", 34
        ));

        assertEquals(0.81, response.getFraudScore());
        assertEquals("HIGH", response.getRiskLevel());
        assertTrue(response.isFraudulent());
        FraudModelPrediction prediction = response.toPrediction("fallback-version", 10L);
        assertEquals("remote-anomaly", prediction.getFraudType());
        assertEquals("REMOTE", prediction.getInferenceSource());
        assertEquals("2026.04", prediction.getModelVersion());
        assertEquals(34L, prediction.getLatencyMs());
    }

    @Test
    void infersLowRiskWhenOptionalFieldsMissing() {
        FraudModelInferenceResponse response = FraudModelInferenceResponse.fromPayload(Map.of(
            "score", 0.12
        ));

        assertEquals("LOW", response.getRiskLevel());
        assertFalse(response.isFraudulent());
        assertEquals("remote-default", response.toPrediction("remote-default", 9L).getModelVersion());
        assertEquals(9L, response.toPrediction("remote-default", 9L).getLatencyMs());
    }

    @Test
    void readsVersionAndLatencyFromNestedMetadata() {
        FraudModelInferenceResponse response = FraudModelInferenceResponse.fromPayload(Map.of(
            "fraudScore", 0.52,
            "metadata", Map.of(
                "model_version", "2026.05",
                "latency_ms", "41"
            )
        ));

        FraudModelPrediction prediction = response.toPrediction("fallback-version", 5L);

        assertEquals("2026.05", prediction.getModelVersion());
        assertEquals(41L, prediction.getLatencyMs());
    }

    @Test
    void rejectsMissingFraudScore() {
        assertThrows(IllegalArgumentException.class, () -> FraudModelInferenceResponse.fromPayload(Map.of(
            "riskLevel", "LOW"
        )));
    }

    @Test
    void rejectsOutOfRangeFraudScore() {
        assertThrows(IllegalArgumentException.class, () -> FraudModelInferenceResponse.fromPayload(Map.of(
            "fraudScore", 1.2
        )));
    }
}