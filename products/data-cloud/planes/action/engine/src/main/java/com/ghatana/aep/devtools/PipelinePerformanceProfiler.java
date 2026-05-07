package com.ghatana.aep.devtools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Pipeline performance profiler for AEP developer tooling (AEP-009.3).
 *
 * <p>Collects per-stage latency samples and computes summary statistics
 * (min, max, avg, p50, p95, p99 percentiles) to help developers identify
 * bottlenecks during local testing and CI performance regression checks.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * PipelinePerformanceProfiler profiler = PipelinePerformanceProfiler.create("fraud-pipe");
 *
 * long start = System.nanoTime();
 * // ... execute stage ...
 * profiler.recordSample("anomaly-detector", Duration.ofNanos(System.nanoTime() - start));
 *
 * // Print a summary report
 * profiler.printReport();
 *
 * // Access stats programmatically
 * StageStats stats = profiler.stats("anomaly-detector");
 * assertThat(stats.avgMs()).isLessThan(10);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Developer performance profiler for AEP pipeline stage latency
 * @doc.layer product
 * @doc.pattern Observer
 */
public final class PipelinePerformanceProfiler {

    private static final Logger LOG = LoggerFactory.getLogger(PipelinePerformanceProfiler.class);

    private final String pipelineName;
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Long>> stageSamples;

    private PipelinePerformanceProfiler(String pipelineName) {
        this.pipelineName = pipelineName;
        this.stageSamples = new ConcurrentHashMap<>();
    }

    /**
     * Creates a new profiler for the given pipeline.
     *
     * @param pipelineName pipeline name attached to all stage samples
     * @return a fresh profiler with no samples
     * @throws NullPointerException if pipelineName is null
     */
    public static PipelinePerformanceProfiler create(String pipelineName) {
        Objects.requireNonNull(pipelineName, "pipelineName must not be null");
        return new PipelinePerformanceProfiler(pipelineName);
    }

    // ─── recording API ────────────────────────────────────────────────────────

    /**
     * Records a latency sample for the given stage.
     *
     * @param stageId  the stage identifier
     * @param duration the measured duration for this sample
     * @throws NullPointerException if stageId or duration is null
     */
    public void recordSample(String stageId, Duration duration) {
        Objects.requireNonNull(stageId, "stageId must not be null");
        Objects.requireNonNull(duration, "duration must not be null");
        long micros = duration.toNanos() / 1_000;
        stageSamples.computeIfAbsent(stageId, k -> new CopyOnWriteArrayList<>()).add(micros);
        LOG.trace("[profiler] [{}] stage={} sample={}µs", pipelineName, stageId, micros);
    }

    /**
     * Records a latency sample using raw nanosecond values.
     *
     * @param stageId  the stage identifier
     * @param startNs  System.nanoTime() at start of processing
     * @param endNs    System.nanoTime() at end of processing
     * @throws NullPointerException if stageId is null
     */
    public void recordSampleNs(String stageId, long startNs, long endNs) {
        recordSample(stageId, Duration.ofNanos(Math.max(0, endNs - startNs)));
    }

    /**
     * Clears all collected samples.
     */
    public void reset() {
        stageSamples.clear();
    }

    // ─── query API ────────────────────────────────────────────────────────────

    /**
     * Returns the computed statistics for a given stage.
     *
     * @param stageId the stage identifier
     * @return a {@link StageStats} summary; returns a zero-stats object if no samples recorded
     * @throws NullPointerException if stageId is null
     */
    public StageStats stats(String stageId) {
        Objects.requireNonNull(stageId, "stageId must not be null");
        List<Long> samples = stageSamples.getOrDefault(stageId, new CopyOnWriteArrayList<>());
        if (samples.isEmpty()) {
            return StageStats.empty(stageId);
        }
        return StageStats.compute(stageId, new ArrayList<>(samples));
    }

    /**
     * @return an unmodifiable map of stageId → {@link StageStats} for all recorded stages
     */
    public Map<String, StageStats> allStats() {
        return stageSamples.keySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        stageId -> stageId,
                        this::stats));
    }

    /**
     * @return the set of stage IDs that have at least one recorded sample
     */
    public java.util.Set<String> profiledStages() {
        return Collections.unmodifiableSet(stageSamples.keySet());
    }

    /**
     * @return the pipeline name
     */
    public String pipelineName() {
        return pipelineName;
    }

    /**
     * Prints a formatted summary report to STDOUT for developer convenience.
     */
    public void printReport() {
        System.out.printf("══ PipelinePerformanceProfiler: %s ══%n", pipelineName);
        System.out.printf("  %-30s %8s %8s %8s %8s %8s %8s %8s%n",
                "Stage", "Count", "MinµS", "MaxµS", "AvgµS", "P50µS", "P95µS", "P99µS");
        System.out.println("  " + "─".repeat(88));

        allStats().forEach((stage, s) ->
                System.out.printf("  %-30s %8d %8.1f %8.1f %8.1f %8.1f %8.1f %8.1f%n",
                        stage, s.sampleCount(), s.minMicros(), s.maxMicros(),
                        s.avgMicros(), s.p50Micros(), s.p95Micros(), s.p99Micros()));
    }

    // ─── StageStats ──────────────────────────────────────────────────────────

    /**
     * Computed latency statistics for a single pipeline stage.
     *
     * <p>All latency values are in <em>microseconds (µs)</em>.
     */
    public record StageStats(
            String stageId,
            int sampleCount,
            double minMicros,
            double maxMicros,
            double avgMicros,
            double p50Micros,
            double p95Micros,
            double p99Micros
    ) {

        /** @return average latency in milliseconds */
        public double avgMs() {
            return avgMicros / 1_000.0;
        }

        /** @return p95 latency in milliseconds */
        public double p95Ms() {
            return p95Micros / 1_000.0;
        }

        /** @return p99 latency in milliseconds */
        public double p99Ms() {
            return p99Micros / 1_000.0;
        }

        static StageStats empty(String stageId) {
            return new StageStats(stageId, 0, 0, 0, 0, 0, 0, 0);
        }

        static StageStats compute(String stageId, List<Long> samples) {
            List<Long> sorted = new ArrayList<>(samples);
            Collections.sort(sorted);

            long min = sorted.get(0);
            long max = sorted.get(sorted.size() - 1);
            double avg = sorted.stream().mapToLong(Long::longValue).average().orElse(0);
            double p50 = percentile(sorted, 50);
            double p95 = percentile(sorted, 95);
            double p99 = percentile(sorted, 99);

            return new StageStats(stageId, sorted.size(), min, max, avg, p50, p95, p99);
        }

        private static double percentile(List<Long> sorted, int pct) {
            if (sorted.isEmpty()) return 0;
            int idx = (int) Math.ceil(pct / 100.0 * sorted.size()) - 1;
            return sorted.get(Math.max(0, Math.min(idx, sorted.size() - 1)));
        }
    }
}
