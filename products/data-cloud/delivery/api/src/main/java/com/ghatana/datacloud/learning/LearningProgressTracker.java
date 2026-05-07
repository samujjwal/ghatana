/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.learning;

import io.activej.promise.Promise;

import java.time.Duration;
import java.util.List;

/**
 * Tracker for learning progress metrics.
 *
 * <p>Monitors training progress and provides detailed metrics.
 *
 * @doc.type interface
 * @doc.purpose Learning progress tracking and metrics
 * @doc.layer product
 * @doc.pattern Service Interface, Observer
 */
public interface LearningProgressTracker {

    /**
     * Record training start.
     *
     * @param jobId training job ID
     * @param config training configuration
     * @return promise completing when recorded
     */
    Promise<Void> recordStart(String jobId, ModelTrainingService.TrainingConfig config);

    /**
     * Record epoch completion.
     *
     * @param jobId training job ID
     * @param epoch epoch number
     * @param metrics epoch metrics
     * @return promise completing when recorded
     */
    Promise<Void> recordEpoch(String jobId, int epoch, ModelTrainingService.EpochMetrics metrics);

    /**
     * Record training completion.
     *
     * @param jobId training job ID
     * @param finalMetrics final metrics
     * @return promise completing when recorded
     */
    Promise<Void> recordCompletion(String jobId, ModelTrainingService.TrainingMetrics finalMetrics);

    /**
     * Record training failure.
     *
     * @param jobId training job ID
     * @param error error message
     * @return promise completing when recorded
     */
    Promise<Void> recordFailure(String jobId, String error);

    /**
     * Get progress summary.
     *
     * @param jobId training job ID
     * @return promise of progress summary
     */
    Promise<ProgressSummary> getProgress(String jobId);

    /**
     * Get detailed metrics history.
     *
     * @param jobId training job ID
     * @return promise of metrics history
     */
    Promise<List<MetricPoint>> getMetricsHistory(String jobId);

    /**
     * Get comparison with previous runs.
     *
     * @param modelId model ID
     * @return promise of historical comparison
     */
    Promise<HistoricalComparison> getHistoricalComparison(String modelId);

    /**
     * Progress summary.
     */
    record ProgressSummary(
        String jobId,
        int currentEpoch,
        int totalEpochs,
        double progressPercent,
        Duration elapsedTime,
        Duration estimatedRemainingTime,
        String status,
        boolean isImproving,
        double bestValidationLoss,
        int epochsSinceImprovement
    ) {}

    /**
     * Metric point.
     */
    record MetricPoint(
        long timestamp,
        int epoch,
        String metricName,
        double value
    ) {}

    /**
     * Historical comparison.
     */
    record HistoricalComparison(
        String modelId,
        int totalRuns,
        double averageBestLoss,
        double bestLoss,
        double worstLoss,
        Duration averageDuration,
        List<RunSummary> runHistory
    ) {
        public boolean currentIsBest(double currentLoss) {
            return currentLoss <= bestLoss;
        }
    }

    /**
     * Run summary.
     */
    record RunSummary(
        String jobId,
        long startTime,
        Duration duration,
        double finalLoss,
        double bestLoss,
        int epochsCompleted,
        String status
    ) {}
}
