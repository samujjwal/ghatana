package com.ghatana.datacloud.application.quality;

import com.ghatana.datacloud.application.service.QualityScoringService;
import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.quality.QualityLevel;
import com.ghatana.datacloud.entity.quality.QualityMetrics;
import com.ghatana.datacloud.entity.quality.QualityScoreExplanation;
import com.ghatana.datacloud.entity.quality.QualityScorer;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link QualityScoringService}.
 *
 * @doc.type test
 * @doc.purpose Validate entity quality scoring, batch scoring, configuration management, and dimension queries
 * @doc.layer application
 */
@DisplayName("QualityScoringService Tests [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class QualityScoringServiceTest extends EventloopTestBase {

    @Mock
    private QualityScorer qualityScorer;

    @Mock
    private MetricsCollector metrics;

    private QualityScoringService service;

    private Entity sampleEntity;
    private QualityMetrics perfectMetrics;
    private QualityScoreExplanation stubExplanation;

    @BeforeEach
    void setUp() { // GH-90000
        service = new QualityScoringService(qualityScorer, metrics); // GH-90000
        sampleEntity = Entity.builder() // GH-90000
                .id(UUID.randomUUID()) // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("test_collection [GH-90000]")
                .data(new HashMap<>(Map.of("name", "Test Entity"))) // GH-90000
                .createdAt(Instant.now()) // GH-90000
                .build(); // GH-90000
        perfectMetrics = QualityMetrics.uniform(100); // GH-90000
        stubExplanation = QualityScoreExplanation.builder() // GH-90000
                .score(100) // GH-90000
                .level(QualityLevel.EXCELLENT) // GH-90000
                .findings(List.of()) // GH-90000
                .recommendations(List.of()) // GH-90000
                .build(); // GH-90000
        lenient().when(qualityScorer.validateEntity(any(), any())) // GH-90000
                .thenReturn(Promise.of(QualityScorer.ValidationResult.valid())); // GH-90000
        lenient().when(qualityScorer.explainScore(any(), any(), any())) // GH-90000
                .thenReturn(Promise.of(stubExplanation)); // GH-90000
    }

    // =========================================================================
    // CONSTRUCTION
    // =========================================================================

    @Nested
    @DisplayName("Construction [GH-90000]")
    class Construction {

        @Test
        @DisplayName("should throw NullPointerException for null scorer [GH-90000]")
        void shouldThrowForNullScorer() { // GH-90000
            assertThatThrownBy(() -> new QualityScoringService(null, metrics)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("should throw NullPointerException for null metrics [GH-90000]")
        void shouldThrowForNullMetrics() { // GH-90000
            assertThatThrownBy(() -> new QualityScoringService(qualityScorer, null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    // =========================================================================
    // SCORE ENTITY
    // =========================================================================

    @Nested
    @DisplayName("scoreEntity [GH-90000]")
    class ScoreEntity {

        @Test
        @DisplayName("should return successful scoring response for valid entity [GH-90000]")
        void shouldScoreValidEntity() { // GH-90000
            when(qualityScorer.scoreEntity(eq("tenant-1 [GH-90000]"), eq(sampleEntity), any()))
                    .thenReturn(Promise.of(perfectMetrics)); // GH-90000

            QualityScoringService.ScoringResponse response =
                    runPromise(() -> service.scoreEntity("tenant-1", sampleEntity)); // GH-90000

            assertThat(response).isNotNull(); // GH-90000
            assertThat(response.isSuccess()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should expose quality level in successful response [GH-90000]")
        void shouldExposeQualityLevel() { // GH-90000
            when(qualityScorer.scoreEntity(eq("tenant-1 [GH-90000]"), eq(sampleEntity), any()))
                    .thenReturn(Promise.of(perfectMetrics)); // GH-90000

            QualityScoringService.ScoringResponse response =
                    runPromise(() -> service.scoreEntity("tenant-1", sampleEntity)); // GH-90000

            assertThat(response.getQualityLevel()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("should throw NullPointerException for null entity [GH-90000]")
        void shouldThrowForNullEntity() { // GH-90000
            assertThatThrownBy(() -> runPromise(() -> service.scoreEntity("tenant-1", null))) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("should throw NullPointerException for null tenantId [GH-90000]")
        void shouldThrowForNullTenantId() { // GH-90000
            assertThatThrownBy(() -> runPromise(() -> service.scoreEntity(null, sampleEntity))) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("should delegate scoring to QualityScorer with tenant context [GH-90000]")
        void shouldDelegateToScorer() { // GH-90000
            when(qualityScorer.scoreEntity(eq("tenant-1 [GH-90000]"), eq(sampleEntity), any()))
                    .thenReturn(Promise.of(perfectMetrics)); // GH-90000

            runPromise(() -> service.scoreEntity("tenant-1", sampleEntity)); // GH-90000

            verify(qualityScorer).scoreEntity(eq("tenant-1 [GH-90000]"), eq(sampleEntity), any());
        }
    }

    // =========================================================================
    // SCORE ENTITIES BATCH
    // =========================================================================

    @Nested
    @DisplayName("scoreEntitiesBatch [GH-90000]")
    class ScoreEntitiesBatch {

        @Test
        @DisplayName("should return batch response with results for all entities [GH-90000]")
        void shouldReturnBatchResponse() { // GH-90000
            when(qualityScorer.scoreEntity(eq("tenant-1 [GH-90000]"), eq(sampleEntity), any()))
                    .thenReturn(Promise.of(perfectMetrics)); // GH-90000

            QualityScoringService.BatchScoringResponse response =
                    runPromise(() -> service.scoreEntitiesBatch("tenant-1", List.of(sampleEntity))); // GH-90000

            assertThat(response).isNotNull(); // GH-90000
            assertThat(response.getTotalCount()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("should return success rate of 1.0 when all entities score successfully [GH-90000]")
        void shouldReturnFullSuccessRate() { // GH-90000
            when(qualityScorer.scoreEntity(eq("tenant-1 [GH-90000]"), any(), any()))
                    .thenReturn(Promise.of(perfectMetrics)); // GH-90000

            QualityScoringService.BatchScoringResponse response =
                    runPromise(() -> service.scoreEntitiesBatch("tenant-1", List.of(sampleEntity))); // GH-90000

            assertThat(response.getSuccessRate()).isEqualTo(100.0); // GH-90000
        }

        @Test
        @DisplayName("should throw NullPointerException for null entities list [GH-90000]")
        void shouldThrowForNullEntities() { // GH-90000
            assertThatThrownBy(() -> runPromise(() -> service.scoreEntitiesBatch("tenant-1", null))) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("should throw for empty entities list [GH-90000]")
        void shouldThrowForEmptyEntities() { // GH-90000
            assertThatThrownBy(() -> runPromise(() -> service.scoreEntitiesBatch("tenant-1", List.of()))) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }
    }

    // =========================================================================
    // GET SUPPORTED DIMENSIONS
    // =========================================================================

    @Nested
    @DisplayName("getSupportedDimensions [GH-90000]")
    class GetSupportedDimensions {

        @Test
        @DisplayName("should return non-empty list of supported quality dimensions [GH-90000]")
        void shouldReturnSupportedDimensions() { // GH-90000
            lenient().when(qualityScorer.getSupportedDimensions()) // GH-90000
                    .thenReturn(Promise.of(List.of("completeness", "accuracy", "consistency"))); // GH-90000

            List<String> dimensions = runPromise(() -> service.getSupportedDimensions()); // GH-90000
            assertThat(dimensions).isNotNull().isNotEmpty(); // GH-90000
        }
    }

    // =========================================================================
    // UPDATE CONFIGURATION
    // =========================================================================

    @Nested
    @DisplayName("updateConfiguration [GH-90000]")
    class UpdateConfiguration {

        @Test
        @DisplayName("should delegate configuration update to scorer [GH-90000]")
        void shouldDelegateUpdateToScorer() { // GH-90000
            when(qualityScorer.updateConfiguration("tenant-1", Map.of("threshold", 80))) // GH-90000
                    .thenReturn(Promise.complete()); // GH-90000

            assertThatCode(() -> runPromise(() -> // GH-90000
                    service.updateConfiguration("tenant-1", Map.of("threshold", 80)))) // GH-90000
                    .doesNotThrowAnyException(); // GH-90000
            verify(qualityScorer).updateConfiguration("tenant-1", Map.of("threshold", 80)); // GH-90000
        }

        @Test
        @DisplayName("should throw NullPointerException for null tenantId in update [GH-90000]")
        void shouldThrowForNullTenantId() { // GH-90000
            assertThatThrownBy(() -> runPromise(() -> // GH-90000
                    service.updateConfiguration(null, Map.of()))) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    // =========================================================================
    // GET CONFIGURATION
    // =========================================================================

    @Nested
    @DisplayName("getConfiguration [GH-90000]")
    class GetConfiguration {

        @Test
        @DisplayName("should return configuration map for tenant [GH-90000]")
        void shouldReturnConfiguration() { // GH-90000
            when(qualityScorer.getConfiguration("tenant-1 [GH-90000]"))
                    .thenReturn(Promise.of(Map.of("threshold", 80))); // GH-90000

            Map<String, Object> config = runPromise(() -> service.getConfiguration("tenant-1 [GH-90000]"));
            assertThat(config).isNotNull().containsKey("threshold [GH-90000]");
        }
    }
}
