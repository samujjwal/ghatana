package com.ghatana.finance.ai;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Fraud model inference request payload.
 *
 * @doc.type class
 * @doc.purpose Encapsulates the outbound contract for finance fraud model inference requests
 * @doc.layer product
 * @doc.pattern DTO
 */
public final class FraudModelInferenceRequest {

    private final String modelId;
    private final Map<String, Object> features;

    public FraudModelInferenceRequest(String modelId, Map<String, Object> features) {
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("modelId must not be blank");
        }
        this.modelId = modelId;
        this.features = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(features, "features cannot be null")));
    }

    public String getModelId() {
        return modelId;
    }

    public Map<String, Object> getFeatures() {
        return features;
    }

    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("modelId", modelId);
        payload.put("features", features);
        return Map.copyOf(payload);
    }
}