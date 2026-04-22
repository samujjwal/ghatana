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
@DisplayName("PipelinePerformanceProfiler [GH-90000]")
class PipelinePerformanceProfilerTest {

    // ─── create ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create: sets correct pipeline name [GH-90000]")
    void create_setsName() { // GH-90000
        PipelinePerformanceProfiler profiler = PipelinePerformanceProfiler.create("my-pipeline [GH-90000]");
        assertThat(profiler.pipelineName()).isEqualTo("my-pipeline [GH-90000]");
    }

    @Test
    @DisplayName("create: starts with no samples [GH-90000]")
    void create_emptyInitially() { // GH-90000
        PipelinePerformanceProfiler profiler = PipelinePerformanceProfiler.create("p [GH-90000]");
        assertThat(profiler.profiledStages()).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("create: null name throws NullPointerException [GH-90000]")
    void create_nullName_throwsNPE() { // GH-90000
        assertThatNullPointerException() // GH-90000
                .isThrownBy(() -> PipelinePerformanceProfiler.create(null)); // GH-90000
    }

    // ─── recordSample ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("recordSample() [GH-90000]")
    class RecordSample {

        @Test
        @DisplayName("records a sample and makes stage visible in profiledStages [GH-90000]")
        void recordSample_stageBecomesVisible() { // GH-90000
            PipelinePerformanceProfiler profiler = PipelinePerformanceProfiler.create("p [GH-90000]");
            profiler.recordSample("enrichment", Duration.ofMillis(5)); // GH-90000
            assertThat(profiler.profiledStages()).contains("enrichment [GH-90000]");
        }

        @Test
        @DisplayName("multiple samples accumulate for the same stage [GH-90000]")
        void recordSample_accumulates() { // GH-90000
            PipelinePerformanceProfiler profiler = PipelinePerformanceProfiler.create("p [GH-90000]");
            profiler.recordSample("s1", Duration.ofMillis(10)); // GH-90000
            profiler.recordSample("s1", Duration.ofMillis(20)); // GH-90000
            profiler.recordSample("s1", Duration.ofMillis(30)); // GH-90000
            assertThat(profiler.stats("s1 [GH-90000]").sampleCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("null stageId throws NullPointerException [GH-90000]")
        void recordSample_nullStage_throwsNPE() { // GH-90000
            PipelinePerformanceProfiler profiler = PipelinePerformanceProfiler.create("p [GH-90000]");
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> profiler.recordSample(null, Duration.ofMillis(1))); // GH-90000
        }

        @Test
        @DisplayName("null duration throws NullPointerException [GH-90000]")
        void recordSample_nullDuration_throwsNPE() { // GH-90000
            PipelinePerformanceProfiler profiler = PipelinePerformanceProfiler.create("p [GH-90000]");
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> profiler.recordSample("s1", null)); // GH-90000
        }
    }

    // ─── recordSampleNs ───────────────────────────────────────────────────────

    @Test
    @DisplayName("recordSampleNs: converts nanosecond delta to micros correctly [GH-90000]")
    void recordSampleNs_convertsCorrectly() { // GH-90000
        PipelinePerformanceProfiler profiler = PipelinePerformanceProfiler.create("p [GH-90000]");
        long startNs = System.nanoTime(); // GH-90000
        long endNs = startNs + 5_000_000L; // 5ms = 5000µs
        profiler.recordSampleNs("stage-a", startNs, endNs); // GH-90000
        PipelinePerformanceProfiler.StageStats stats = profiler.stats("stage-a [GH-90000]");
        assertThat(stats.sampleCount()).isEqualTo(1); // GH-90000
        assertThat(stats.avgMicros()).isEqualTo(5_000.0); // GH-90000
    }

    // ─── stats ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("stats() [GH-90000]")
    class Stats {

        @Test
        @DisplayName("returns empty stats for unknown stage [GH-90000]")
        void stats_unknownStage_returnsEmpty() { // GH-90000
            PipelinePerformanceProfiler profiler = PipelinePerformanceProfiler.create("p [GH-90000]");
            PipelinePerformanceProfiler.StageStats stats = profiler.stats("nonexistent [GH-90000]");
            assertThat(stats.sampleCount()).isZero(); // GH-90000
            assertThat(stats.avgMicros()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("min/max are correct for a set of samples [GH-90000]")
        void stats_minMax_correct() { // GH-90000
            PipelinePerformanceProfiler profiler = PipelinePerformanceProfiler.create("p [GH-90000]");
            profiler.recordSample("stage", Duration.ofMillis(10)); // GH-90000
            profiler.recordSample("stage", Duration.ofMillis(30)); // GH-90000
            profiler.recordSample("stage", Duration.ofMillis(20)); // GH-90000

            PipelinePerformanceProfiler.StageStats stats = profiler.stats("stage [GH-90000]");
            assertThat(stats.minMicros()).isEqualTo(10_000.0); // GH-90000
            assertThat(stats.maxMicros()).isEqualTo(30_000.0); // GH-90000
            assertThat(stats.avgMicros()).isEqualTo(20_000.0); // GH-90000
        }

        @Test
        @DisplayName("p50 percentile is the median for ordered samples [GH-90000]")
        void stats_p50_isMedian() { // GH-90000
            PipelinePerformanceProfiler profiler = PipelinePerformanceProfiler.create("p [GH-90000]");
            for (int i = 1; i <= 100; i++) { // GH-90000
                profiler.recordSample("stage", Duration.ofMillis(i)); // GH-90000
            }
            PipelinePerformanceProfiler.StageStats stats = profiler.stats("stage [GH-90000]");
            // Median of 1..100 should be ~50ms
            assertThat(stats.p50Micros()).isBetween(49_000.0, 51_000.0); // GH-90000
        }

        @Test
        @DisplayName("p95 percentile is correct [GH-90000]")
        void stats_p95_correct() { // GH-90000
            PipelinePerformanceProfiler profiler = PipelinePerformanceProfiler.create("p [GH-90000]");
            for (int i = 1; i <= 100; i++) { // GH-90000
                profiler.recordSample("stage", Duration.ofMillis(i)); // GH-90000
            }
            PipelinePerformanceProfiler.StageStats stats = profiler.stats("stage [GH-90000]");
            // p95 of 1..100 should be ~95ms
            assertThat(stats.p95Micros()).isBetween(93_000.0, 97_000.0); // GH-90000
        }

        @Test
        @DisplayName("avgMs converts microseconds to milliseconds correctly [GH-90000]")
        void stats_avgMs_convertsCorrectly() { // GH-90000
            PipelinePerformanceProfiler profiler = PipelinePerformanceProfiler.create("p [GH-90000]");
            profiler.recordSample("s", Duration.ofMillis(10)); // GH-90000
            assertThat(profiler.stats("s [GH-90000]").avgMs()).isEqualTo(10.0);
        }

        @Test
        @DisplayName("null stageId throws NullPointerException [GH-90000]")
        void stats_nullStage_throwsNPE() { // GH-90000
            PipelinePerformanceProfiler profiler = PipelinePerformanceProfiler.create("p [GH-90000]");
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> profiler.stats(null)); // GH-90000
        }
    }

    // ─── allStats ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("allStats: returns stats for every profiled stage [GH-90000]")
    void allStats_allStages() { // GH-90000
        PipelinePerformanceProfiler profiler = PipelinePerformanceProfiler.create("p [GH-90000]");
        profiler.recordSample("s1", Duration.ofMillis(5)); // GH-90000
        profiler.recordSample("s2", Duration.ofMillis(10)); // GH-90000

        var all = profiler.allStats(); // GH-90000
        assertThat(all).containsKeys("s1", "s2"); // GH-90000
    }

    // ─── reset ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("reset: clears all collected samples [GH-90000]")
    void reset_clearsAllSamples() { // GH-90000
        PipelinePerformanceProfiler profiler = PipelinePerformanceProfiler.create("p [GH-90000]");
        profiler.recordSample("s1", Duration.ofMillis(5)); // GH-90000
        profiler.reset(); // GH-90000
        assertThat(profiler.profiledStages()).isEmpty(); // GH-90000
        assertThat(profiler.stats("s1 [GH-90000]").sampleCount()).isZero();
    }

    // ─── printReport ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("printReport: completes without exception [GH-90000]")
    void printReport_noException() { // GH-90000
        PipelinePerformanceProfiler profiler = PipelinePerformanceProfiler.create("test-pipe [GH-90000]");
        profiler.recordSample("stage-a", Duration.ofMillis(5)); // GH-90000
        profiler.recordSample("stage-a", Duration.ofMillis(15)); // GH-90000
        profiler.recordSample("stage-b", Duration.ofMillis(3)); // GH-90000
        profiler.printReport(); // GH-90000
    }
}
