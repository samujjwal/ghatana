/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.learning;

import io.activej.promise.Promise;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Service interface for model training operations.
 *
 * <p>Manages training pipelines, epochs, and model optimization.
 *
 * @doc.type interface
 * @doc.purpose Model training service for ML pipelines
 * @doc.layer product
 * @doc.pattern Service Interface
 */
public interface ModelTrainingService {

    /**
     * Start a training job.
     *
     * @param config training configuration
     * @return promise of training job
     */
    Promise<TrainingJob> startTraining(TrainingConfig config);

    /**
     * Get training job status.
     *
     * @param jobId job identifier
     * @return promise of job status
     */
    Promise<TrainingStatus> getStatus(String jobId);

    /**
     * Pause a training job.
     *
     * @param jobId job identifier
     * @return promise of paused job
     */
    Promise<TrainingJob> pauseTraining(String jobId);

    /**
     * Resume a training job.
     *
     * @param jobId job identifier
     * @return promise of resumed job
     */
    Promise<TrainingJob> resumeTraining(String jobId);

    /**
     * Cancel a training job.
     *
     * @param jobId job identifier
     * @return promise of cancelled job
     */
    Promise<TrainingJob> cancelTraining(String jobId);

    /**
     * Get training metrics.
     *
     * @param jobId job identifier
     * @return promise of training metrics
     */
    Promise<TrainingMetrics> getMetrics(String jobId);

    /**
     * List training jobs.
     *
     * @param tenantId tenant identifier
     * @param status filter by status
     * @return promise of job list
     */
    Promise<List<TrainingJob>> listJobs(String tenantId, TrainingStatus.Status status);

    /**
     * Training configuration.
     */
    record TrainingConfig(
        String tenantId,
        String modelId,
        String datasetId,
        int epochs,
        int batchSize,
        double learningRate,
        String optimizer,
        Map<String, Object> hyperparameters,
        String checkpointPath,
        Duration maxDuration,
        int earlyStoppingPatience
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String tenantId;
            private String modelId;
            private String datasetId;
            private int epochs = 10;
            private int batchSize = 32;
            private double learningRate = 0.001;
            private String optimizer = "adam";
            private Map<String, Object> hyperparameters = Map.of();
            private String checkpointPath;
            private Duration maxDuration = Duration.ofHours(24);
            private int earlyStoppingPatience = 5;

            public Builder tenantId(String tenantId) {
                this.tenantId = tenantId;
                return this;
            }

            public Builder modelId(String modelId) {
                this.modelId = modelId;
                return this;
            }

            public Builder datasetId(String datasetId) {
                this.datasetId = datasetId;
                return this;
            }

            public Builder epochs(int epochs) {
                this.epochs = epochs;
                return this;
            }

            public Builder batchSize(int batchSize) {
                this.batchSize = batchSize;
                return this;
            }

            public Builder learningRate(double learningRate) {
                this.learningRate = learningRate;
                return this;
            }

            public Builder optimizer(String optimizer) {
                this.optimizer = optimizer;
                return this;
            }

            public Builder hyperparameters(Map<String, Object> hyperparameters) {
                this.hyperparameters = hyperparameters;
                return this;
            }

            public Builder checkpointPath(String checkpointPath) {
                this.checkpointPath = checkpointPath;
                return this;
            }

            public Builder maxDuration(Duration maxDuration) {
                this.maxDuration = maxDuration;
                return this;
            }

            public Builder earlyStoppingPatience(int patience) {
                this.earlyStoppingPatience = patience;
                return this;
            }

            public TrainingConfig build() {
                return new TrainingConfig(tenantId, modelId, datasetId, epochs, batchSize,
                    learningRate, optimizer, hyperparameters, checkpointPath, maxDuration, earlyStoppingPatience);
            }
        }
    }

    /**
     * Training job.
     */
    record TrainingJob(
        String id,
        String tenantId,
        String modelId,
        TrainingStatus.Status status,
        long startTime,
        Long endTime,
        TrainingConfig config
    ) {}

    /**
     * Training status.
     */
    record TrainingStatus(
        String jobId,
        Status status,
        int currentEpoch,
        int totalEpochs,
        double progressPercent,
        Duration elapsedTime,
        Duration estimatedRemainingTime,
        String currentStage,
        List<String> logs
    ) {
        public enum Status {
            PENDING, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED
        }
    }

    /**
     * Training metrics.
     */
    record TrainingMetrics(
        String jobId,
        int currentEpoch,
        double trainLoss,
        double validationLoss,
        double trainAccuracy,
        double validationAccuracy,
        double learningRate,
        double gradientNorm,
        long samplesProcessed,
        long samplesPerSecond,
        List<EpochMetrics> epochHistory
    ) {
        public boolean isImproving() {
            if (epochHistory.size() < 2) return true;
            EpochMetrics last = epochHistory.get(epochHistory.size() - 1);
            EpochMetrics prev = epochHistory.get(epochHistory.size() - 2);
            return last.validationLoss() < prev.validationLoss();
        }
    }

    /**
     * Epoch metrics.
     */
    record EpochMetrics(
        int epoch,
        double trainLoss,
        double validationLoss,
        double trainAccuracy,
        double validationAccuracy,
        Duration duration
    ) {}
}
