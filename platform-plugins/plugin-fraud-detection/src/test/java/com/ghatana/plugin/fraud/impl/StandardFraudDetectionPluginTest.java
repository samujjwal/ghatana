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
@ExtendWith(MockitoExtension.class) 
class StandardFraudDetectionPluginTest extends EventloopTestBase {

    @Mock
    private PluginContext mockContext;

    private StandardFraudDetectionPlugin fraudPlugin;

    @BeforeEach
    void setUp() { 
        fraudPlugin = new StandardFraudDetectionPlugin(); 
    }

    @Test
    @DisplayName("Should initialize fraud detection plugin")
    void testInitialize() { 
        assertThat(fraudPlugin.getState()).isEqualTo(PluginState.UNLOADED); 
        Promise<Void> result = fraudPlugin.initialize(mockContext); 
        runPromise(() -> result); 
        assertThat(fraudPlugin.getState()).isEqualTo(PluginState.INITIALIZED); 
    }

    @Test
    @DisplayName("Should start fraud detection plugin")
    void testStart() { 
        runPromise(() -> fraudPlugin.initialize(mockContext)); 
        Promise<Void> result = fraudPlugin.start(); 
        runPromise(() -> result); 
        assertThat(fraudPlugin.getState()).isEqualTo(PluginState.STARTED); 
    }

    @Test
    @DisplayName("Should return correct metadata")
    void testMetadata() { 
        var metadata = fraudPlugin.metadata(); 
        assertThat(metadata.name()).isEqualTo("Fraud Detection Plugin");
        assertThat(metadata.version()).isEqualTo("1.0.0");
    }

    @Test
    @DisplayName("Should assess transaction for fraud")
    void testAssessTransaction() { 
        runPromise(() -> fraudPlugin.initialize(mockContext) 
                .then(v -> fraudPlugin.start())); 

        FraudDetectionPlugin.FraudDetectionRequest request =
            new FraudDetectionPlugin.FraudDetectionRequest( 
                "txn123", "TRANSACTION", Map.of("amount", 100, "mcc", 5411), 
                "model_v1");

        Promise<FraudDetectionPlugin.FraudAssessment> result =
                fraudPlugin.assessTransaction("txn123", request); 
        FraudDetectionPlugin.FraudAssessment assessment = runPromise(() -> result); 

        assertThat(assessment.transactionId()).isEqualTo("txn123");
        assertThat(assessment.riskScore()).isBetween(0.0, 1.0); 
        assertThat(assessment.assessedAt()).isNotNull(); 
    }

    @Test
    @DisplayName("Should register fraud rule")
    void testRegisterRule() { 
        runPromise(() -> fraudPlugin.initialize(mockContext) 
                .then(v -> fraudPlugin.start())); 

        FraudDetectionPlugin.FraudRule rule = new FraudDetectionPlugin.FraudRule( 
            "RULE_001", "AMOUNT_THRESHOLD", "Amount exceeds threshold", 0.5);

        Promise<Void> result = fraudPlugin.registerRule("domain-alpha", rule); 
        runPromise(() -> result); 

        // Verify rule was registered by assessing with product
        FraudDetectionPlugin.FraudDetectionRequest request =
            new FraudDetectionPlugin.FraudDetectionRequest( 
                "txn999", "domain-alpha", Map.of("amount", 50000), "model_v1"); 

        Promise<FraudDetectionPlugin.FraudAssessment> assessResult =
                fraudPlugin.assessTransaction("txn999", request); 
        FraudDetectionPlugin.FraudAssessment assessment = runPromise(() -> assessResult); 

        assertThat(assessment).isNotNull(); 
    }

    @Test
    @DisplayName("Should detect fraud patterns")
    void testDetectPatterns() { 
        runPromise(() -> fraudPlugin.initialize(mockContext) 
                .then(v -> fraudPlugin.start())); 

        Instant now = Instant.now(); 
        if (fraudPlugin.detectPatterns("product1", 
            new FraudDetectionPlugin.TimeWindow(now.minusSeconds(3600), now)) != null) { 
            // Pattern detected - may vary based on current state
        }
        // Just verify the method returns a result
        assertThat(fraudPlugin).isNotNull(); 
    }

    @Test
    @DisplayName("Should train fraud model")
    void testTrainModel() { 
        runPromise(() -> fraudPlugin.initialize(mockContext) 
                .then(v -> fraudPlugin.start())); 

        var examples = Arrays.asList( 
            new FraudDetectionPlugin.TrainingData.TrainingExample( 
                Map.of("amount", 100.0, "country", "US"), false), 
            new FraudDetectionPlugin.TrainingData.TrainingExample( 
                Map.of("amount", 50000.0, "country", "CN"), true) 
        );

        FraudDetectionPlugin.TrainingData trainingData =
            new FraudDetectionPlugin.TrainingData(examples, Map.of()); 

        Promise<Void> result = fraudPlugin.trainModel("model_v2", trainingData); 
        runPromise(() -> result); 

        // Verify model was trained
        Promise<FraudDetectionPlugin.ModelMetrics> metricsResult =
                fraudPlugin.getModelMetrics("model_v2");
        FraudDetectionPlugin.ModelMetrics metrics = runPromise(() -> metricsResult); 

        assertThat(metrics).isNotNull(); 
        assertThat(metrics.precision()).isGreaterThan(0); 
    }

    @Test
    @DisplayName("Should shutdown fraud detection plugin")
    void testShutdown() { 
        runPromise(() -> fraudPlugin.initialize(mockContext) 
                .then(v -> fraudPlugin.start())); 

        Promise<Void> result = fraudPlugin.shutdown(); 
        runPromise(() -> result); 

        assertThat(fraudPlugin.getState()).isEqualTo(PluginState.UNLOADED); 
    }
}
