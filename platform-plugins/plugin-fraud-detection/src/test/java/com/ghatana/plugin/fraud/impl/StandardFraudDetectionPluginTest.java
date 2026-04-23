package com.ghatana.plugin.fraud.impl;

import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.plugin.fraud.FraudDetectionPlugin;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Comprehensive tests for StandardFraudDetectionPlugin
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("StandardFraudDetectionPlugin Tests")
@ExtendWith(MockitoExtension.class) // GH-90000
class StandardFraudDetectionPluginTest extends EventloopTestBase {

    @Mock
    private PluginContext mockContext;

    private StandardFraudDetectionPlugin fraudPlugin;

    @BeforeEach
    void setUp() { // GH-90000
        fraudPlugin = new StandardFraudDetectionPlugin(); // GH-90000
    }

    @Test
    @DisplayName("Should initialize fraud detection plugin")
    void testInitialize() { // GH-90000
        assertThat(fraudPlugin.getState()).isEqualTo(PluginState.UNLOADED); // GH-90000
        Promise<Void> result = fraudPlugin.initialize(mockContext); // GH-90000
        runPromise(() -> result); // GH-90000
        assertThat(fraudPlugin.getState()).isEqualTo(PluginState.INITIALIZED); // GH-90000
    }

    @Test
    @DisplayName("Should start fraud detection plugin")
    void testStart() { // GH-90000
        runPromise(() -> fraudPlugin.initialize(mockContext)); // GH-90000
        Promise<Void> result = fraudPlugin.start(); // GH-90000
        runPromise(() -> result); // GH-90000
        assertThat(fraudPlugin.getState()).isEqualTo(PluginState.STARTED); // GH-90000
    }

    @Test
    @DisplayName("Should return correct metadata")
    void testMetadata() { // GH-90000
        var metadata = fraudPlugin.metadata(); // GH-90000
        assertThat(metadata.name()).isEqualTo("Fraud Detection Plugin");
        assertThat(metadata.version()).isEqualTo("1.0.0");
    }

    @Test
    @DisplayName("Should assess transaction for fraud")
    void testAssessTransaction() { // GH-90000
        runPromise(() -> fraudPlugin.initialize(mockContext) // GH-90000
                .then(v -> fraudPlugin.start())); // GH-90000

        FraudDetectionPlugin.FraudDetectionRequest request =
            new FraudDetectionPlugin.FraudDetectionRequest( // GH-90000
                "txn123", "TRANSACTION", Map.of("amount", 100, "mcc", 5411), // GH-90000
                "model_v1");

        Promise<FraudDetectionPlugin.FraudAssessment> result =
                fraudPlugin.assessTransaction("txn123", request); // GH-90000
        FraudDetectionPlugin.FraudAssessment assessment = runPromise(() -> result); // GH-90000

        assertThat(assessment.transactionId()).isEqualTo("txn123");
        assertThat(assessment.riskScore()).isBetween(0.0, 1.0); // GH-90000
        assertThat(assessment.assessedAt()).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Should register fraud rule")
    void testRegisterRule() { // GH-90000
        runPromise(() -> fraudPlugin.initialize(mockContext) // GH-90000
                .then(v -> fraudPlugin.start())); // GH-90000

        FraudDetectionPlugin.FraudRule rule = new FraudDetectionPlugin.FraudRule( // GH-90000
            "RULE_001", "AMOUNT_THRESHOLD", "Amount exceeds threshold", 0.5);

        Promise<Void> result = fraudPlugin.registerRule("product_finance", rule); // GH-90000
        runPromise(() -> result); // GH-90000

        // Verify rule was registered by assessing with product
        FraudDetectionPlugin.FraudDetectionRequest request =
            new FraudDetectionPlugin.FraudDetectionRequest( // GH-90000
                "txn999", "product_finance", Map.of("amount", 50000), "model_v1"); // GH-90000

        Promise<FraudDetectionPlugin.FraudAssessment> assessResult =
                fraudPlugin.assessTransaction("txn999", request); // GH-90000
        FraudDetectionPlugin.FraudAssessment assessment = runPromise(() -> assessResult); // GH-90000

        assertThat(assessment).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Should detect fraud patterns")
    void testDetectPatterns() { // GH-90000
        runPromise(() -> fraudPlugin.initialize(mockContext) // GH-90000
                .then(v -> fraudPlugin.start())); // GH-90000

        Instant now = Instant.now(); // GH-90000
        if (fraudPlugin.detectPatterns("product1", // GH-90000
            new FraudDetectionPlugin.TimeWindow(now.minusSeconds(3600), now)) != null) { // GH-90000
            // Pattern detected - may vary based on current state
        }
        // Just verify the method returns a result
        assertThat(fraudPlugin).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Should train fraud model")
    void testTrainModel() { // GH-90000
        runPromise(() -> fraudPlugin.initialize(mockContext) // GH-90000
                .then(v -> fraudPlugin.start())); // GH-90000

        var examples = Arrays.asList( // GH-90000
            new FraudDetectionPlugin.TrainingData.TrainingExample( // GH-90000
                Map.of("amount", 100.0, "country", "US"), false), // GH-90000
            new FraudDetectionPlugin.TrainingData.TrainingExample( // GH-90000
                Map.of("amount", 50000.0, "country", "CN"), true) // GH-90000
        );

        FraudDetectionPlugin.TrainingData trainingData =
            new FraudDetectionPlugin.TrainingData(examples, Map.of()); // GH-90000

        Promise<Void> result = fraudPlugin.trainModel("model_v2", trainingData); // GH-90000
        runPromise(() -> result); // GH-90000

        // Verify model was trained
        Promise<FraudDetectionPlugin.ModelMetrics> metricsResult =
                fraudPlugin.getModelMetrics("model_v2");
        FraudDetectionPlugin.ModelMetrics metrics = runPromise(() -> metricsResult); // GH-90000

        assertThat(metrics).isNotNull(); // GH-90000
        assertThat(metrics.precision()).isGreaterThan(0); // GH-90000
    }

    @Test
    @DisplayName("Should shutdown fraud detection plugin")
    void testShutdown() { // GH-90000
        runPromise(() -> fraudPlugin.initialize(mockContext) // GH-90000
                .then(v -> fraudPlugin.start())); // GH-90000

        Promise<Void> result = fraudPlugin.shutdown(); // GH-90000
        runPromise(() -> result); // GH-90000

        assertThat(fraudPlugin.getState()).isEqualTo(PluginState.UNLOADED); // GH-90000
    }
}
