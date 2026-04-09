package com.ghatana.finance.ai;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @doc.type class
 * @doc.purpose Verifies fraud model inference request normalization
 * @doc.layer product
 * @doc.pattern Test
 */
class FraudModelInferenceRequestTest {

    @Test
    void copiesFeaturesIntoStablePayload() {
        Map<String, Object> features = new HashMap<>();
        features.put("amount", 4200.0);

        FraudModelInferenceRequest request = new FraudModelInferenceRequest("fraud-detection-v2", features);
        features.put("amount", 9999.0);

        assertEquals(4200.0, request.getFeatures().get("amount"));
        assertEquals("fraud-detection-v2", request.toPayload().get("modelId"));
    }

    @Test
    void rejectsBlankModelId() {
        assertThrows(IllegalArgumentException.class, () -> new FraudModelInferenceRequest(" ", Map.of()));
    }
}
