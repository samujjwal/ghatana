package com.ghatana.finance.ai;

import java.util.Map;

/**
 * Contract for finance fraud model inference.
 *
 * @doc.type interface
 * @doc.purpose Abstraction for fraud model inference execution
 * @doc.layer product
 * @doc.pattern Service
 */
public interface FraudModelInferenceService {

    FraudModelPrediction predict(String modelId, Map<String, Object> features);
}