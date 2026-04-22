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
@DisplayName("ML Quality Scorer Tests [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class MLQualityScorerTest extends EventloopTestBase {

    @Mock
    private ModelRegistryService modelRegistry;

    @Mock
    private AiMetricsEmitter aiMetrics;

    private MLQualityScorer scorer;

    @BeforeEach
    void setup() { // GH-90000
        scorer = new MLQualityScorer( // GH-90000
            modelRegistry,
            aiMetrics,
            Executors.newCachedThreadPool(), // GH-90000
            "quality-scorer-v1",
            0.7
        );
    }

    @Test
    @DisplayName("Should score entity with high-quality data [GH-90000]")
    void shouldScoreHighQualityEntity() { // GH-90000
        // GIVEN: Entity with complete, consistent data
        Entity entity = createEntity(Map.of( // GH-90000
            "name", "John Doe",
            "email", "john@example.com",
            "age", 30
        ));

        // AND: Model is available
        ModelMetadata model = createModel("quality-scorer-v1", "1.0.0", DeploymentStatus.PRODUCTION); // GH-90000
        when(modelRegistry.findByStatus("tenant-1", DeploymentStatus.PRODUCTION)) // GH-90000
            .thenReturn(List.of(model)); // GH-90000

        // WHEN: Scoring entity
        QualityMetrics metrics = runPromise(() -> // GH-90000
            scorer.scoreEntity("tenant-1", entity, null) // GH-90000
        );

        // THEN: Returns high quality scores
        assertThat(metrics.getOverallScore()).isGreaterThan(70); // GH-90000
        assertThat(metrics.getCompleteness()).isGreaterThan(90); // GH-90000

        // AND: Records metrics
        verify(aiMetrics).recordInference( // GH-90000
            eq("quality-scorer-v1 [GH-90000]"),
            eq("1.0.0 [GH-90000]"),
            any(Duration.class), // GH-90000
            eq(true) // GH-90000
        );
    }

    @Test
    @DisplayName("Should handle entity with missing fields [GH-90000]")
    void shouldHandleIncompleteEntity() { // GH-90000
        // GIVEN: Entity with missing fields (using HashMap to allow null values) // GH-90000
        Map<String, Object> data = new HashMap<>(); // GH-90000
        data.put("name", "John Doe"); // GH-90000
        data.put("email", null); // GH-90000
        data.put("age", ""); // GH-90000
        Entity entity = createEntity(data); // GH-90000

        ModelMetadata model = createModel("quality-scorer-v1", "1.0.0", DeploymentStatus.PRODUCTION); // GH-90000
        when(modelRegistry.findByStatus(anyString(), any())) // GH-90000
            .thenReturn(List.of(model)); // GH-90000

        // WHEN: Scoring entity
        QualityMetrics metrics = runPromise(() -> // GH-90000
            scorer.scoreEntity("tenant-1", entity, Map.of()) // GH-90000
        );

        // THEN: Completeness score reflects missing fields
        assertThat(metrics.getCompleteness()).isLessThan(100); // GH-90000
    }

    @Test
    @DisplayName("Should score batch of entities [GH-90000]")
    void shouldScoreBatch() { // GH-90000
        // GIVEN: Multiple entities
        List<Entity> entities = List.of( // GH-90000
            createEntity(Map.of("name", "Entity1", "value", 100)), // GH-90000
            createEntity(Map.of("name", "Entity2", "value", 200)), // GH-90000
            createEntity(Map.of("name", "Entity3", "value", 300)) // GH-90000
        );

        ModelMetadata model = createModel("quality-scorer-v1", "1.0.0", DeploymentStatus.PRODUCTION); // GH-90000
        when(modelRegistry.findByStatus(anyString(), any())) // GH-90000
            .thenReturn(List.of(model)); // GH-90000

        // WHEN: Scoring batch
        List<QualityMetrics> results = runPromise(() -> // GH-90000
            scorer.scoreEntitiesBatch("tenant-1", entities, null) // GH-90000
        );

        // THEN: Returns metrics for all entities
        assertThat(results).hasSize(3); // GH-90000
        assertThat(results).allMatch(m -> m.getOverallScore() > 0); // GH-90000
    }

    @Test
    @DisplayName("Should explain quality score [GH-90000]")
    void shouldExplainScore() { // GH-90000
        // GIVEN: Entity and metrics
        Entity entity = createEntity(Map.of("name", "Test", "value", 100)); // GH-90000
        QualityMetrics metrics = QualityMetrics.builder() // GH-90000
            .completeness(85) // GH-90000
            .consistency(90) // GH-90000
            .accuracy(92) // GH-90000
            .relevance(78) // GH-90000
            .build(); // GH-90000

        // WHEN: Explaining score
        QualityScoreExplanation explanation = runPromise(() -> // GH-90000
            scorer.explainScore("tenant-1", entity, metrics) // GH-90000
        );

        // THEN: Provides detailed explanation
        // Overall score is calculated as weighted average: (85*0.25 + 90*0.25 + 92*0.30 + 78*0.20) = 86.95 ≈ 87 // GH-90000
        assertThat(explanation.getScore()).isEqualTo(87); // GH-90000
        assertThat(explanation.getFindings()).isNotEmpty(); // GH-90000
        assertThat(explanation.getRecommendations()).isNotEmpty(); // GH-90000
    }

    @Test
    @DisplayName("Should reject null parameters [GH-90000]")
    void shouldRejectNullParameters() { // GH-90000
        Entity entity = createEntity(Map.of("test", "value")); // GH-90000

        assertThatThrownBy(() -> // GH-90000
            scorer.scoreEntity(null, entity, null) // GH-90000
        ).isInstanceOf(NullPointerException.class) // GH-90000
         .hasMessageContaining("tenantId [GH-90000]");

        assertThatThrownBy(() -> // GH-90000
            scorer.scoreEntity("tenant-1", null, null) // GH-90000
        ).isInstanceOf(NullPointerException.class) // GH-90000
         .hasMessageContaining("entity [GH-90000]");
    }

    @Test
    @DisplayName("Should handle model not found error [GH-90000]")
    void shouldHandleModelNotFound() { // GH-90000
        // GIVEN: No model registered
        when(modelRegistry.findByStatus(anyString(), any())) // GH-90000
            .thenReturn(List.of()); // GH-90000

        Entity entity = createEntity(Map.of("test", "value")); // GH-90000

        // WHEN/THEN: Throws ModelNotFoundException
        assertThatThrownBy(() -> // GH-90000
            runPromise(() -> scorer.scoreEntity("tenant-1", entity, Map.of())) // GH-90000
        ).isInstanceOf(MLQualityScorer.ModelNotFoundException.class); // GH-90000

        // Clear fatal error since we expected this exception
        clearFatalError(); // GH-90000

        // AND: Records failure metric
        verify(aiMetrics).recordInference( // GH-90000
            anyString(), anyString(), any(Duration.class), eq(false) // GH-90000
        );
    }

    @Test
    @DisplayName("Should validate entity [GH-90000]")
    void shouldValidateEntity() { // GH-90000
        // GIVEN: Valid entity
        Entity entity = createEntity(Map.of("name", "Test")); // GH-90000

        // WHEN: Validating
        var result = runPromise(() -> // GH-90000
            scorer.validateEntity("tenant-1", entity) // GH-90000
        );

        // THEN: Validation passes
        assertThat(result.isValid()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("Should reject empty entity [GH-90000]")
    void shouldRejectEmptyEntity() { // GH-90000
        // GIVEN: Empty entity
        Entity entity = createEntity(Map.of()); // GH-90000

        // WHEN: Validating
        var result = runPromise(() -> // GH-90000
            scorer.validateEntity("tenant-1", entity) // GH-90000
        );

        // THEN: Validation fails
        assertThat(result.isValid()).isFalse(); // GH-90000
        assertThat(result.errors()).contains("Entity data is empty [GH-90000]");
    }

    @Test
    @DisplayName("Should get supported dimensions [GH-90000]")
    void shouldGetSupportedDimensions() { // GH-90000
        // WHEN: Getting dimensions
        List<String> dimensions = runPromise(scorer::getSupportedDimensions); // GH-90000

        // THEN: Returns all quality dimensions
        assertThat(dimensions).containsExactlyInAnyOrder( // GH-90000
            "completeness", "consistency", "accuracy", "relevance"
        );
    }

    @Test
    @DisplayName("Should enforce confidence threshold [GH-90000]")
    void shouldEnforceConfidenceThreshold() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
            new MLQualityScorer(modelRegistry, aiMetrics, // GH-90000
                Executors.newCachedThreadPool(), "test", -0.1) // GH-90000
        ).isInstanceOf(IllegalArgumentException.class) // GH-90000
         .hasMessageContaining("minConfidenceThreshold [GH-90000]");

        assertThatThrownBy(() -> // GH-90000
            new MLQualityScorer(modelRegistry, aiMetrics, // GH-90000
                Executors.newCachedThreadPool(), "test", 1.5) // GH-90000
        ).isInstanceOf(IllegalArgumentException.class) // GH-90000
         .hasMessageContaining("minConfidenceThreshold [GH-90000]");
    }

    // Helper methods

    private Entity createEntity(Map<String, Object> data) { // GH-90000
        return Entity.builder() // GH-90000
            .id(UUID.randomUUID()) // GH-90000
            .tenantId("tenant-1 [GH-90000]")
            .collectionName("test_collection [GH-90000]")
            .data(new HashMap<>(data)) // GH-90000
            .createdAt(Instant.now()) // GH-90000
            .updatedAt(Instant.now()) // GH-90000
            .build(); // GH-90000
    }

    private ModelMetadata createModel(String name, String version, DeploymentStatus status) { // GH-90000
        return ModelMetadata.builder() // GH-90000
            .id(UUID.randomUUID()) // GH-90000
            .tenantId("tenant-1 [GH-90000]")
            .name(name) // GH-90000
            .version(version) // GH-90000
            .framework("tensorflow [GH-90000]")
            .deploymentStatus(status) // GH-90000
            .metadata(Map.of()) // GH-90000
            .trainingMetrics(Map.of()) // GH-90000
            .createdAt(Instant.now()) // GH-90000
            .updatedAt(Instant.now()) // GH-90000
            .build(); // GH-90000
    }
}
