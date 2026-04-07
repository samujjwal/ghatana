package com.ghatana.finance.ai;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @doc.type class
 * @doc.purpose Verifies fraud model endpoint metadata parsing
 * @doc.layer product
 * @doc.pattern Test
 */
class FraudModelEndpointConfigTest {

    @Test
    void resolvesEndpointAndTimeoutAliases() {
        FraudModelEndpointConfig config = FraudModelEndpointConfig.fromMetadata(Map.of(
            "prediction_endpoint", "http://fraud-model.internal/score",
            "prediction_timeout_ms", "1800",
            "modelVersion", "2026.04"
        ));

        assertEquals("http://fraud-model.internal/score", config.getEndpoint());
        assertEquals(1800L, config.getTimeout().toMillis());
        assertEquals("2026.04", config.getModelVersion());
    }

    @Test
    void fallsBackToDefaultTimeoutWhenInvalid() {
        FraudModelEndpointConfig config = FraudModelEndpointConfig.fromMetadata(Map.of(
            "endpoint", "http://fraud-model.internal/predict",
            "timeout_ms", "invalid"
        ));

        assertEquals(FraudModelEndpointConfig.DEFAULT_TIMEOUT.toMillis(), config.getTimeout().toMillis());
    }

    @Test
    void returnsNullWhenEndpointMissing() {
        assertNull(FraudModelEndpointConfig.fromMetadata(Map.of("timeout_ms", 900)));
    }
}