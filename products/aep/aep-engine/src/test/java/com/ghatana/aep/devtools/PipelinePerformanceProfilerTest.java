package com.ghatana.aep.devtools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Unit tests for {@link PipelinePerformanceProfiler} — AEP-009.3.
 *
 * @doc.type class
 * @doc.purpose Unit tests for AEP pipeline performance profiler
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PipelinePerformanceProfiler")
class PipelinePerformanceProfilerTest {

    // ─── create ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create: sets correct pipeline name")
    void create_setsName() {
        PipelinePerformanceProfiler profiler = PipelinePerformanceProfiler.create("my-pipeline");
        assertThat(profiler.pipelineName()).isEqualTo("my-pipeline");
    }

    @Test
    @DisplayName("create: starts with no samples")
    void create_emptyInitially() {
        PipelinePerformanceProfiler profiler = PipelinePerformanceProfiler.create("p");
        assertThat(profiler.profiledStages()).isEmpty();
    }

    @Test
    @DisplayName("create: null name throws NullPointerException")
    void create_nullName_throwsNPE() {
        assertThatNullPointerException()
                .isThrownBy(() -> PipelinePerformanceProfiler.create(null));
    }

    // ─── recordSample ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("recordSample()")
    class RecordSample {

        @Test
        @DisplayName("records a sample and makes stage visible in profiledStages")
        void recordSample_stageBecomesVisible() {
            PipelinePerformanceProfiler profiler = PipelinePerformanceProfiler.create("p");
            profiler.recordSample("enrichment", Duration.ofMillis(5));
            assertThat(profiler.profiledStages()).contains("enrichment");
        }

        @Test
        @DisplayName("multiple samples accumulate for the same stage")
        void recordSample_accumulates() {
            PipelinePerformanceProfiler profiler = PipelinePerformanceProfiler.create("p");
            profiler.recordSample("s1", Duration.ofMillis(10));
            profiler.recordSample("s1", Duration.ofMillis(20));
            profiler.recordSample("s1", Duration.ofMillis(30));
            assertThat(profiler.stats("s1").sampleCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("null stageId throws NullPointerException")
        void recordSample_nullStage_throwsNPE() {
            PipelinePerformanceProfiler profiler = PipelinePerformanceProfiler.create("p");
            assertThatNullPointerException()
                    .isThrownBy(() -> profiler.recordSample(null, Duration.ofMillis(1)));
        }

        @Test
        @DisplayName("null duration throws NullPointerException")
        void recordSample_nullDuration_throwsNPE() {
            PipelinePerformanceProfiler profiler = PipelinePerformanceProfiler.create("p");
            assertThatNullPointerException()
                    .isThrownBy(() -> profiler.recordSample("s1", null));
        }
    }

    // ─── recordSampleNs ───────────────────────────────────────────────────────

    @Test
    @DisplayName("recordSampleNs: converts nanosecond delta to micros correctly")
    void recordSampleNs_convertsCorrectly() {
        PipelinePerformanceProfiler profiler = PipelinePerformanceProfiler.create("p");
        long startNs = System.nanoTime();
        long endNs = startNs + 5_000_000L; // 5ms = 5000µs
        profiler.recordSampleNs("stage-a", startNs, endNs);
        PipelinePerformanceProfiler.StageStats stats = profiler.stats("stage-a");
        assertThat(stats.sampleCount()).isEqualTo(1);
        assertThat(stats.avgMicros()).isEqualTo(5_000.0);
    }

    // ─── stats ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("stats()")
    class Stats {

        @Test
        @DisplayName("returns empty stats for unknown stage")
        void stats_unknownStage_returnsEmpty() {
            PipelinePerformanceProfiler profiler = PipelinePerformanceProfiler.create("p");
            PipelinePerformanceProfiler.StageStats stats = profiler.stats("nonexistent");
            assertThat(stats.sampleCount()).isZero();
            assertThat(stats.avgMicros()).isZero();
        }

        @Test
        @DisplayName("min/max are correct for a set of samples")
        void stats_minMax_correct() {
            PipelinePerformanceProfiler profiler = PipelinePerformanceProfiler.create("p");
            profiler.recordSample("stage", Duration.ofMillis(10));
            profiler.recordSample("stage", Duration.ofMillis(30));
            profiler.recordSample("stage", Duration.ofMillis(20));

            PipelinePerformanceProfiler.StageStats stats = profiler.stats("stage");
            assertThat(stats.minMicros()).isEqualTo(10_000.0);
            assertThat(stats.maxMicros()).isEqualTo(30_000.0);
            assertThat(stats.avgMicros()).isEqualTo(20_000.0);
        }

        @Test
        @DisplayName("p50 percentile is the median for ordered samples")
        void stats_p50_isMedian() {
            PipelinePerformanceProfiler profiler = PipelinePerformanceProfiler.create("p");
            for (int i = 1; i <= 100; i++) {
                profiler.recordSample("stage", Duration.ofMillis(i));
            }
            PipelinePerformanceProfiler.StageStats stats = profiler.stats("stage");
            // Median of 1..100 should be ~50ms
            assertThat(stats.p50Micros()).isBetween(49_000.0, 51_000.0);
        }

        @Test
        @DisplayName("p95 percentile is correct")
        void stats_p95_correct() {
            PipelinePerformanceProfiler profiler = PipelinePerformanceProfiler.create("p");
            for (int i = 1; i <= 100; i++) {
                profiler.recordSample("stage", Duration.ofMillis(i));
            }
            PipelinePerformanceProfiler.StageStats stats = profiler.stats("stage");
            // p95 of 1..100 should be ~95ms
            assertThat(stats.p95Micros()).isBetween(93_000.0, 97_000.0);
        }

        @Test
        @DisplayName("avgMs converts microseconds to milliseconds correctly")
        void stats_avgMs_convertsCorrectly() {
            PipelinePerformanceProfiler profiler = PipelinePerformanceProfiler.create("p");
            profiler.recordSample("s", Duration.ofMillis(10));
            assertThat(profiler.stats("s").avgMs()).isEqualTo(10.0);
        }

        @Test
        @DisplayName("null stageId throws NullPointerException")
        void stats_nullStage_throwsNPE() {
            PipelinePerformanceProfiler profiler = PipelinePerformanceProfiler.create("p");
            assertThatNullPointerException()
                    .isThrownBy(() -> profiler.stats(null));
        }
    }

    // ─── allStats ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("allStats: returns stats for every profiled stage")
    void allStats_allStages() {
        PipelinePerformanceProfiler profiler = PipelinePerformanceProfiler.create("p");
        profiler.recordSample("s1", Duration.ofMillis(5));
        profiler.recordSample("s2", Duration.ofMillis(10));

        var all = profiler.allStats();
        assertThat(all).containsKeys("s1", "s2");
    }

    // ─── reset ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("reset: clears all collected samples")
    void reset_clearsAllSamples() {
        PipelinePerformanceProfiler profiler = PipelinePerformanceProfiler.create("p");
        profiler.recordSample("s1", Duration.ofMillis(5));
        profiler.reset();
        assertThat(profiler.profiledStages()).isEmpty();
        assertThat(profiler.stats("s1").sampleCount()).isZero();
    }

    // ─── printReport ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("printReport: completes without exception")
    void printReport_noException() {
        PipelinePerformanceProfiler profiler = PipelinePerformanceProfiler.create("test-pipe");
        profiler.recordSample("stage-a", Duration.ofMillis(5));
        profiler.recordSample("stage-a", Duration.ofMillis(15));
        profiler.recordSample("stage-b", Duration.ofMillis(3));
        profiler.printReport();
    }
}

