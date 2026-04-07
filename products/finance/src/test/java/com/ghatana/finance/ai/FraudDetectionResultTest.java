package com.ghatana.finance.ai;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @doc.type class
 * @doc.purpose Verifies unified fraud detection result factories and accessors
 * @doc.layer product
 * @doc.pattern Test
 */
class FraudDetectionResultTest {

    @Test
    void createsSkipResult() {
        FraudDetectionResult result = FraudDetectionResult.skip();

        assertTrue(result.isSkipped());
        assertFalse(result.isFraudulent());
        assertEquals("SKIPPED", result.getRiskLevel());
        assertEquals(0.0, result.getFraudScore());
        assertEquals(Map.of(), result.getFeatures());
        assertEquals("SKIPPED", result.getInferenceSource());
    }

    @Test
    void createsCleanResult() {
        FraudDetectionResult result = FraudDetectionResult.clean("trade-1", "account-1");

        assertEquals("trade-1", result.getTradeId());
        assertEquals("account-1", result.getAccountId());
        assertFalse(result.isSuspicious());
        assertFalse(result.isFraudulent());
        assertEquals("LOW", result.getRiskLevel());
        assertEquals(1.0, result.getAccuracy());
        assertEquals("LOCAL_RULES", result.getInferenceSource());
    }

    @Test
    void createsSuspiciousResult() {
        FraudDetectionResult result = FraudDetectionResult.suspicious(
            "trade-2",
            "account-2",
            "spoofing",
            0.85
        );

        assertTrue(result.isSuspicious());
        assertTrue(result.isFraudulent());
        assertEquals("spoofing", result.getFraudType());
        assertEquals(0.85, result.getConfidence());
        assertEquals("HIGH", result.getRiskLevel());
        assertEquals("LOCAL_RULES", result.getInferenceSource());
    }

    @Test
    void createsScoredResultAndCopiesFeatures() {
        Map<String, Object> features = Map.of("velocity", 0.4, "amount", 1000.0);

        FraudDetectionResult result = FraudDetectionResult.scored(
            "trade-3",
            "account-3",
            "anomaly",
            0.55,
            "MEDIUM",
            false,
            0.91,
            0.93,
            features,
            42L,
            "REMOTE",
            "2026.04",
            18L
        );

        assertEquals("trade-3", result.getTradeId());
        assertEquals("account-3", result.getAccountId());
        assertEquals("anomaly", result.getFraudType());
        assertEquals(0.55, result.getFraudScore());
        assertEquals("MEDIUM", result.getRiskLevel());
        assertFalse(result.isFraudulent());
        assertEquals(0.91, result.getConfidence());
        assertEquals(0.93, result.getAccuracy());
        assertEquals(42L, result.getLatencyMs());
        assertEquals("REMOTE", result.getInferenceSource());
        assertEquals("2026.04", result.getModelVersion());
        assertEquals(18L, result.getInferenceLatencyMs());
        assertEquals(features, result.getFeatures());
    }

    @Test
    void requiresRiskLevelAndFeaturesForScoredResult() {
        assertThrows(NullPointerException.class, () -> FraudDetectionResult.scored(
            "trade-4",
            "account-4",
            null,
            0.1,
            null,
            false,
            0.8,
            0.9,
            Map.of(),
            1L,
            null,
            null,
            1L
        ));

        assertThrows(NullPointerException.class, () -> FraudDetectionResult.scored(
            "trade-4",
            "account-4",
            null,
            0.1,
            "LOW",
            false,
            0.8,
            0.9,
            null,
            1L,
            "REMOTE",
            null,
            1L
        ));

        assertThrows(NullPointerException.class, () -> FraudDetectionResult.scored(
            "trade-4",
            "account-4",
            null,
            0.1,
            "LOW",
            false,
            0.8,
            0.9,
            Map.of(),
            1L,
            null,
            null,
            1L
        ));
    }
}