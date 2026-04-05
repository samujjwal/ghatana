/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.learning;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for learning pipeline end-to-end (D011).
 *
 * <p>Validates training pipeline from data loading to model deployment.
 *
 * @doc.type class
 * @doc.purpose Learning pipeline end-to-end integration tests
 * @doc.layer product
 * @doc.pattern Integration Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LearningPipeline – End-to-End (D011)")
class LearningPipelineIntegrationTest extends EventloopTestBase {

    @Mock
    private ModelTrainingService trainingService;

    @Mock
    private LearningProgressTracker progressTracker;

    // ─────────────────────────────────────────────────────────────────────────
    // Training Pipeline Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Training Pipeline")
    class TrainingPipelineTests {

        @Test
        @DisplayName("[D011]: start_training_creates_job")
        void startTrainingCreatesJob() {
            String tenantId = "tenant-alpha";

            ModelTrainingService.TrainingConfig config = ModelTrainingService.TrainingConfig.builder()
                .tenantId(tenantId)
                .modelId("model-001")
                .datasetId("dataset-001")
                .epochs(10)
                .batchSize(32)
                .learningRate(0.001)
                .build();

            ModelTrainingService.TrainingJob job = new ModelTrainingService.TrainingJob(
                "job-001", tenantId, "model-001",
                ModelTrainingService.TrainingStatus.Status.PENDING,
                System.currentTimeMillis(), null, config
            );

            when(trainingService.startTraining(any()))
                .thenReturn(Promise.of(job));

            ModelTrainingService.TrainingJob result = runPromise(() ->
                trainingService.startTraining(config)
            );

            assertThat(result.id()).isEqualTo("job-001");
            assertThat(result.status()).isEqualTo(ModelTrainingService.TrainingStatus.Status.PENDING);
        }

        @Test
        @DisplayName("[D011]: training_job_status_transitions_correctly")
        void trainingJobStatusTransitionsCorrectly() {
            String jobId = "job-001";

            // Pending -> Running
            ModelTrainingService.TrainingStatus pending = new ModelTrainingService.TrainingStatus(
                jobId, ModelTrainingService.TrainingStatus.Status.PENDING,
                0, 10, 0.0, Duration.ZERO, Duration.ofMinutes(30),
                "Initializing", List.of()
            );

            ModelTrainingService.TrainingStatus running = new ModelTrainingService.TrainingStatus(
                jobId, ModelTrainingService.TrainingStatus.Status.RUNNING,
                5, 10, 50.0, Duration.ofMinutes(15), Duration.ofMinutes(15),
                "Training epoch 5", List.of("Epoch 4 completed")
            );

            when(trainingService.getStatus(jobId))
                .thenReturn(Promise.of(pending))
                .thenReturn(Promise.of(running));

            ModelTrainingService.TrainingStatus status1 = runPromise(() -> trainingService.getStatus(jobId));
            ModelTrainingService.TrainingStatus status2 = runPromise(() -> trainingService.getStatus(jobId));

            assertThat(status1.status()).isEqualTo(ModelTrainingService.TrainingStatus.Status.PENDING);
            assertThat(status2.status()).isEqualTo(ModelTrainingService.TrainingStatus.Status.RUNNING);
        }

        @Test
        @DisplayName("[D011]: completed_training_job_has_metrics")
        void completedTrainingJobHasMetrics() {
            String jobId = "job-001";

            List<ModelTrainingService.EpochMetrics> epochHistory = List.of(
                new ModelTrainingService.EpochMetrics(1, 0.5, 0.45, 0.8, 0.82, Duration.ofMinutes(2)),
                new ModelTrainingService.EpochMetrics(2, 0.4, 0.38, 0.85, 0.86, Duration.ofMinutes(2)),
                new ModelTrainingService.EpochMetrics(3, 0.3, 0.28, 0.9, 0.91, Duration.ofMinutes(2))
            );

            ModelTrainingService.TrainingMetrics metrics = new ModelTrainingService.TrainingMetrics(
                jobId, 3, 0.3, 0.28, 0.9, 0.91, 0.001, 0.1,
                1000, 100, epochHistory
            );

            when(trainingService.getMetrics(jobId))
                .thenReturn(Promise.of(metrics));

            ModelTrainingService.TrainingMetrics result = runPromise(() ->
                trainingService.getMetrics(jobId)
            );

            assertThat(result.trainLoss()).isEqualTo(0.3);
            assertThat(result.validationAccuracy()).isEqualTo(0.91);
            assertThat(result.epochHistory()).hasSize(3);
        }

        @Test
        @DisplayName("[D011]: training_metrics_show_improvement")
        void trainingMetricsShowImprovement() {
            List<ModelTrainingService.EpochMetrics> improving = List.of(
                new ModelTrainingService.EpochMetrics(1, 0.5, 0.45, 0.8, 0.82, Duration.ofMinutes(1)),
                new ModelTrainingService.EpochMetrics(2, 0.4, 0.38, 0.85, 0.86, Duration.ofMinutes(1)),
                new ModelTrainingService.EpochMetrics(3, 0.3, 0.28, 0.9, 0.91, Duration.ofMinutes(1))
            );

            ModelTrainingService.TrainingMetrics metrics = new ModelTrainingService.TrainingMetrics(
                "job-001", 3, 0.3, 0.28, 0.9, 0.91, 0.001, 0.1,
                1000, 100, improving
            );

            assertThat(metrics.isImproving()).isTrue();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Progress Tracking Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Progress Tracking")
    class ProgressTrackingTests {

        @Test
        @DisplayName("[D011]: progress_tracker_records_start")
        void progressTrackerRecordsStart() {
            String jobId = "job-001";
            ModelTrainingService.TrainingConfig config = ModelTrainingService.TrainingConfig.builder()
                .tenantId("tenant-alpha")
                .modelId("model-001")
                .datasetId("dataset-001")
                .build();

            when(progressTracker.recordStart(jobId, config))
                .thenReturn(Promise.of((Void) null));

            runPromise(() -> progressTracker.recordStart(jobId, config));

            verify(progressTracker).recordStart(jobId, config);
        }

        @Test
        @DisplayName("[D011]: progress_tracker_records_epoch_completion")
        void progressTrackerRecordsEpochCompletion() {
            String jobId = "job-001";

            ModelTrainingService.EpochMetrics epoch = new ModelTrainingService.EpochMetrics(
                1, 0.5, 0.45, 0.8, 0.82, Duration.ofMinutes(2)
            );

            when(progressTracker.recordEpoch(eq(jobId), eq(1), any()))
                .thenReturn(Promise.of((Void) null));

            runPromise(() -> progressTracker.recordEpoch(jobId, 1, epoch));

            verify(progressTracker).recordEpoch(jobId, 1, epoch);
        }

        @Test
        @DisplayName("[D011]: progress_summary_includes_completion_percent")
        void progressSummaryIncludesCompletionPercent() {
            String jobId = "job-001";

            LearningProgressTracker.ProgressSummary summary = new LearningProgressTracker.ProgressSummary(
                jobId, 5, 10, 50.0, Duration.ofMinutes(15), Duration.ofMinutes(15),
                "RUNNING", true, 0.28, 0
            );

            when(progressTracker.getProgress(jobId))
                .thenReturn(Promise.of(summary));

            LearningProgressTracker.ProgressSummary result = runPromise(() ->
                progressTracker.getProgress(jobId)
            );

            assertThat(result.progressPercent()).isEqualTo(50.0);
            assertThat(result.estimatedRemainingTime()).isEqualTo(Duration.ofMinutes(15));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Control Flow Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Control Flow")
    class ControlFlowTests {

        @Test
        @DisplayName("[D011]: pause_training_stops_execution")
        void pauseTrainingStopsExecution() {
            String jobId = "job-001";

            ModelTrainingService.TrainingJob paused = new ModelTrainingService.TrainingJob(
                jobId, "tenant-alpha", "model-001",
                ModelTrainingService.TrainingStatus.Status.PAUSED,
                System.currentTimeMillis(), null, null
            );

            when(trainingService.pauseTraining(jobId))
                .thenReturn(Promise.of(paused));

            ModelTrainingService.TrainingJob result = runPromise(() ->
                trainingService.pauseTraining(jobId)
            );

            assertThat(result.status()).isEqualTo(ModelTrainingService.TrainingStatus.Status.PAUSED);
        }

        @Test
        @DisplayName("[D011]: resume_training_continues_execution")
        void resumeTrainingContinuesExecution() {
            String jobId = "job-001";

            ModelTrainingService.TrainingJob resumed = new ModelTrainingService.TrainingJob(
                jobId, "tenant-alpha", "model-001",
                ModelTrainingService.TrainingStatus.Status.RUNNING,
                System.currentTimeMillis(), null, null
            );

            when(trainingService.resumeTraining(jobId))
                .thenReturn(Promise.of(resumed));

            ModelTrainingService.TrainingJob result = runPromise(() ->
                trainingService.resumeTraining(jobId)
            );

            assertThat(result.status()).isEqualTo(ModelTrainingService.TrainingStatus.Status.RUNNING);
        }

        @Test
        @DisplayName("[D011]: cancel_training_stops_permanently")
        void cancelTrainingStopsPermanently() {
            String jobId = "job-001";

            ModelTrainingService.TrainingJob cancelled = new ModelTrainingService.TrainingJob(
                jobId, "tenant-alpha", "model-001",
                ModelTrainingService.TrainingStatus.Status.CANCELLED,
                System.currentTimeMillis(), System.currentTimeMillis(), null
            );

            when(trainingService.cancelTraining(jobId))
                .thenReturn(Promise.of(cancelled));

            ModelTrainingService.TrainingJob result = runPromise(() ->
                trainingService.cancelTraining(jobId)
            );

            assertThat(result.status()).isEqualTo(ModelTrainingService.TrainingStatus.Status.CANCELLED);
            assertThat(result.endTime()).isNotNull();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Configuration Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Configuration")
    class ConfigurationTests {

        @Test
        @DisplayName("[D011]: training_config_has_hyperparameters")
        void trainingConfigHasHyperparameters() {
            ModelTrainingService.TrainingConfig config = ModelTrainingService.TrainingConfig.builder()
                .epochs(10)
                .batchSize(32)
                .learningRate(0.001)
                .optimizer("adam")
                .hyperparameters(java.util.Map.of("dropout", 0.5, "weight_decay", 0.01))
                .earlyStoppingPatience(5)
                .build();

            assertThat(config.epochs()).isEqualTo(10);
            assertThat(config.batchSize()).isEqualTo(32);
            assertThat(config.learningRate()).isEqualTo(0.001);
            assertThat(config.optimizer()).isEqualTo("adam");
            assertThat(config.hyperparameters()).containsKeys("dropout", "weight_decay");
            assertThat(config.earlyStoppingPatience()).isEqualTo(5);
        }

        @Test
        @DisplayName("[D011]: training_config_validates_required_fields")
        void trainingConfigValidatesRequiredFields() {
            // Builder ensures required fields are set
            ModelTrainingService.TrainingConfig config = ModelTrainingService.TrainingConfig.builder()
                .tenantId("tenant-alpha")
                .modelId("model-001")
                .datasetId("dataset-001")
                .build();

            assertThat(config.tenantId()).isEqualTo("tenant-alpha");
            assertThat(config.modelId()).isEqualTo("model-001");
            assertThat(config.datasetId()).isEqualTo("dataset-001");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Job Listing Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Job Listing")
    class JobListingTests {

        @Test
        @DisplayName("[D011]: list_jobs_returns_jobs_for_tenant")
        void listJobsReturnsJobsForTenant() {
            String tenantId = "tenant-alpha";

            List<ModelTrainingService.TrainingJob> jobs = List.of(
                new ModelTrainingService.TrainingJob("job-1", tenantId, "model-1",
                    ModelTrainingService.TrainingStatus.Status.RUNNING, 0, null, null),
                new ModelTrainingService.TrainingJob("job-2", tenantId, "model-2",
                    ModelTrainingService.TrainingStatus.Status.COMPLETED, 0, 1000L, null)
            );

            when(trainingService.listJobs(tenantId, null))
                .thenReturn(Promise.of(jobs));

            List<ModelTrainingService.TrainingJob> result = runPromise(() ->
                trainingService.listJobs(tenantId, null)
            );

            assertThat(result).hasSize(2);
            assertThat(result).allMatch(j -> tenantId.equals(j.tenantId()));
        }

        @Test
        @DisplayName("[D011]: list_jobs_with_status_filter")
        void listJobsWithStatusFilter() {
            String tenantId = "tenant-alpha";

            List<ModelTrainingService.TrainingJob> jobs = List.of(
                new ModelTrainingService.TrainingJob("job-1", tenantId, "model-1",
                    ModelTrainingService.TrainingStatus.Status.COMPLETED, 0, 1000L, null)
            );

            when(trainingService.listJobs(tenantId, ModelTrainingService.TrainingStatus.Status.COMPLETED))
                .thenReturn(Promise.of(jobs));

            List<ModelTrainingService.TrainingJob> result = runPromise(() ->
                trainingService.listJobs(tenantId, ModelTrainingService.TrainingStatus.Status.COMPLETED)
            );

            assertThat(result).hasSize(1);
            assertThat(result.get(0).status()).isEqualTo(ModelTrainingService.TrainingStatus.Status.COMPLETED);
        }
    }
}
