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
@DisplayName("QualityScoringService Tests")
@ExtendWith(MockitoExtension.class)
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
    void setUp() {
        service = new QualityScoringService(qualityScorer, metrics);
        sampleEntity = Entity.builder()
                .id(UUID.randomUUID())
                .tenantId("tenant-1")
                .collectionName("test_collection")
                .data(new HashMap<>(Map.of("name", "Test Entity")))
                .createdAt(Instant.now())
                .build();
        perfectMetrics = QualityMetrics.uniform(100);
        stubExplanation = QualityScoreExplanation.builder()
                .score(100)
                .level(QualityLevel.EXCELLENT)
                .findings(List.of())
                .recommendations(List.of())
                .build();
        lenient().when(qualityScorer.validateEntity(any(), any()))
                .thenReturn(Promise.of(QualityScorer.ValidationResult.valid()));
        lenient().when(qualityScorer.explainScore(any(), any(), any()))
                .thenReturn(Promise.of(stubExplanation));
    }

    // =========================================================================
    // CONSTRUCTION
    // =========================================================================

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should throw NullPointerException for null scorer")
        void shouldThrowForNullScorer() {
            assertThatThrownBy(() -> new QualityScoringService(null, metrics))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should throw NullPointerException for null metrics")
        void shouldThrowForNullMetrics() {
            assertThatThrownBy(() -> new QualityScoringService(qualityScorer, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // =========================================================================
    // SCORE ENTITY
    // =========================================================================

    @Nested
    @DisplayName("scoreEntity")
    class ScoreEntity {

        @Test
        @DisplayName("should return successful scoring response for valid entity")
        void shouldScoreValidEntity() {
            when(qualityScorer.scoreEntity(eq("tenant-1"), eq(sampleEntity), any()))
                    .thenReturn(Promise.of(perfectMetrics));

            QualityScoringService.ScoringResponse response =
                    runPromise(() -> service.scoreEntity("tenant-1", sampleEntity));

            assertThat(response).isNotNull();
            assertThat(response.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("should expose quality level in successful response")
        void shouldExposeQualityLevel() {
            when(qualityScorer.scoreEntity(eq("tenant-1"), eq(sampleEntity), any()))
                    .thenReturn(Promise.of(perfectMetrics));

            QualityScoringService.ScoringResponse response =
                    runPromise(() -> service.scoreEntity("tenant-1", sampleEntity));

            assertThat(response.getQualityLevel()).isNotNull();
        }

        @Test
        @DisplayName("should throw NullPointerException for null entity")
        void shouldThrowForNullEntity() {
            assertThatThrownBy(() -> runPromise(() -> service.scoreEntity("tenant-1", null)))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should throw NullPointerException for null tenantId")
        void shouldThrowForNullTenantId() {
            assertThatThrownBy(() -> runPromise(() -> service.scoreEntity(null, sampleEntity)))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should delegate scoring to QualityScorer with tenant context")
        void shouldDelegateToScorer() {
            when(qualityScorer.scoreEntity(eq("tenant-1"), eq(sampleEntity), any()))
                    .thenReturn(Promise.of(perfectMetrics));

            runPromise(() -> service.scoreEntity("tenant-1", sampleEntity));

            verify(qualityScorer).scoreEntity(eq("tenant-1"), eq(sampleEntity), any());
        }
    }

    // =========================================================================
    // SCORE ENTITIES BATCH
    // =========================================================================

    @Nested
    @DisplayName("scoreEntitiesBatch")
    class ScoreEntitiesBatch {

        @Test
        @DisplayName("should return batch response with results for all entities")
        void shouldReturnBatchResponse() {
            when(qualityScorer.scoreEntity(eq("tenant-1"), eq(sampleEntity), any()))
                    .thenReturn(Promise.of(perfectMetrics));

            QualityScoringService.BatchScoringResponse response =
                    runPromise(() -> service.scoreEntitiesBatch("tenant-1", List.of(sampleEntity)));

            assertThat(response).isNotNull();
            assertThat(response.getTotalCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("should return success rate of 1.0 when all entities score successfully")
        void shouldReturnFullSuccessRate() {
            when(qualityScorer.scoreEntity(eq("tenant-1"), any(), any()))
                    .thenReturn(Promise.of(perfectMetrics));

            QualityScoringService.BatchScoringResponse response =
                    runPromise(() -> service.scoreEntitiesBatch("tenant-1", List.of(sampleEntity)));

            assertThat(response.getSuccessRate()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("should throw NullPointerException for null entities list")
        void shouldThrowForNullEntities() {
            assertThatThrownBy(() -> runPromise(() -> service.scoreEntitiesBatch("tenant-1", null)))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should throw for empty entities list")
        void shouldThrowForEmptyEntities() {
            assertThatThrownBy(() -> runPromise(() -> service.scoreEntitiesBatch("tenant-1", List.of())))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // =========================================================================
    // GET SUPPORTED DIMENSIONS
    // =========================================================================

    @Nested
    @DisplayName("getSupportedDimensions")
    class GetSupportedDimensions {

        @Test
        @DisplayName("should return non-empty list of supported quality dimensions")
        void shouldReturnSupportedDimensions() {
            lenient().when(qualityScorer.getSupportedDimensions())
                    .thenReturn(Promise.of(List.of("completeness", "accuracy", "consistency")));

            List<String> dimensions = runPromise(() -> service.getSupportedDimensions());
            assertThat(dimensions).isNotNull().isNotEmpty();
        }
    }

    // =========================================================================
    // UPDATE CONFIGURATION
    // =========================================================================

    @Nested
    @DisplayName("updateConfiguration")
    class UpdateConfiguration {

        @Test
        @DisplayName("should delegate configuration update to scorer")
        void shouldDelegateUpdateToScorer() {
            when(qualityScorer.updateConfiguration("tenant-1", Map.of("threshold", 80)))
                    .thenReturn(Promise.complete());

            assertThatCode(() -> runPromise(() ->
                    service.updateConfiguration("tenant-1", Map.of("threshold", 80))))
                    .doesNotThrowAnyException();
            verify(qualityScorer).updateConfiguration("tenant-1", Map.of("threshold", 80));
        }

        @Test
        @DisplayName("should throw NullPointerException for null tenantId in update")
        void shouldThrowForNullTenantId() {
            assertThatThrownBy(() -> runPromise(() ->
                    service.updateConfiguration(null, Map.of())))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // =========================================================================
    // GET CONFIGURATION
    // =========================================================================

    @Nested
    @DisplayName("getConfiguration")
    class GetConfiguration {

        @Test
        @DisplayName("should return configuration map for tenant")
        void shouldReturnConfiguration() {
            when(qualityScorer.getConfiguration("tenant-1"))
                    .thenReturn(Promise.of(Map.of("threshold", 80)));

            Map<String, Object> config = runPromise(() -> service.getConfiguration("tenant-1"));
            assertThat(config).isNotNull().containsKey("threshold");
        }
    }
}
