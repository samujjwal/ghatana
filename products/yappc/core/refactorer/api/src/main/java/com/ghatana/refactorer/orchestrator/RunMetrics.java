/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.orchestrator;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Tracks detailed metrics for a polyfix run, including performance, resource usage, and operational
 * metrics.
 
 * @doc.type class
 * @doc.purpose Handles run metrics operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class RunMetrics {
    private final Instant startTime;
    private Instant endTime;
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

    // Core metrics
    private final AtomicInteger totalDiagnostics = new AtomicInteger(0);
    private final AtomicInteger totalFixes = new AtomicInteger(0);
    private final AtomicInteger totalFiles = new AtomicInteger(0);
    private final AtomicInteger filesProcessed = new AtomicInteger(0);
    private final AtomicInteger filesSkipped = new AtomicInteger(0);
    private final AtomicInteger filesFailed = new AtomicInteger(0);

    // Detailed breakdowns
    private final Map<String, AtomicInteger> diagnosticsByType = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> fixesByType = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> filesByLanguage = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> customMetrics = new ConcurrentHashMap<>();

    // Timing metrics
    private final Map<String, Long> timingMetrics = new ConcurrentHashMap<>();
    private final Map<String, Long> phaseStartTimes = new ConcurrentHashMap<>();

    // Resource usage
    private long initialMemoryUsage;
    private long peakMemoryUsage;
    private final Map<String, Long> memorySnapshots = new ConcurrentHashMap<>();

    /**
     * Creates a new RunMetrics instance with the current time as the start time. Initializes memory
     * tracking.
     */
    public RunMetrics() {
        this.startTime = Instant.now();
        this.initialMemoryUsage = getCurrentMemoryUsage();
        this.peakMemoryUsage = initialMemoryUsage;

        // Record initial memory snapshot
        recordMemorySnapshot("initial");
    }

    /**
 * Marks the end of the run and records final metrics. */
    public void markEnd() {
        this.endTime = Instant.now();
        recordMemorySnapshot("final");

        // Record total duration
        recordTiming("total_duration", getDurationMillis());

        // Record peak memory usage
        recordMemorySnapshot("peak");

        // Calculate and record memory usage metrics
        long finalMemoryUsage = getCurrentMemoryUsage();
        recordMetric("memory.initial_mb", initialMemoryUsage / (1024 * 1024));
        recordMetric("memory.final_mb", finalMemoryUsage / (1024 * 1024));
        recordMetric("memory.peak_mb", peakMemoryUsage / (1024 * 1024));
        recordMetric("memory.used_mb", (finalMemoryUsage - initialMemoryUsage) / (1024 * 1024));
    }

    /**
     * Records a diagnostic of the given type.
     *
     * @param diagnosticType The type of diagnostic being recorded
     */
    public void recordDiagnostic(String diagnosticType) {
        Objects.requireNonNull(diagnosticType, "Diagnostic type cannot be null");
        diagnosticsByType
                .computeIfAbsent(diagnosticType, k -> new AtomicInteger())
                .incrementAndGet();
        totalDiagnostics.incrementAndGet();
    }

    /**
     * Records a fix attempt for a diagnostic type and whether it was successful.
     *
     * @param diagnosticType The type of diagnostic being fixed
     * @param successful Whether the fix was successfully applied
     */
    public void recordFixAttempt(String diagnosticType, boolean successful) {
        String status = successful ? "success" : "failed";
        String metricName = String.format("fix_attempts.%s.%s", diagnosticType, status);
        customMetrics.computeIfAbsent(metricName, k -> new LongAdder()).increment();

        if (successful) {
            recordFix(diagnosticType);
        }
    }

    /**
     * Records a successful fix of the given type.
     *
     * @param fixType The type of fix that was applied
     */
    public void recordFix(String fixType) {
        Objects.requireNonNull(fixType, "Fix type cannot be null");
        fixesByType.computeIfAbsent(fixType, k -> new AtomicInteger()).incrementAndGet();
        totalFixes.incrementAndGet();
    }

    /**
     * Records processing of a file with the given language and status.
     *
     * @param language The language of the file
     * @param status The processing status (processed, skipped, failed)
     */
    public void recordFileProcessed(String language, String status) {
        Objects.requireNonNull(language, "Language cannot be null");
        Objects.requireNonNull(status, "Status cannot be null");

        // Update language-specific counters
        filesByLanguage.computeIfAbsent(language, k -> new AtomicInteger()).incrementAndGet();

        // Update status counters
        switch (status.toLowerCase()) {
            case "processed" -> filesProcessed.incrementAndGet();
            case "skipped" -> filesSkipped.incrementAndGet();
            case "failed" -> filesFailed.incrementAndGet();
            default -> log.warn("Unknown file status: " + status);
        }

        // Update total files counter
        totalFiles.incrementAndGet();
    }

    /**
     * Records a custom metric with a numeric value.
     *
     * @param name The name of the metric
     * @param value The value to record
     */
    public void recordMetric(String name, long value) {
        customMetrics.computeIfAbsent(name, k -> new LongAdder()).add(value);
    }

    /**
     * Records a timing metric with the given duration.
     *
     * @param metricName The name of the timing metric
     * @param durationMillis The duration in milliseconds
     */
    public void recordTiming(String metricName, long durationMillis) {
        Objects.requireNonNull(metricName, "Metric name cannot be null");
        timingMetrics.merge(metricName, durationMillis, Long::sum);
    }

    /**
     * Starts timing a phase of execution.
     *
     * @param phaseName The name of the phase being timed
     */
    public void startPhase(String phaseName) {
        Objects.requireNonNull(phaseName, "Phase name cannot be null");
        phaseStartTimes.put(phaseName, System.currentTimeMillis());
    }

    /**
     * Ends timing a phase of execution and records the duration.
     *
     * @param phaseName The name of the phase being timed
     */
    public void endPhase(String phaseName) {
        Objects.requireNonNull(phaseName, "Phase name cannot be null");
        Long startTime = phaseStartTimes.get(phaseName);
        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            recordTiming(phaseName + "_duration_ms", duration);
            phaseStartTimes.remove(phaseName);
        } else {
            log.warn("Attempted to end phase that was not started: " + phaseName);
        }
    }

    // Getters for metrics

    /**
     * Gets the total number of diagnostics recorded.
     *
     * @return The total number of diagnostics
     */
    public int getTotalDiagnostics() {
        return totalDiagnostics.get();
    }

    /**
     * Gets the total number of fixes applied.
     *
     * @return The total number of fixes
     */
    public int getTotalFixes() {
        return totalFixes.get();
    }

    /**
     * Gets the total number of files processed.
     *
     * @return The number of files successfully processed
     */
    public int getFilesProcessed() {
        return filesProcessed.get();
    }

    /**
     * Gets the total number of files skipped.
     *
     * @return The number of files skipped
     */
    public int getFilesSkipped() {
        return filesSkipped.get();
    }

    /**
     * Gets the total number of files that failed processing.
     *
     * @return The number of files that failed processing
     */
    public int getFilesFailed() {
        return filesFailed.get();
    }

    /**
     * Gets the total number of files encountered.
     *
     * @return The total number of files (processed + skipped + failed)
     */
    public int getTotalFiles() {
        return totalFiles.get();
    }

    /**
     * Gets the duration of the run in milliseconds.
     *
     * @return The duration in milliseconds
     */
    public long getDurationMillis() {
        Instant end = endTime != null ? endTime : Instant.now();
        return Duration.between(startTime, end).toMillis();
    }

    /**
     * Gets all custom metrics that have been recorded.
     *
     * @return A map of metric names to their values
     */
    public Map<String, Long> getCustomMetrics() {
        Map<String, Long> result = new HashMap<>();
        customMetrics.forEach((k, v) -> result.put(k, v.longValue()));
        return result;
    }

    /**
     * Gets all memory snapshots that have been taken.
     *
     * @return A map of snapshot names to memory usage in bytes
     */
    public Map<String, Long> getMemorySnapshots() {
        return new HashMap<>(memorySnapshots);
    }

    /**
     * Gets a map of diagnostic counts by type.
     *
     * @return A map of diagnostic types to their counts
     */
    public Map<String, Integer> getDiagnosticsByType() {
        Map<String, Integer> result = new HashMap<>();
        diagnosticsByType.forEach((k, v) -> result.put(k, v.get()));
        return result;
    }

    /**
     * Gets a map of fix counts by type.
     *
     * @return A map of fix types to their counts
     */
    public Map<String, Integer> getFixesByType() {
        Map<String, Integer> result = new HashMap<>();
        fixesByType.forEach((k, v) -> result.put(k, v.get()));
        return result;
    }

    /**
     * Gets the success rate for fixes by diagnostic type.
     *
     * @return A map of diagnostic types to their success rates (0.0 to 1.0)
     */
    public Map<String, Double> getFixSuccessRates() {
        Map<String, Double> successRates = new HashMap<>();

        // For each diagnostic type, calculate success rate
        diagnosticsByType
                .keySet()
                .forEach(
                        diagnosticType -> {
                            long success =
                                    customMetrics
                                            .getOrDefault(
                                                    "fix_attempts." + diagnosticType + ".success",
                                                    new LongAdder())
                                            .longValue();
                            long failed =
                                    customMetrics
                                            .getOrDefault(
                                                    "fix_attempts." + diagnosticType + ".failed",
                                                    new LongAdder())
                                            .longValue();
                            long total = success + failed;

                            if (total > 0) {
                                successRates.put(diagnosticType, (double) success / total);
                            }
                        });

        return successRates;
    }

    /**
     * Gets a map of file counts by language.
     *
     * @return A map of languages to their file counts
     */
    public Map<String, Integer> getFilesByLanguage() {
        Map<String, Integer> result = new HashMap<>();
        filesByLanguage.forEach((k, v) -> result.put(k, v.get()));
        return result;
    }

    /**
     * Gets all timing metrics that have been recorded.
     *
     * @return A map of metric names to durations in milliseconds
     */
    public Map<String, Long> getTimingMetrics() {
        return new HashMap<>(timingMetrics);
    }

    /**
     * Gets the start time of the run.
     *
     * @return The start time
     */
    public Instant getStartTime() {
        return startTime;
    }

    /**
     * Gets the end time of the run, or null if not yet ended.
     *
     * @return The end time, or null if the run is still in progress
     */
    public Instant getEndTime() {
        return endTime;
    }

    // Helper methods for memory tracking

    private long getCurrentMemoryUsage() {
        MemoryUsage heapMemoryUsage = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeapMemoryUsage = memoryBean.getNonHeapMemoryUsage();
        return heapMemoryUsage.getUsed() + nonHeapMemoryUsage.getUsed();
    }

    private void updatePeakMemory() {
        long current = getCurrentMemoryUsage();
        if (current > peakMemoryUsage) {
            peakMemoryUsage = current;
        }
    }

    /**
     * Records a memory snapshot with the given name.
     *
     * @param name The name to give this snapshot
     */
    public void recordMemorySnapshot(String name) {
        long usage = getCurrentMemoryUsage();
        memorySnapshots.put(name, usage);
        updatePeakMemory();
    }

    private static final Logger log = LogManager.getLogger(RunMetrics.class);
}
