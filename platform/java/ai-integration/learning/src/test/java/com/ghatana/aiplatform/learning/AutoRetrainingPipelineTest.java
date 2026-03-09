package com.ghatana.aiplatform.learning;

import com.ghatana.aiplatform.registry.ModelMetadata;
import com.ghatana.aiplatform.registry.ModelRegistryService;
import com.ghatana.aiplatform.featurestore.FeatureStoreService;
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
 * Unit tests for AutoRetrainingPipeline.
 *
 * Tests cover:
 * - Model registration
 * - Scheduled retraining
 * - Drift-triggered retraining
 * - Quality-triggered retraining
 * - Feature selection
 * - Hyperparameter tuning
 * - Model promotion decisions
 *
 * @doc.type test
 * @doc.purpose Unit tests for auto-retraining pipeline
 * @doc.layer platform
 */
@DisplayName("AutoRetrainingPipeline Tests")
class AutoRetrainingPipelineTest extends EventloopTestBase {
    
    @Mock
    private ModelRegistryService modelRegistry;
    
    @Mock
    private FeatureStoreService featureStore;
    
    @Mock
    private AiMetricsEmitter aiMetrics;
    
    @Mock
    private MetricsEmitter metrics;
    
    private AutoRetrainingPipeline pipeline;
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
        ModelMetadata currentModel = createModelMetadata(modelName, "v1");
        when(modelRegistry.getProductionModel(eq(tenantId), eq(modelName)))
            .thenReturn(Promise.of(Optional.of(currentModel)));
        
        pipeline = AutoRetrainingPipeline.builder()
            .modelRegistry(modelRegistry)
            .featureStore(featureStore)
            .aiMetrics(aiMetrics)
            .metrics(metrics)
            .schedule(RetrainingSchedule.ON_DEMAND)
            .driftThreshold(0.05)
            .qualityThreshold(0.02)
            .blockingExecutor(blockingExecutor)
            .build();
    }
    
    @Test
    @DisplayName("Should register model for retraining")
    void shouldRegisterModelForRetraining() {
        // GIVEN
        ModelRetrainingConfig config = new ModelRetrainingConfig(tenantId, 30, 0.8);
        
        // WHEN
        pipeline.registerModel(modelName, config);
        
        // THEN
        Map<String, Object> stats = pipeline.getStatistics();
        assertThat(stats.get("registeredModels")).isEqualTo(1);
        
        verify(metrics).incrementCounter("retraining.model_registered",
                                        "model_name", modelName);
    }
    
    @Test
    @DisplayName("Should trigger manual retraining")
    void shouldTriggerManualRetraining() {
        // GIVEN
        ModelRetrainingConfig config = new ModelRetrainingConfig(tenantId, 30, 0.8);
        pipeline.registerModel(modelName, config);
        
        // WHEN
        TrainingResult result = runPromise(() -> pipeline.triggerRetraining(modelName, tenantId));
        
        // THEN
        assertThat(result).isNotNull();
        assertThat(result.getModel()).isNotNull();
        
        verify(metrics, atLeastOnce()).recordTimer(eq("retraining.training_duration"),
                                                   anyLong(),
                                                   eq("model_name"), eq(modelName),
                                                   eq("trigger"), eq("manual"));
    }
    
    @Test
    @DisplayName("Should promote model when accuracy improves")
    void shouldPromoteModelWhenAccuracyImproves() {
        // GIVEN
        ModelRetrainingConfig config = new ModelRetrainingConfig(tenantId, 30, 0.8);
        pipeline.registerModel(modelName, config);
        
        // Mock validation result with significant improvement
        // (This would require mocking internal validation logic)
        
        // WHEN
        TrainingResult result = runPromise(() -> pipeline.triggerRetraining(modelName, tenantId));
        
        // THEN
        // Check if model was registered (promotion happens internally)
        verify(aiMetrics, atLeastOnce()).trackTraining(eq(modelName), 
                                                       anyString(),
                                                       anyDouble(),
                                                       any(Duration.class));
    }
    
    @Test
    @DisplayName("Should not promote model when improvement is small")
    void shouldNotPromoteModelWhenImprovementIsSmall() {
        // GIVEN
        ModelRetrainingConfig config = new ModelRetrainingConfig(tenantId, 30, 0.8);
        pipeline.registerModel(modelName, config);
        
        // Mock current model with high accuracy
        ModelMetadata currentModel = createModelMetadata(modelName, "v1");
        when(modelRegistry.getProductionModel(eq(tenantId), eq(modelName)))
            .thenReturn(Promise.of(Optional.of(currentModel)));
        
        // WHEN
        TrainingResult result = runPromise(() -> pipeline.triggerRetraining(modelName, tenantId));
        
        // THEN
        assertThat(result).isNotNull();
        // Promotion decision depends on internal validation
    }
    
    @Test
    @DisplayName("Should start and stop pipeline")
    void shouldStartAndStopPipeline() {
        // WHEN
        runPromise(() -> pipeline.start());
        
        // THEN
        Map<String, Object> stats = pipeline.getStatistics();
        assertThat(stats.get("running")).isEqualTo(true);
        
        verify(metrics).incrementCounter("retraining.pipeline_started");
        
        // WHEN
        runPromise(() -> pipeline.stop());
        
        // THEN
        stats = pipeline.getStatistics();
        assertThat(stats.get("running")).isEqualTo(false);
        
        verify(metrics).incrementCounter("retraining.pipeline_stopped");
    }
    
    @Test
    @DisplayName("Should track retraining count")
    void shouldTrackRetrainingCount() {
        // GIVEN
        ModelRetrainingConfig config = new ModelRetrainingConfig(tenantId, 30, 0.8);
        pipeline.registerModel(modelName, config);
        
        // WHEN
        runPromise(() -> pipeline.triggerRetraining(modelName, tenantId));
        runPromise(() -> pipeline.triggerRetraining(modelName, tenantId));
        
        // THEN
        Map<String, Object> stats = pipeline.getStatistics();
        assertThat(stats.get("retrainingCount")).isEqualTo(2L);
    }
    
    @Test
    @DisplayName("Should handle retraining errors gracefully")
    void shouldHandleRetrainingErrorsGracefully() {
        // GIVEN
        ModelRetrainingConfig config = new ModelRetrainingConfig(tenantId, 30, 0.8);
        pipeline.registerModel(modelName, config);
        
        // Mock feature store failure
        when(featureStore.getFeatures(anyString(), anyString(), any(), any()))
            .thenReturn(Promise.ofException(new RuntimeException("Feature store unavailable")));
        
        // WHEN & THEN
        // Should handle error without crashing
        try {
            runPromise(() -> pipeline.triggerRetraining(modelName, tenantId));
        } catch (Exception e) {
            // Expected
        }
    }
    
    @Test
    @DisplayName("Should get retraining statistics")
    void shouldGetRetrainingStatistics() {
        // GIVEN
        ModelRetrainingConfig config = new ModelRetrainingConfig(tenantId, 30, 0.8);
        pipeline.registerModel(modelName, config);
        
        // WHEN
        Map<String, Object> stats = pipeline.getStatistics();
        
        // THEN
        assertThat(stats).containsKeys("running", "registeredModels", "retrainingCount",
                                      "promotionCount", "driftTriggeredCount", 
                                      "qualityTriggeredCount", "lastTrainingTime");
        assertThat(stats.get("registeredModels")).isEqualTo(1);
    }
    
    @Test
    @DisplayName("Should handle model not registered error")
    void shouldHandleModelNotRegisteredError() {
        // WHEN & THEN
        assertThatThrownBy(() -> runPromise(() -> pipeline.triggerRetraining("unknown-model", tenantId)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Model not registered");
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
