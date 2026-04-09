package com.ghatana.finance.ai;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @doc.type class
 * @doc.purpose Verifies remote and fallback behavior for finance fraud model inference
 * @doc.layer product
 * @doc.pattern Test
 */
class DefaultFraudModelInferenceServiceTest {

    @Test
    void usesRemoteTransportWhenEndpointConfigured() {
        ModelRepository repository = new ModelRepository();
        ModelRecord model = new ModelRecord();
        model.setModelId("fraud-detection-v2");
        model.setMetadata(Map.of("fraud_endpoint", "http://fraud-model.internal/predict"));
        repository.save(model);

        DefaultFraudModelInferenceService service = new DefaultFraudModelInferenceService(
            repository,
            (endpointConfig, request) -> {
                assertEquals("http://fraud-model.internal/predict", endpointConfig.getEndpoint());
                assertEquals("fraud-detection-v2", request.getModelId());
                return new FraudModelPrediction(0.91, "HIGH", true, 0.97, 0.96, "remote-anomaly", "REMOTE", "2026.04", 18L);
            }
        );

        FraudModelPrediction prediction = service.predict("fraud-detection-v2", Map.of("amount", 1000.0));

        assertEquals(0.91, prediction.getFraudScore());
        assertEquals("HIGH", prediction.getRiskLevel());
        assertEquals("remote-anomaly", prediction.getFraudType());
        assertEquals("REMOTE", prediction.getInferenceSource());
        assertEquals("2026.04", prediction.getModelVersion());
        assertEquals(18L, prediction.getLatencyMs());
    }

    @Test
    void fallsBackWhenRemoteTransportFails() {
        ModelRepository repository = new ModelRepository();
        ModelRecord model = new ModelRecord();
        model.setModelId("fraud-detection-v2");
        model.setMetadata(Map.of("endpoint", "http://fraud-model.internal/predict"));
        repository.save(model);

        DefaultFraudModelInferenceService service = new DefaultFraudModelInferenceService(
            repository,
            (endpointConfig, request) -> {
                throw new IllegalStateException("transport unavailable");
            }
        );

        FraudModelPrediction prediction = service.predict("fraud-detection-v2", Map.of(
            "amount_factor", 0.5,
            "velocity_score", 12.0,
            "geolocation_risk", 0.3,
            "time_risk", 0.2,
            "merchant_risk", 0.2,
            "counterparty_risk", 0.22,
            "payment_method_risk", 0.18,
            "location_mismatch_risk", 0.16
        ));

        assertTrue(prediction.isFraudulent());
        assertEquals("HIGH", prediction.getRiskLevel());
        assertEquals("cross-border-anomaly", prediction.getFraudType());
        assertEquals("FALLBACK", prediction.getInferenceSource());
    }

    @Test
    void usesFallbackWhenNoEndpointConfigured() {
        DefaultFraudModelInferenceService service = new DefaultFraudModelInferenceService(new ModelRepository());

        FraudModelPrediction prediction = service.predict("fraud-detection-v2", Map.of(
            "amount_factor", 0.05,
            "velocity_score", 0.0,
            "geolocation_risk", 0.05,
            "time_risk", 0.05
        ));

        assertEquals("LOW", prediction.getRiskLevel());
        assertTrue(prediction.getFraudScore() < 0.45);
        assertEquals("FALLBACK", prediction.getInferenceSource());
    }

    @Test
    void derivesVelocityFraudTypeFromExecutionSignals() {
        DefaultFraudModelInferenceService service = new DefaultFraudModelInferenceService(new ModelRepository());

        FraudModelPrediction prediction = service.predict("fraud-detection-v2", Map.of(
            "amount_factor", 0.25,
            "velocity_score", 15.0,
            "execution_channel_risk", 0.18,
            "geolocation_risk", 0.15,
            "time_risk", 0.2
        ));

        assertTrue(prediction.isFraudulent());
        assertEquals("velocity-anomaly", prediction.getFraudType());
        assertEquals("FALLBACK", prediction.getInferenceSource());
    }

    @Test
    void honorsPredictionEndpointAliasAndConfiguredTimeout() {
        ModelRepository repository = new ModelRepository();
        ModelRecord model = new ModelRecord();
        model.setModelId("fraud-detection-v2");
        model.setMetadata(Map.of(
            "prediction_endpoint", "http://fraud-model.internal/score",
            "prediction_timeout_ms", 1500,
            "model_version", "v2.5"
        ));
        repository.save(model);

        DefaultFraudModelInferenceService service = new DefaultFraudModelInferenceService(
            repository,
            (endpointConfig, request) -> {
                assertEquals("http://fraud-model.internal/score", endpointConfig.getEndpoint());
                assertEquals(1500L, endpointConfig.getTimeout().toMillis());
                assertEquals("v2.5", endpointConfig.getModelVersion());
                assertEquals(Map.of("amount", 4200.0), request.getFeatures());
                return new FraudModelPrediction(0.12, "LOW", false, 0.88, 0.91, null, "REMOTE", "v2.5", 12L);
            }
        );

        FraudModelPrediction prediction = service.predict("fraud-detection-v2", Map.of("amount", 4200.0));

        assertNotNull(prediction);
        assertEquals("LOW", prediction.getRiskLevel());
        assertEquals("v2.5", prediction.getModelVersion());
    }

    @Test
    void fallsBackWhenRemotePayloadWouldBeInvalid() {
        ModelRepository repository = new ModelRepository();
        ModelRecord model = new ModelRecord();
        model.setModelId("fraud-detection-v2");
        model.setMetadata(Map.of("endpoint", "http://fraud-model.internal/predict"));
        repository.save(model);

        DefaultFraudModelInferenceService service = new DefaultFraudModelInferenceService(
            repository,
            (endpointConfig, request) -> {
                throw new IllegalArgumentException("fraudScore is required");
            }
        );

        FraudModelPrediction prediction = service.predict("fraud-detection-v2", Map.of(
            "amount_factor", 0.2,
            "velocity_score", 4.0,
            "geolocation_risk", 0.05,
            "time_risk", 0.05
        ));

        assertEquals("LOW", prediction.getRiskLevel());
        assertTrue(prediction.getFraudScore() < 0.45);
        assertEquals("FALLBACK", prediction.getInferenceSource());
    }

    @Test
    void retriesTransientRemoteFailuresBeforeReturningRemotePrediction() {
        ModelRepository repository = new ModelRepository();
        ModelRecord model = new ModelRecord();
        model.setModelId("fraud-detection-v2");
        model.setMetadata(Map.of("endpoint", "http://fraud-model.internal/predict"));
        repository.save(model);
        AtomicInteger attempts = new AtomicInteger();

        DefaultFraudModelInferenceService service = new DefaultFraudModelInferenceService(
            repository,
            new RetryingFraudInferenceTransport((endpointConfig, request) -> {
                if (attempts.incrementAndGet() < 3) {
                    throw new IllegalStateException("temporary outage");
                }
                return new FraudModelPrediction(0.88, "HIGH", true, 0.96, 0.94, "remote-anomaly", "REMOTE", "v3", 16L);
            }, 3)
        );

        FraudModelPrediction prediction = service.predict("fraud-detection-v2", Map.of("amount", 1000.0));

        assertEquals(3, attempts.get());
        assertEquals("REMOTE", prediction.getInferenceSource());
        assertEquals("v3", prediction.getModelVersion());
    }
}
