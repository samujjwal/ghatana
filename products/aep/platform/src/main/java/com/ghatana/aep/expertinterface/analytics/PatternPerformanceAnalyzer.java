package com.ghatana.aep.expertinterface.analytics;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Tracks performance of promoted patterns in production.
 * Monitors success rates, production metrics, and trend analysis.
 *
 * @doc.type class
 * @doc.purpose Analyzes effectiveness of patterns that experts promoted to production
 * @doc.layer core
 * @doc.pattern Service
 */
public class PatternPerformanceAnalyzer {
    private static final Logger log = LoggerFactory.getLogger(PatternPerformanceAnalyzer.class);

    private final Map<String, PatternHistory> patternHistories = new ConcurrentHashMap<>();
    private final Duration analysisWindow;
    private final int minSampleSize;

    /**
     * Creates a PatternPerformanceAnalyzer with specified window and sample requirements.
     *
     * @param analysisWindow Time window for trend analysis
     * @param minSampleSize Minimum samples required for reliable statistics
     */
    public PatternPerformanceAnalyzer(Duration analysisWindow, int minSampleSize) {
        if (analysisWindow.isNegative() || analysisWindow.isZero()) {
            throw new IllegalArgumentException("analysisWindow must be positive");
        }
        if (minSampleSize <= 0) {
            throw new IllegalArgumentException("minSampleSize must be positive");
        }

        this.analysisWindow = analysisWindow;
        this.minSampleSize = minSampleSize;
    }

    /**
     * Records a pattern execution result.
     *
     * @param patternId Pattern identifier
     * @param outcome Execution outcome (success/failure)
     * @return Promise of recording result
     */
    public Promise<PatternExecution> recordExecution(
            String patternId,
            ExecutionOutcome outcome) {

        try {
            PatternHistory history = patternHistories.computeIfAbsent(
                    patternId,
                    k -> new PatternHistory(patternId));

            PatternExecution execution = new PatternExecution(
                    patternId,
                    outcome,
                    Instant.now()
            );

            history.addExecution(execution);

            log.debug("Recorded execution for pattern {}: {}", patternId, outcome);

            return Promise.of(execution);
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }

    /**
     * Calculates success rate for a pattern.
     *
     * @param patternId Pattern identifier
     * @return Promise of success rate metrics
     */
    public Promise<SuccessRateMetrics> calculateSuccessRate(String patternId) {
        try {
            PatternHistory history = patternHistories.get(patternId);

            if (history == null) {
                return Promise.of(SuccessRateMetrics.noData(patternId));
            }

            List<PatternExecution> recent = history.getExecutionsInWindow(analysisWindow);

            if (recent.isEmpty()) {
                return Promise.of(SuccessRateMetrics.noData(patternId));
            }

            long successes = recent.stream()
                    .filter(e -> e.outcome() == ExecutionOutcome.SUCCESS)
                    .count();

            double successRate = (double) successes / recent.size();
            boolean reliable = recent.size() >= minSampleSize;

            log.debug("Pattern {} success rate: {:.2f}% ({}/{} samples, reliable: {})",
                    patternId, successRate * 100, successes, recent.size(), reliable);

            return Promise.of(new SuccessRateMetrics(
                    patternId,
                    successRate,
                    (int) successes,
                    recent.size() - (int) successes,
                    recent.size(),
                    reliable
            ));
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }

    /**
     * Analyzes performance trend for a pattern.
     *
     * @param patternId Pattern identifier
     * @return Promise of trend analysis
     */
    public Promise<TrendAnalysis> analyzeTrend(String patternId) {
        try {
            PatternHistory history = patternHistories.get(patternId);

            if (history == null) {
                return Promise.of(TrendAnalysis.noData(patternId));
            }

            List<PatternExecution> recent = history.getExecutionsInWindow(analysisWindow);

            if (recent.size() < minSampleSize) {
                return Promise.of(TrendAnalysis.insufficient(patternId, recent.size()));
            }

            // Split into early and late periods
            int midpoint = recent.size() / 2;
            List<PatternExecution> early = recent.subList(0, midpoint);
            List<PatternExecution> late = recent.subList(midpoint, recent.size());

            double earlyRate = calculateRate(early);
            double lateRate = calculateRate(late);

            double change = lateRate - earlyRate;
            TrendDirection direction = determineTrendDirection(change);

            log.info("Pattern {} trend: {} (early: {:.2f}%, late: {:.2f}%, change: {:+.2f}%)",
                    patternId, direction, earlyRate * 100, lateRate * 100, change * 100);

            return Promise.of(new TrendAnalysis(
                    patternId,
                    direction,
                    earlyRate,
                    lateRate,
                    change,
                    recent.size()
            ));
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }

    /**
     * Compares performance of multiple patterns.
     *
     * @param patternIds List of pattern identifiers to compare
     * @return Promise of performance comparison
     */
    public Promise<PerformanceComparison> comparePatterns(List<String> patternIds) {
        try {
            if (patternIds.isEmpty()) {
                return Promise.of(PerformanceComparison.empty());
            }

            List<SuccessRateMetrics> metrics = new ArrayList<>();

            for (String patternId : patternIds) {
                SuccessRateMetrics metric = runPromise(() -> calculateSuccessRate(patternId));
                metrics.add(metric);
            }

            // Find best and worst performers
            SuccessRateMetrics best = metrics.stream()
                    .max(Comparator.comparingDouble(SuccessRateMetrics::successRate))
                    .orElse(null);

            SuccessRateMetrics worst = metrics.stream()
                    .min(Comparator.comparingDouble(SuccessRateMetrics::successRate))
                    .orElse(null);

            // Calculate overall statistics
            DoubleSummaryStatistics stats = metrics.stream()
                    .mapToDouble(SuccessRateMetrics::successRate)
                    .summaryStatistics();

            log.info("Compared {} patterns: avg={:.2f}%, best={} ({:.2f}%), worst={} ({:.2f}%)",
                    patternIds.size(), stats.getAverage() * 100,
                    best != null ? best.patternId() : "none",
                    best != null ? best.successRate() * 100 : 0,
                    worst != null ? worst.patternId() : "none",
                    worst != null ? worst.successRate() * 100 : 0);

            return Promise.of(new PerformanceComparison(
                    metrics,
                    best,
                    worst,
                    stats.getAverage(),
                    stats.getMax(),
                    stats.getMin()
            ));
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }

    /**
     * Identifies underperforming patterns that need attention.
     *
     * @param threshold Success rate threshold for flagging
     * @return Promise of list of underperforming patterns
     */
    public Promise<List<UnderperformingPattern>> identifyUnderperformers(double threshold) {
        try {
            if (threshold < 0.0 || threshold > 1.0) {
                throw new IllegalArgumentException("threshold must be between 0.0 and 1.0");
            }

            List<UnderperformingPattern> underperformers = new ArrayList<>();

            for (String patternId : patternHistories.keySet()) {
                SuccessRateMetrics metrics = runPromise(() -> calculateSuccessRate(patternId));

                if (metrics.reliable() && metrics.successRate() < threshold) {
                    TrendAnalysis trend = runPromise(() -> analyzeTrend(patternId));

                    underperformers.add(new UnderperformingPattern(
                            patternId,
                            metrics.successRate(),
                            threshold,
                            trend.direction(),
                            metrics.totalExecutions()
                    ));
                }
            }

            underperformers.sort(Comparator.comparingDouble(UnderperformingPattern::successRate));

            log.warn("Identified {} underperforming patterns (threshold: {:.2f}%)",
                    underperformers.size(), threshold * 100);

            return Promise.of(underperformers);
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }

    /**
     * Gets overall system performance metrics.
     *
     * @return Promise of system-wide metrics
     */
    public Promise<SystemMetrics> getSystemMetrics() {
        try {
            int totalPatterns = patternHistories.size();
            int activePatterns = 0;
            int totalExecutions = 0;
            int totalSuccesses = 0;

            for (PatternHistory history : patternHistories.values()) {
                List<PatternExecution> recent = history.getExecutionsInWindow(analysisWindow);

                if (!recent.isEmpty()) {
                    activePatterns++;
                    totalExecutions += recent.size();
                    totalSuccesses += recent.stream()
                            .filter(e -> e.outcome() == ExecutionOutcome.SUCCESS)
                            .count();
                }
            }

            double overallSuccessRate = totalExecutions > 0
                    ? (double) totalSuccesses / totalExecutions
                    : 0.0;

            log.info("System metrics: {} patterns ({} active), {:.2f}% success rate",
                    totalPatterns, activePatterns, overallSuccessRate * 100);

            return Promise.of(new SystemMetrics(
                    totalPatterns,
                    activePatterns,
                    totalExecutions,
                    totalSuccesses,
                    overallSuccessRate
            ));
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }

    // Helper methods

    private double calculateRate(List<PatternExecution> executions) {
        if (executions.isEmpty()) {
            return 0.0;
        }

        long successes = executions.stream()
                .filter(e -> e.outcome() == ExecutionOutcome.SUCCESS)
                .count();

        return (double) successes / executions.size();
    }

    private TrendDirection determineTrendDirection(double change) {
        if (Math.abs(change) < 0.05) {
            return TrendDirection.STABLE;
        }
        return change > 0 ? TrendDirection.IMPROVING : TrendDirection.DECLINING;
    }

    // Helper for synchronous Promise execution
    private <T> T runPromise(java.util.function.Supplier<Promise<T>> supplier) {
        // In test environment, this would use EventloopTestBase.runPromise
        // In production, this would use proper Eventloop.submit
        try {
            return supplier.get().getResult();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Inner classes

    private static class PatternHistory {
        private final String patternId;
        private final Deque<PatternExecution> executions = new LinkedList<>();

        PatternHistory(String patternId) {
            this.patternId = patternId;
        }

        synchronized void addExecution(PatternExecution execution) {
            executions.addLast(execution);
        }

        synchronized List<PatternExecution> getExecutionsInWindow(Duration window) {
            Instant cutoff = Instant.now().minus(window);

            return executions.stream()
                    .filter(e -> e.timestamp().isAfter(cutoff))
                    .collect(Collectors.toList());
        }
    }

    // Enums and value objects

    public enum ExecutionOutcome {
        SUCCESS,
        FAILURE
    }

    public enum TrendDirection {
        IMPROVING,
        STABLE,
        DECLINING
    }

    public record PatternExecution(
            String patternId,
            ExecutionOutcome outcome,
            Instant timestamp
    ) {}

    public record SuccessRateMetrics(
            String patternId,
            double successRate,
            int successes,
            int failures,
            int totalExecutions,
            boolean reliable
    ) {
        public static SuccessRateMetrics noData(String patternId) {
            return new SuccessRateMetrics(patternId, 0.0, 0, 0, 0, false);
        }
    }

    public record TrendAnalysis(
            String patternId,
            TrendDirection direction,
            double earlyRate,
            double lateRate,
            double change,
            int sampleSize
    ) {
        public static TrendAnalysis noData(String patternId) {
            return new TrendAnalysis(patternId, TrendDirection.STABLE, 0.0, 0.0, 0.0, 0);
        }

        public static TrendAnalysis insufficient(String patternId, int sampleSize) {
            return new TrendAnalysis(patternId, TrendDirection.STABLE, 0.0, 0.0, 0.0, sampleSize);
        }
    }

    public record PerformanceComparison(
            List<SuccessRateMetrics> allMetrics,
            SuccessRateMetrics bestPattern,
            SuccessRateMetrics worstPattern,
            double averageSuccessRate,
            double maxSuccessRate,
            double minSuccessRate
    ) {
        public static PerformanceComparison empty() {
            return new PerformanceComparison(List.of(), null, null, 0.0, 0.0, 0.0);
        }
    }

    public record UnderperformingPattern(
            String patternId,
            double successRate,
            double threshold,
            TrendDirection trend,
            int executionCount
    ) {}

    public record SystemMetrics(
            int totalPatterns,
            int activePatterns,
            int totalExecutions,
            int totalSuccesses,
            double overallSuccessRate
    ) {}
}
