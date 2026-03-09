package com.ghatana.datacloud.infrastructure.quality;

import com.ghatana.aiplatform.observability.AiMetricsEmitter;
import com.ghatana.aiplatform.registry.DeploymentStatus;
import com.ghatana.aiplatform.registry.ModelMetadata;
import com.ghatana.aiplatform.registry.ModelRegistryService;
import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.quality.QualityMetrics;
import com.ghatana.datacloud.entity.quality.QualityScoreExplanation;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link MLQualityScorer}.
 * 
 * @doc.type test
 * @doc.purpose Verify ML-based quality scoring functionality
 * @doc.layer infrastructure
 */
@DisplayName("ML Quality Scorer Tests")
@ExtendWith(MockitoExtension.class)
class MLQualityScorerTest extends EventloopTestBase {
    
    @Mock
    private ModelRegistryService modelRegistry;
    
    @Mock
    private AiMetricsEmitter aiMetrics;
    
    private MLQualityScorer scorer;
    
    @BeforeEach
    void setup() {
        scorer = new MLQualityScorer(
            modelRegistry,
            aiMetrics,
            Executors.newCachedThreadPool(),
            "quality-scorer-v1",
            0.7
        );
    }
    
    @Test
    @DisplayName("Should score entity with high-quality data")
    void shouldScoreHighQualityEntity() {
        // GIVEN: Entity with complete, consistent data
        Entity entity = createEntity(Map.of(
            "name", "John Doe",
            "email", "john@example.com",
            "age", 30
        ));
        
        // AND: Model is available
        ModelMetadata model = createModel("quality-scorer-v1", "1.0.0", DeploymentStatus.PRODUCTION);
        when(modelRegistry.findByStatus("tenant-1", DeploymentStatus.PRODUCTION))
            .thenReturn(List.of(model));
        
        // WHEN: Scoring entity
        QualityMetrics metrics = runPromise(() -> 
            scorer.scoreEntity("tenant-1", entity, null)
        );
        
        // THEN: Returns high quality scores
        assertThat(metrics.getOverallScore()).isGreaterThan(70);
        assertThat(metrics.getCompleteness()).isGreaterThan(90);
        
        // AND: Records metrics
        verify(aiMetrics).recordInference(
            eq("quality-scorer-v1"),
            eq("1.0.0"),
            any(Duration.class),
            eq(true)
        );
    }
    
    @Test
    @DisplayName("Should handle entity with missing fields")
    void shouldHandleIncompleteEntity() {
        // GIVEN: Entity with missing fields (using HashMap to allow null values)
        Map<String, Object> data = new HashMap<>();
        data.put("name", "John Doe");
        data.put("email", null);
        data.put("age", "");
        Entity entity = createEntity(data);
        
        ModelMetadata model = createModel("quality-scorer-v1", "1.0.0", DeploymentStatus.PRODUCTION);
        when(modelRegistry.findByStatus(anyString(), any()))
            .thenReturn(List.of(model));
        
        // WHEN: Scoring entity
        QualityMetrics metrics = runPromise(() -> 
            scorer.scoreEntity("tenant-1", entity, Map.of())
        );
        
        // THEN: Completeness score reflects missing fields
        assertThat(metrics.getCompleteness()).isLessThan(100);
    }
    
    @Test
    @DisplayName("Should score batch of entities")
    void shouldScoreBatch() {
        // GIVEN: Multiple entities
        List<Entity> entities = List.of(
            createEntity(Map.of("name", "Entity1", "value", 100)),
            createEntity(Map.of("name", "Entity2", "value", 200)),
            createEntity(Map.of("name", "Entity3", "value", 300))
        );
        
        ModelMetadata model = createModel("quality-scorer-v1", "1.0.0", DeploymentStatus.PRODUCTION);
        when(modelRegistry.findByStatus(anyString(), any()))
            .thenReturn(List.of(model));
        
        // WHEN: Scoring batch
        List<QualityMetrics> results = runPromise(() -> 
            scorer.scoreEntitiesBatch("tenant-1", entities, null)
        );
        
        // THEN: Returns metrics for all entities
        assertThat(results).hasSize(3);
        assertThat(results).allMatch(m -> m.getOverallScore() > 0);
    }
    
    @Test
    @DisplayName("Should explain quality score")
    void shouldExplainScore() {
        // GIVEN: Entity and metrics
        Entity entity = createEntity(Map.of("name", "Test", "value", 100));
        QualityMetrics metrics = QualityMetrics.builder()
            .completeness(85)
            .consistency(90)
            .accuracy(92)
            .relevance(78)
            .build();
        
        // WHEN: Explaining score
        QualityScoreExplanation explanation = runPromise(() -> 
            scorer.explainScore("tenant-1", entity, metrics)
        );
        
        // THEN: Provides detailed explanation
        // Overall score is calculated as weighted average: (85*0.25 + 90*0.25 + 92*0.30 + 78*0.20) = 86.95 ≈ 87
        assertThat(explanation.getScore()).isEqualTo(87);
        assertThat(explanation.getFindings()).isNotEmpty();
        assertThat(explanation.getRecommendations()).isNotEmpty();
    }
    
    @Test
    @DisplayName("Should reject null parameters")
    void shouldRejectNullParameters() {
        Entity entity = createEntity(Map.of("test", "value"));
        
        assertThatThrownBy(() -> 
            scorer.scoreEntity(null, entity, null)
        ).isInstanceOf(NullPointerException.class)
         .hasMessageContaining("tenantId");
        
        assertThatThrownBy(() -> 
            scorer.scoreEntity("tenant-1", null, null)
        ).isInstanceOf(NullPointerException.class)
         .hasMessageContaining("entity");
    }
    
    @Test
    @DisplayName("Should handle model not found error")
    void shouldHandleModelNotFound() {
        // GIVEN: No model registered
        when(modelRegistry.findByStatus(anyString(), any()))
            .thenReturn(List.of());
        
        Entity entity = createEntity(Map.of("test", "value"));
        
        // WHEN/THEN: Throws ModelNotFoundException
        assertThatThrownBy(() -> 
            runPromise(() -> scorer.scoreEntity("tenant-1", entity, Map.of()))
        ).isInstanceOf(MLQualityScorer.ModelNotFoundException.class);
        
        // Clear fatal error since we expected this exception
        clearFatalError();
        
        // AND: Records failure metric
        verify(aiMetrics).recordInference(
            anyString(), anyString(), any(Duration.class), eq(false)
        );
    }
    
    @Test
    @DisplayName("Should validate entity")
    void shouldValidateEntity() {
        // GIVEN: Valid entity
        Entity entity = createEntity(Map.of("name", "Test"));
        
        // WHEN: Validating
        var result = runPromise(() -> 
            scorer.validateEntity("tenant-1", entity)
        );
        
        // THEN: Validation passes
        assertThat(result.isValid()).isTrue();
    }
    
    @Test
    @DisplayName("Should reject empty entity")
    void shouldRejectEmptyEntity() {
        // GIVEN: Empty entity
        Entity entity = createEntity(Map.of());
        
        // WHEN: Validating
        var result = runPromise(() -> 
            scorer.validateEntity("tenant-1", entity)
        );
        
        // THEN: Validation fails
        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).contains("Entity data is empty");
    }
    
    @Test
    @DisplayName("Should get supported dimensions")
    void shouldGetSupportedDimensions() {
        // WHEN: Getting dimensions
        List<String> dimensions = runPromise(scorer::getSupportedDimensions);
        
        // THEN: Returns all quality dimensions
        assertThat(dimensions).containsExactlyInAnyOrder(
            "completeness", "consistency", "accuracy", "relevance"
        );
    }
    
    @Test
    @DisplayName("Should enforce confidence threshold")
    void shouldEnforceConfidenceThreshold() {
        assertThatThrownBy(() -> 
            new MLQualityScorer(modelRegistry, aiMetrics, 
                Executors.newCachedThreadPool(), "test", -0.1)
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("minConfidenceThreshold");
        
        assertThatThrownBy(() -> 
            new MLQualityScorer(modelRegistry, aiMetrics, 
                Executors.newCachedThreadPool(), "test", 1.5)
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("minConfidenceThreshold");
    }
    
    // Helper methods
    
    private Entity createEntity(Map<String, Object> data) {
        return Entity.builder()
            .id(UUID.randomUUID())
            .tenantId("tenant-1")
            .collectionName("test_collection")
            .data(new HashMap<>(data))
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }
    
    private ModelMetadata createModel(String name, String version, DeploymentStatus status) {
        return ModelMetadata.builder()
            .id(UUID.randomUUID())
            .tenantId("tenant-1")
            .name(name)
            .version(version)
            .framework("tensorflow")
            .deploymentStatus(status)
            .metadata(Map.of())
            .trainingMetrics(Map.of())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }
}
