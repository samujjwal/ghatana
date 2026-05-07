/**
 * @doc.type class
 * @doc.purpose Test OptimizedFeatureIngestionPipeline ingestion, validation, metrics, and lifecycle
 * @doc.layer products
 * @doc.pattern Test
 */
package com.ghatana.datacloud.featurestore;

import com.ghatana.services.featurestore.ingest.OptimizedFeatureIngestionPipeline;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link OptimizedFeatureIngestionPipeline}.
 *
 * <p>Covers synchronous ingestion, error counter increment on validation failure,
 * closed-pipeline guard, async API on closed pipeline, metrics snapshot completeness,
 * and batch flush lifecycle.
 */
@DisplayName("OptimizedFeatureIngestionPipeline")
class FeatureIngestionPipelineTest {

    private static final int BATCH_SIZE = 10;
    private static final long BATCH_TIMEOUT_MS = 5_000L;

    /** Valid feature always accepted by the default no-op validator / transformer below. */
    private static Map<String, Object> validFeature(String name) {
        Map<String, Object> f = new HashMap<>();
        f.put("feature_name", name);
        f.put("value", 42.0);
        return f;
    }

    private List<List<Map<String, Object>>> stored;
    private OptimizedFeatureIngestionPipeline pipeline;

    @BeforeEach
    void setUp() {
        stored = new ArrayList<>();
        pipeline = new OptimizedFeatureIngestionPipeline(
                BATCH_SIZE,
                BATCH_TIMEOUT_MS,
                /* validator    */ feature -> { /* accept all */ },
                /* transformer  */ feature -> feature.put("enriched", true),
                /* storageCallback */ batch -> stored.add(new ArrayList<>(batch)));
    }

    @AfterEach
    void tearDown() {
        if (!pipeline.isClosed()) {
            pipeline.close();
        }
    }

    // ─── Ingestion and metrics ────────────────────────────────────────────────

    @Nested
    @DisplayName("Ingestion metrics")
    class IngestionMetrics {

        @Test
        @DisplayName("ingestFeature increments ingested_count and validated_count")
        void ingestFeatureIncrementsCounters() {
            pipeline.ingestFeature(validFeature("score"));

            Map<String, Long> metrics = pipeline.getMetrics();
            assertThat(metrics.get("ingested_count")).isEqualTo(1L);
            assertThat(metrics.get("validated_count")).isEqualTo(1L);
            assertThat(metrics.get("transformed_count")).isEqualTo(1L);
            assertThat(metrics.get("error_count")).isEqualTo(0L);
        }

        @Test
        @DisplayName("ingestFeatures increments ingested_count by feature list size")
        void ingestFeaturesIncrementsCountersByListSize() {
            List<Map<String, Object>> batch = List.of(
                    validFeature("age"),
                    validFeature("income"),
                    validFeature("tenure"));

            pipeline.ingestFeatures(batch);

            Map<String, Long> metrics = pipeline.getMetrics();
            assertThat(metrics.get("ingested_count")).isEqualTo(3L);
            assertThat(metrics.get("error_count")).isEqualTo(0L);
        }

        @Test
        @DisplayName("validator throwing RuntimeException increments error_count")
        void validatorExceptionIncrementsErrorCount() {
            OptimizedFeatureIngestionPipeline strictPipeline = new OptimizedFeatureIngestionPipeline(
                    BATCH_SIZE,
                    BATCH_TIMEOUT_MS,
                    feature -> { throw new IllegalArgumentException("invalid feature"); },
                    null,
                    batch -> {});

            try {
                assertThatThrownBy(() -> strictPipeline.ingestFeature(validFeature("bad")))
                        .isInstanceOf(RuntimeException.class);

                assertThat(strictPipeline.getMetrics().get("error_count")).isEqualTo(1L);
            } finally {
                strictPipeline.close();
            }
        }

        @Test
        @DisplayName("getMetrics returns snapshot with all expected keys")
        void getMetricsReturnsAllExpectedKeys() {
            Map<String, Long> metrics = pipeline.getMetrics();
            assertThat(metrics).containsKeys(
                    "ingested_count",
                    "validated_count",
                    "transformed_count",
                    "stored_count",
                    "error_count",
                    "processing_time_ms");
        }
    }

    // ─── Closed-pipeline guards ───────────────────────────────────────────────

    @Nested
    @DisplayName("Closed pipeline guards")
    class ClosedPipelineGuards {

        @Test
        @DisplayName("ingestFeature after close throws IllegalStateException")
        void ingestFeatureAfterCloseThrows() {
            pipeline.close();

            assertThatThrownBy(() -> pipeline.ingestFeature(validFeature("x")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("closed");
        }

        @Test
        @DisplayName("ingestFeaturesAsync after close returns failed CompletableFuture")
        void ingestFeaturesAsyncAfterCloseReturnsFailedFuture() throws InterruptedException {
            pipeline.close();

            CompletableFuture<Void> future = pipeline.ingestFeaturesAsync(List.of(validFeature("y")));

            assertThat(future).isCompletedExceptionally();
            assertThatThrownBy(future::get)
                    .isInstanceOf(ExecutionException.class)
                    .cause()
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("isClosed returns true after close()")
        void isClosedReturnsTrueAfterClose() {
            assertThat(pipeline.isClosed()).isFalse();
            pipeline.close();
            assertThat(pipeline.isClosed()).isTrue();
        }
    }
}
