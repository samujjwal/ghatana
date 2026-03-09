package com.ghatana.aiplatform.testing;

import com.ghatana.aiplatform.registry.ModelMetadata;
import com.ghatana.aiplatform.registry.ModelRegistryService;
import com.ghatana.aiplatform.observability.AiMetricsEmitter;
import com.ghatana.platform.observability.MetricsEmitter;
import com.ghatana.testing.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ModelABTestingManager.
 *
 * Tests cover:
 * - A/B test creation and lifecycle
 * - Traffic splitting
 * - Metric collection (latency, accuracy, errors)
 * - Statistical significance testing
 * - Automatic promotion decisions
 * - Automatic rollback on degradation
 * - Gradual rollout phases
 *
 * @doc.type test
 * @doc.purpose Unit tests for A/B testing manager
 * @doc.layer platform
 */
@DisplayName("ModelABTestingManager Tests")
class ModelABTestingManagerTest extends EventloopTestBase {
    
    @Mock
    private ModelRegistryService modelRegistry;
    
    @Mock
    private AiMetricsEmitter aiMetrics;
    
    @Mock
    private MetricsEmitter metrics;
    
    private ModelABTestingManager abTestingManager;
    private String tenantId;
    private String modelName;
    private Executor blockingExecutor;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        tenantId = "tenant-1";
        modelName = "test-model";
        blockingExecutor = Executors.newFixedThreadPool(2);
        
        // Mock model registry
        ModelMetadata controlModel = createModelMetadata(modelName, "v1");
        ModelMetadata variantModel = createModelMetadata(modelName, "v2");
        
        when(modelRegistry.getModel(eq(tenantId), eq(modelName), eq("v1")))
            .thenReturn(Promise.of(Optional.of(controlModel)));
        
        when(modelRegistry.getModel(eq(tenantId), eq(modelName), eq("v2")))
            .thenReturn(Promise.of(Optional.of(variantModel)));
        
        abTestingManager = ModelABTestingManager.builder()
            .modelRegistry(modelRegistry)
            .aiMetrics(aiMetrics)
            .metrics(metrics)
            .minSampleSize(100)
            .confidenceLevel(0.95)
            .checkInterval(Duration.ofSeconds(1))
            .blockingExecutor(blockingExecutor)
            .build();
    }
    
    @Test
    @DisplayName("Should start A/B test successfully")
    void shouldStartABTestSuccessfully() {
        // WHEN
        runPromise(() -> abTestingManager.start());
        ABTest test = abTestingManager.startTest(tenantId, modelName, "v1", "v2", 0.10);
        
        // THEN
        assertThat(test).isNotNull();
        assertThat(test.getTenantId()).isEqualTo(tenantId);
        assertThat(test.getModelName()).isEqualTo(modelName);
        assertThat(test.getControlVersion()).isEqualTo("v1");
        assertThat(test.getVariantVersion()).isEqualTo("v2");
        assertThat(test.getTrafficSplit()).isEqualTo(0.10);
        
        verify(metrics).incrementCounter("abtesting.test_started",
                                        "model_name", modelName,
                                        "tenant_id", tenantId);
    }
    
    @Test
    @DisplayName("Should select model based on traffic split")
    void shouldSelectModelBasedOnTrafficSplit() {
        // GIVEN
        runPromise(() -> abTestingManager.start());
        ABTest test = abTestingManager.startTest(tenantId, modelName, "v1", "v2", 0.10);
        
        int controlCount = 0;
        int variantCount = 0;
        int trials = 1000;
        
        // WHEN
        for (int i = 0; i < trials; i++) {
            ModelMetadata selected = abTestingManager.selectModel(test.getId());
            if (selected.getVersion().equals("v2")) {
                variantCount++;
            } else {
                controlCount++;
            }
        }
        
        // THEN
        // Approximately 10% should be variant (with tolerance)
        double variantRatio = variantCount / (double) trials;
        assertThat(variantRatio).isBetween(0.08, 0.12); // 10% ± 2%
    }
    
    @Test
    @DisplayName("Should record inference metrics")
    void shouldRecordInferenceMetrics() {
        // GIVEN
        runPromise(() -> abTestingManager.start());
        ABTest test = abTestingManager.startTest(tenantId, modelName, "v1", "v2", 0.10);
        
        // WHEN
        abTestingManager.recordInference(test.getId(), "v2", Duration.ofMillis(50), 0.95, true);
        
        // THEN
        assertThat(test.getVariantSampleSize()).isEqualTo(1);
        assertThat(test.getVariantTotalLatency()).isGreaterThan(0);
        assertThat(test.getVariantTotalAccuracy()).isGreaterThan(0);
        
        verify(metrics).incrementCounter(eq("abtesting.inference_recorded"),
                                        eq("test_id"), eq(test.getId()),
                                        eq("version"), eq("v2"),
                                        eq("success"), eq("true"));
    }
    
    @Test
    @DisplayName("Should promote variant after successful test")
    void shouldPromoteVariantAfterSuccessfulTest() throws InterruptedException {
        // GIVEN
        runPromise(() -> abTestingManager.start());
        ABTest test = abTestingManager.startTest(tenantId, modelName, "v1", "v2", 1.0);
        
        // Record good variant performance
        for (int i = 0; i < 100; i++) {
            abTestingManager.recordInference(test.getId(), "v2", 
                Duration.ofMillis(40), 0.95, true);
            abTestingManager.recordInference(test.getId(), "v1", 
                Duration.ofMillis(50), 0.90, true);
        }
        
        // WHEN
        Thread.sleep(2000); // Wait for analysis
        
        // THEN
        verify(modelRegistry).promoteToProduction(tenantId, modelName, "v2");
        verify(metrics).incrementCounter(eq("abtesting.test_promoted"),
                                        eq("model_name"), eq(modelName),
                                        eq("tenant_id"), eq(tenantId));
    }
    
    @Test
    @DisplayName("Should rollback on performance degradation")
    void shouldRollbackOnPerformanceDegradation() throws InterruptedException {
        // GIVEN
        runPromise(() -> abTestingManager.start());
        ABTest test = abTestingManager.startTest(tenantId, modelName, "v1", "v2", 0.10);
        
        // Record control performance
        for (int i = 0; i < 100; i++) {
            abTestingManager.recordInference(test.getId(), "v1", 
                Duration.ofMillis(50), 0.95, true);
        }
        
        // Record poor variant performance
        for (int i = 0; i < 100; i++) {
            abTestingManager.recordInference(test.getId(), "v2", 
                Duration.ofMillis(50), 0.85, true); // 10% accuracy drop
        }
        
        // WHEN
        Thread.sleep(2000); // Wait for analysis
        
        // THEN
        verify(metrics, atLeastOnce()).incrementCounter(eq("abtesting.test_rolled_back"),
                                                       eq("model_name"), eq(modelName),
                                                       eq("tenant_id"), eq(tenantId));
    }
    
    @Test
    @DisplayName("Should rollback on high error rate")
    void shouldRollbackOnHighErrorRate() throws InterruptedException {
        // GIVEN
        runPromise(() -> abTestingManager.start());
        ABTest test = abTestingManager.startTest(tenantId, modelName, "v1", "v2", 0.10);
        
        // Record control performance
        for (int i = 0; i < 100; i++) {
            abTestingManager.recordInference(test.getId(), "v1", 
                Duration.ofMillis(50), 0.95, true);
        }
        
        // Record variant with high errors
        for (int i = 0; i < 100; i++) {
            boolean success = i < 80; // 20% error rate
            abTestingManager.recordInference(test.getId(), "v2", 
                Duration.ofMillis(50), 0.95, success);
        }
        
        // WHEN
        Thread.sleep(2000); // Wait for analysis
        
        // THEN
        verify(metrics, atLeastOnce()).incrementCounter(eq("abtesting.test_rolled_back"),
                                                       anyString(), anyString());
    }
    
    @Test
    @DisplayName("Should progress through rollout stages")
    void shouldProgressThroughRolloutStages() throws InterruptedException {
        // GIVEN
        runPromise(() -> abTestingManager.start());
        ABTest test = abTestingManager.startTest(tenantId, modelName, "v1", "v2", 0.10);
        
        // Record good variant performance
        for (int i = 0; i < 100; i++) {
            abTestingManager.recordInference(test.getId(), "v2", 
                Duration.ofMillis(40), 0.96, true);
            abTestingManager.recordInference(test.getId(), "v1", 
                Duration.ofMillis(50), 0.90, true);
        }
        
        // WHEN
        Thread.sleep(2000); // Wait for analysis
        
        // THEN
        // Should move to next stage (0.25)
        verify(metrics, atLeastOnce()).incrementCounter(eq("abtesting.test_continued"),
                                                       eq("model_name"), eq(modelName),
                                                       eq("tenant_id"), eq(tenantId),
                                                       anyString(), anyString());
    }
    
    @Test
    @DisplayName("Should wait for minimum sample size")
    void shouldWaitForMinimumSampleSize() throws InterruptedException {
        // GIVEN
        runPromise(() -> abTestingManager.start());
        ABTest test = abTestingManager.startTest(tenantId, modelName, "v1", "v2", 0.10);
        
        // Record only 50 samples (less than minSampleSize of 100)
        for (int i = 0; i < 50; i++) {
            abTestingManager.recordInference(test.getId(), "v2", 
                Duration.ofMillis(40), 0.96, true);
            abTestingManager.recordInference(test.getId(), "v1", 
                Duration.ofMillis(50), 0.90, true);
        }
        
        // WHEN
        Thread.sleep(2000); // Wait for analysis
        
        // THEN
        // Should not promote or rollback yet
        verify(modelRegistry, never()).promoteToProduction(anyString(), anyString(), anyString());
    }
    
    @Test
    @DisplayName("Should check statistical significance")
    void shouldCheckStatisticalSignificance() {
        // GIVEN
        runPromise(() -> abTestingManager.start());
        ABTest test = abTestingManager.startTest(tenantId, modelName, "v1", "v2", 0.10);
        
        // Record statistically insignificant difference
        for (int i = 0; i < 100; i++) {
            abTestingManager.recordInference(test.getId(), "v2", 
                Duration.ofMillis(50), 0.901, true); // Only 0.1% improvement
            abTestingManager.recordInference(test.getId(), "v1", 
                Duration.ofMillis(50), 0.900, true);
        }
        
        // WHEN
        Map<String, Object> stats = abTestingManager.getStatistics();
        
        // THEN
        // Should not promote due to insignificant improvement
        assertThat(stats.get("testsPromoted")).isEqualTo(0L);
    }
    
    @Test
    @DisplayName("Should get A/B testing statistics")
    void shouldGetABTestingStatistics() {
        // GIVEN
        runPromise(() -> abTestingManager.start());
        abTestingManager.startTest(tenantId, modelName, "v1", "v2", 0.10);
        
        // WHEN
        Map<String, Object> stats = abTestingManager.getStatistics();
        
        // THEN
        assertThat(stats).containsKeys("running", "activeTests", "testsStarted",
                                      "testsPromoted", "testsRolledBack", "testsContinued");
        assertThat(stats.get("running")).isEqualTo(true);
        assertThat(stats.get("activeTests")).isEqualTo(1);
        assertThat(stats.get("testsStarted")).isEqualTo(1L);
    }
    
    @Test
    @DisplayName("Should handle concurrent A/B tests")
    void shouldHandleConcurrentABTests() {
        // GIVEN
        runPromise(() -> abTestingManager.start());
        
        // WHEN
        ABTest test1 = abTestingManager.startTest(tenantId, "model1", "v1", "v2", 0.10);
        ABTest test2 = abTestingManager.startTest(tenantId, "model2", "v1", "v2", 0.25);
        
        // THEN
        assertThat(test1.getId()).isNotEqualTo(test2.getId());
        
        Map<String, Object> stats = abTestingManager.getStatistics();
        assertThat(stats.get("activeTests")).isEqualTo(2);
    }
    
    // Helper methods
    
    private ModelMetadata createModelMetadata(String name, String version) {
        return ModelMetadata.builder()
            .name(name)
            .version(version)
            .tenantId(tenantId)
            .build();
    }
}
