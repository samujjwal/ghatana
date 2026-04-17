package com.ghatana.integration.phrfinance;

import com.ghatana.finance.ai.DefaultFraudModelInferenceService;
import com.ghatana.finance.ai.FraudModelPrediction;
import com.ghatana.finance.ai.ModelRecord;
import com.ghatana.finance.ai.ModelRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * KP-032 — Hot-reload fraud-model demo.
 *
 * <p>Demonstrates that {@link DefaultFraudModelInferenceService} reflects an updated
 * {@link ModelRecord} on the <em>next</em> {@code predict()} call without any restart —
 * because {@link ModelRepository#findByModelId} is invoked on every request. This
 * property is the foundation of the hot-reload capability.</p>
 *
 * <p>Also verifies that the {@code finance.fraud.inference.fallback_total} counter is
 * incremented exactly once when a remote transport failure forces the local fallback
 * to run.</p>
 *
 * @doc.type class
 * @doc.purpose KP-032 hot-reload fraud-model integration test
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("KP-032 — Fraud Model Hot-Reload")
class FinanceFraudModelHotReloadIT {

    private static final String MODEL_ID = "trade-fraud-model";

    /** Standard, low-risk set of features for deterministic fallback scoring. */
    private static final Map<String, Object> LOW_RISK_FEATURES = Map.of(
            "amount_factor", 0.1,
            "velocity_score", 1.0,
            "geolocation_risk", 0.0,
            "time_risk", 0.05
    );

    private ModelRepository modelRepository;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        modelRepository = new ModelRepository();        // in-memory, no DataSource
        meterRegistry   = new SimpleMeterRegistry();
    }

    // -------------------------------------------------------------------------
    // Hot-reload: version metadata update is picked up on next predict()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Model version v1.0 is returned before hot-reload")
    void predict_returnsVersionFromModelRecord_v1() {
        saveModel(MODEL_ID, "1.0", Map.of());   // no endpoint → fallback path

        DefaultFraudModelInferenceService svc =
                new DefaultFraudModelInferenceService(modelRepository,
                        alwaysThrowingTransport(),
                        meterRegistry);

        FraudModelPrediction prediction = svc.predict(MODEL_ID, LOW_RISK_FEATURES);

        // Inference source is FALLBACK because no endpoint exists
        assertThat(prediction.getInferenceSource()).isEqualTo("FALLBACK");
        assertThat(prediction.getModelVersion()).isEqualTo("1.0");
    }

    @Test
    @DisplayName("After hot-reload model version becomes v2.0 on the next predict()")
    void predict_picksUpNewVersion_afterHotReload() {
        saveModel(MODEL_ID, "1.0", Map.of());

        DefaultFraudModelInferenceService svc =
                new DefaultFraudModelInferenceService(modelRepository,
                        alwaysThrowingTransport(),
                        meterRegistry);

        // Initial predict — v1
        FraudModelPrediction before = svc.predict(MODEL_ID, LOW_RISK_FEATURES);
        assertThat(before.getModelVersion()).isEqualTo("1.0");

        // Hot-reload: update model record in-place in the repository
        saveModel(MODEL_ID, "2.0", Map.of());

        // Next predict picks up v2 WITHOUT any service restart
        FraudModelPrediction after = svc.predict(MODEL_ID, LOW_RISK_FEATURES);
        assertThat(after.getModelVersion()).isEqualTo("2.0");
        assertThat(after.getInferenceSource()).isEqualTo("FALLBACK");
    }

    @Test
    @DisplayName("Fallback produces a deterministic risk score with known features")
    void fallback_isDeterministic_forConstantFeatures() {
        saveModel(MODEL_ID, "1.0", Map.of());

        DefaultFraudModelInferenceService svc =
                new DefaultFraudModelInferenceService(modelRepository,
                        alwaysThrowingTransport(),
                        meterRegistry);

        FraudModelPrediction p1 = svc.predict(MODEL_ID, LOW_RISK_FEATURES);
        FraudModelPrediction p2 = svc.predict(MODEL_ID, LOW_RISK_FEATURES);

        assertThat(p1.getFraudScore()).isEqualTo(p2.getFraudScore());
        assertThat(p1.getRiskLevel()).isEqualTo(p2.getRiskLevel());
    }

    // -------------------------------------------------------------------------
    // fallback counter
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("fallback_total counter increments once when remote transport fails")
    void fallbackCounter_incrementsOnRemoteFailure() {
        // Model must have an endpoint so the remote path is attempted
        Map<String, Object> endpointMeta = new HashMap<>();
        endpointMeta.put("endpoint", "http://localhost:9999/score");   // unreachable
        endpointMeta.put("model_version", "1.0");
        saveModel(MODEL_ID, "1.0", endpointMeta);

        DefaultFraudModelInferenceService svc =
                new DefaultFraudModelInferenceService(modelRepository,
                        alwaysThrowingTransport(),
                        meterRegistry);

        svc.predict(MODEL_ID, LOW_RISK_FEATURES);

        Counter fallbackCounter = meterRegistry.find("finance.fraud.inference.fallback_total").counter();
        assertThat(fallbackCounter).isNotNull();
        assertThat(fallbackCounter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("fallback_total stays zero when no remote transport failure occurs")
    void fallbackCounter_doesNotIncrementWithoutEndpoint() {
        // No endpoint in metadata → endpointConfig == null → no remote attempt → no counter increment
        saveModel(MODEL_ID, "1.0", Map.of());

        DefaultFraudModelInferenceService svc =
                new DefaultFraudModelInferenceService(modelRepository,
                        alwaysThrowingTransport(),
                        meterRegistry);

        svc.predict(MODEL_ID, LOW_RISK_FEATURES);

        Counter fallbackCounter = meterRegistry.find("finance.fraud.inference.fallback_total").counter();
        // Counter is registered lazily; if present its value must be 0
        if (fallbackCounter != null) {
            assertThat(fallbackCounter.count()).isEqualTo(0.0);
        }
    }

    @Test
    @DisplayName("Counter accumulates across multiple remote failures")
    void fallbackCounter_accumulatesAcrossMultipleFailures() {
        Map<String, Object> endpointMeta = new HashMap<>();
        endpointMeta.put("endpoint", "http://localhost:9999/score");
        endpointMeta.put("model_version", "1.0");
        saveModel(MODEL_ID, "1.0", endpointMeta);

        DefaultFraudModelInferenceService svc =
                new DefaultFraudModelInferenceService(modelRepository,
                        alwaysThrowingTransport(),
                        meterRegistry);

        svc.predict(MODEL_ID, LOW_RISK_FEATURES);
        svc.predict(MODEL_ID, LOW_RISK_FEATURES);
        svc.predict(MODEL_ID, LOW_RISK_FEATURES);

        Counter fallbackCounter = meterRegistry.find("finance.fraud.inference.fallback_total").counter();
        assertThat(fallbackCounter).isNotNull();
        assertThat(fallbackCounter.count()).isEqualTo(3.0);
    }

    // -------------------------------------------------------------------------
    // Unknown model
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Inference falls back gracefully when modelId is not registered")
    void predict_fallsBackGracefully_whenModelNotRegistered() {
        DefaultFraudModelInferenceService svc =
                new DefaultFraudModelInferenceService(modelRepository,
                        alwaysThrowingTransport(),
                        meterRegistry);

        FraudModelPrediction p = svc.predict("does-not-exist", LOW_RISK_FEATURES);

        assertThat(p.getInferenceSource()).isEqualTo("FALLBACK");
        assertThat(p.getModelVersion()).isNull();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Registers or replaces a model record in the in-memory repository.
     *
     * @param modelId  the model identifier
     * @param version  the version to embed in metadata (used by hot-reload assertions)
     * @param extra    extra metadata entries (e.g. endpoint config)
     */
    private void saveModel(String modelId, String version, Map<String, Object> extra) {
        Map<String, Object> metadata = new HashMap<>(extra);
        metadata.put("version", version);

        ModelRecord record = new ModelRecord();
        record.setModelId(modelId);
        record.setName("Trade Fraud Model");
        record.setVersion(version);
        record.setType("FRAUD_DETECTION");
        record.setMetadata(metadata);
        record.setStatus("active");

        modelRepository.save(record);
    }

    /**
     * Returns a transport implementation that always throws so the fallback path runs
     * (and the fallback counter increments) when an endpoint is configured.
     */
    private DefaultFraudModelInferenceService.FraudInferenceTransport alwaysThrowingTransport() {
        return (endpointConfig, request) -> {
            throw new IllegalStateException("Simulated remote inference failure");
        };
    }
}
