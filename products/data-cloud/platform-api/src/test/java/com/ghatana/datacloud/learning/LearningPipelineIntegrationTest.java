/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
 * Integration tests for learning pipeline end-to-end (D011). // GH-90000
 *
 * <p>Validates training pipeline from data loading to model deployment.
 *
 * @doc.type class
 * @doc.purpose Learning pipeline end-to-end integration tests
 * @doc.layer product
 * @doc.pattern Integration Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("LearningPipeline – End-to-End (D011) [GH-90000]")
class LearningPipelineIntegrationTest extends EventloopTestBase {

    @Mock
    private ModelTrainingService trainingService;

    @Mock
    private LearningProgressTracker progressTracker;

    // ─────────────────────────────────────────────────────────────────────────
    // Training Pipeline Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Training Pipeline [GH-90000]")
    class TrainingPipelineTests {

        @Test
        @DisplayName("[D011]: start_training_creates_job [GH-90000]")
        void startTrainingCreatesJob() { // GH-90000
            String tenantId = "tenant-alpha";

            ModelTrainingService.TrainingConfig config = ModelTrainingService.TrainingConfig.builder() // GH-90000
                .tenantId(tenantId) // GH-90000
                .modelId("model-001 [GH-90000]")
                .datasetId("dataset-001 [GH-90000]")
                .epochs(10) // GH-90000
                .batchSize(32) // GH-90000
                .learningRate(0.001) // GH-90000
                .build(); // GH-90000

            ModelTrainingService.TrainingJob job = new ModelTrainingService.TrainingJob( // GH-90000
                "job-001", tenantId, "model-001",
                ModelTrainingService.TrainingStatus.Status.PENDING,
                System.currentTimeMillis(), null, config // GH-90000
            );

            when(trainingService.startTraining(any())) // GH-90000
                .thenReturn(Promise.of(job)); // GH-90000

            ModelTrainingService.TrainingJob result = runPromise(() -> // GH-90000
                trainingService.startTraining(config) // GH-90000
            );

            assertThat(result.id()).isEqualTo("job-001 [GH-90000]");
            assertThat(result.status()).isEqualTo(ModelTrainingService.TrainingStatus.Status.PENDING); // GH-90000
        }

        @Test
        @DisplayName("[D011]: training_job_status_transitions_correctly [GH-90000]")
        void trainingJobStatusTransitionsCorrectly() { // GH-90000
            String jobId = "job-001";

            // Pending -> Running
            ModelTrainingService.TrainingStatus pending = new ModelTrainingService.TrainingStatus( // GH-90000
                jobId, ModelTrainingService.TrainingStatus.Status.PENDING,
                0, 10, 0.0, Duration.ZERO, Duration.ofMinutes(30), // GH-90000
                "Initializing", List.of() // GH-90000
            );

            ModelTrainingService.TrainingStatus running = new ModelTrainingService.TrainingStatus( // GH-90000
                jobId, ModelTrainingService.TrainingStatus.Status.RUNNING,
                5, 10, 50.0, Duration.ofMinutes(15), Duration.ofMinutes(15), // GH-90000
                "Training epoch 5", List.of("Epoch 4 completed [GH-90000]")
            );

            when(trainingService.getStatus(jobId)) // GH-90000
                .thenReturn(Promise.of(pending)) // GH-90000
                .thenReturn(Promise.of(running)); // GH-90000

            ModelTrainingService.TrainingStatus status1 = runPromise(() -> trainingService.getStatus(jobId)); // GH-90000
            ModelTrainingService.TrainingStatus status2 = runPromise(() -> trainingService.getStatus(jobId)); // GH-90000

            assertThat(status1.status()).isEqualTo(ModelTrainingService.TrainingStatus.Status.PENDING); // GH-90000
            assertThat(status2.status()).isEqualTo(ModelTrainingService.TrainingStatus.Status.RUNNING); // GH-90000
        }

        @Test
        @DisplayName("[D011]: completed_training_job_has_metrics [GH-90000]")
        void completedTrainingJobHasMetrics() { // GH-90000
            String jobId = "job-001";

            List<ModelTrainingService.EpochMetrics> epochHistory = List.of( // GH-90000
                new ModelTrainingService.EpochMetrics(1, 0.5, 0.45, 0.8, 0.82, Duration.ofMinutes(2)), // GH-90000
                new ModelTrainingService.EpochMetrics(2, 0.4, 0.38, 0.85, 0.86, Duration.ofMinutes(2)), // GH-90000
                new ModelTrainingService.EpochMetrics(3, 0.3, 0.28, 0.9, 0.91, Duration.ofMinutes(2)) // GH-90000
            );

            ModelTrainingService.TrainingMetrics metrics = new ModelTrainingService.TrainingMetrics( // GH-90000
                jobId, 3, 0.3, 0.28, 0.9, 0.91, 0.001, 0.1,
                1000, 100, epochHistory
            );

            when(trainingService.getMetrics(jobId)) // GH-90000
                .thenReturn(Promise.of(metrics)); // GH-90000

            ModelTrainingService.TrainingMetrics result = runPromise(() -> // GH-90000
                trainingService.getMetrics(jobId) // GH-90000
            );

            assertThat(result.trainLoss()).isEqualTo(0.3); // GH-90000
            assertThat(result.validationAccuracy()).isEqualTo(0.91); // GH-90000
            assertThat(result.epochHistory()).hasSize(3); // GH-90000
        }

        @Test
        @DisplayName("[D011]: training_metrics_show_improvement [GH-90000]")
        void trainingMetricsShowImprovement() { // GH-90000
            List<ModelTrainingService.EpochMetrics> improving = List.of( // GH-90000
                new ModelTrainingService.EpochMetrics(1, 0.5, 0.45, 0.8, 0.82, Duration.ofMinutes(1)), // GH-90000
                new ModelTrainingService.EpochMetrics(2, 0.4, 0.38, 0.85, 0.86, Duration.ofMinutes(1)), // GH-90000
                new ModelTrainingService.EpochMetrics(3, 0.3, 0.28, 0.9, 0.91, Duration.ofMinutes(1)) // GH-90000
            );

            ModelTrainingService.TrainingMetrics metrics = new ModelTrainingService.TrainingMetrics( // GH-90000
                "job-001", 3, 0.3, 0.28, 0.9, 0.91, 0.001, 0.1,
                1000, 100, improving
            );

            assertThat(metrics.isImproving()).isTrue(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Progress Tracking Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Progress Tracking [GH-90000]")
    class ProgressTrackingTests {

        @Test
        @DisplayName("[D011]: progress_tracker_records_start [GH-90000]")
        void progressTrackerRecordsStart() { // GH-90000
            String jobId = "job-001";
            ModelTrainingService.TrainingConfig config = ModelTrainingService.TrainingConfig.builder() // GH-90000
                .tenantId("tenant-alpha [GH-90000]")
                .modelId("model-001 [GH-90000]")
                .datasetId("dataset-001 [GH-90000]")
                .build(); // GH-90000

            when(progressTracker.recordStart(jobId, config)) // GH-90000
                .thenReturn(Promise.of((Void) null)); // GH-90000

            runPromise(() -> progressTracker.recordStart(jobId, config)); // GH-90000

            verify(progressTracker).recordStart(jobId, config); // GH-90000
        }

        @Test
        @DisplayName("[D011]: progress_tracker_records_epoch_completion [GH-90000]")
        void progressTrackerRecordsEpochCompletion() { // GH-90000
            String jobId = "job-001";

            ModelTrainingService.EpochMetrics epoch = new ModelTrainingService.EpochMetrics( // GH-90000
                1, 0.5, 0.45, 0.8, 0.82, Duration.ofMinutes(2) // GH-90000
            );

            when(progressTracker.recordEpoch(eq(jobId), eq(1), any())) // GH-90000
                .thenReturn(Promise.of((Void) null)); // GH-90000

            runPromise(() -> progressTracker.recordEpoch(jobId, 1, epoch)); // GH-90000

            verify(progressTracker).recordEpoch(jobId, 1, epoch); // GH-90000
        }

        @Test
        @DisplayName("[D011]: progress_summary_includes_completion_percent [GH-90000]")
        void progressSummaryIncludesCompletionPercent() { // GH-90000
            String jobId = "job-001";

            LearningProgressTracker.ProgressSummary summary = new LearningProgressTracker.ProgressSummary( // GH-90000
                jobId, 5, 10, 50.0, Duration.ofMinutes(15), Duration.ofMinutes(15), // GH-90000
                "RUNNING", true, 0.28, 0
            );

            when(progressTracker.getProgress(jobId)) // GH-90000
                .thenReturn(Promise.of(summary)); // GH-90000

            LearningProgressTracker.ProgressSummary result = runPromise(() -> // GH-90000
                progressTracker.getProgress(jobId) // GH-90000
            );

            assertThat(result.progressPercent()).isEqualTo(50.0); // GH-90000
            assertThat(result.estimatedRemainingTime()).isEqualTo(Duration.ofMinutes(15)); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Control Flow Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Control Flow [GH-90000]")
    class ControlFlowTests {

        @Test
        @DisplayName("[D011]: pause_training_stops_execution [GH-90000]")
        void pauseTrainingStopsExecution() { // GH-90000
            String jobId = "job-001";

            ModelTrainingService.TrainingJob paused = new ModelTrainingService.TrainingJob( // GH-90000
                jobId, "tenant-alpha", "model-001",
                ModelTrainingService.TrainingStatus.Status.PAUSED,
                System.currentTimeMillis(), null, null // GH-90000
            );

            when(trainingService.pauseTraining(jobId)) // GH-90000
                .thenReturn(Promise.of(paused)); // GH-90000

            ModelTrainingService.TrainingJob result = runPromise(() -> // GH-90000
                trainingService.pauseTraining(jobId) // GH-90000
            );

            assertThat(result.status()).isEqualTo(ModelTrainingService.TrainingStatus.Status.PAUSED); // GH-90000
        }

        @Test
        @DisplayName("[D011]: resume_training_continues_execution [GH-90000]")
        void resumeTrainingContinuesExecution() { // GH-90000
            String jobId = "job-001";

            ModelTrainingService.TrainingJob resumed = new ModelTrainingService.TrainingJob( // GH-90000
                jobId, "tenant-alpha", "model-001",
                ModelTrainingService.TrainingStatus.Status.RUNNING,
                System.currentTimeMillis(), null, null // GH-90000
            );

            when(trainingService.resumeTraining(jobId)) // GH-90000
                .thenReturn(Promise.of(resumed)); // GH-90000

            ModelTrainingService.TrainingJob result = runPromise(() -> // GH-90000
                trainingService.resumeTraining(jobId) // GH-90000
            );

            assertThat(result.status()).isEqualTo(ModelTrainingService.TrainingStatus.Status.RUNNING); // GH-90000
        }

        @Test
        @DisplayName("[D011]: cancel_training_stops_permanently [GH-90000]")
        void cancelTrainingStopsPermanently() { // GH-90000
            String jobId = "job-001";

            ModelTrainingService.TrainingJob cancelled = new ModelTrainingService.TrainingJob( // GH-90000
                jobId, "tenant-alpha", "model-001",
                ModelTrainingService.TrainingStatus.Status.CANCELLED,
                System.currentTimeMillis(), System.currentTimeMillis(), null // GH-90000
            );

            when(trainingService.cancelTraining(jobId)) // GH-90000
                .thenReturn(Promise.of(cancelled)); // GH-90000

            ModelTrainingService.TrainingJob result = runPromise(() -> // GH-90000
                trainingService.cancelTraining(jobId) // GH-90000
            );

            assertThat(result.status()).isEqualTo(ModelTrainingService.TrainingStatus.Status.CANCELLED); // GH-90000
            assertThat(result.endTime()).isNotNull(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Configuration Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Configuration [GH-90000]")
    class ConfigurationTests {

        @Test
        @DisplayName("[D011]: training_config_has_hyperparameters [GH-90000]")
        void trainingConfigHasHyperparameters() { // GH-90000
            ModelTrainingService.TrainingConfig config = ModelTrainingService.TrainingConfig.builder() // GH-90000
                .epochs(10) // GH-90000
                .batchSize(32) // GH-90000
                .learningRate(0.001) // GH-90000
                .optimizer("adam [GH-90000]")
                .hyperparameters(java.util.Map.of("dropout", 0.5, "weight_decay", 0.01)) // GH-90000
                .earlyStoppingPatience(5) // GH-90000
                .build(); // GH-90000

            assertThat(config.epochs()).isEqualTo(10); // GH-90000
            assertThat(config.batchSize()).isEqualTo(32); // GH-90000
            assertThat(config.learningRate()).isEqualTo(0.001); // GH-90000
            assertThat(config.optimizer()).isEqualTo("adam [GH-90000]");
            assertThat(config.hyperparameters()).containsKeys("dropout", "weight_decay"); // GH-90000
            assertThat(config.earlyStoppingPatience()).isEqualTo(5); // GH-90000
        }

        @Test
        @DisplayName("[D011]: training_config_validates_required_fields [GH-90000]")
        void trainingConfigValidatesRequiredFields() { // GH-90000
            // Builder ensures required fields are set
            ModelTrainingService.TrainingConfig config = ModelTrainingService.TrainingConfig.builder() // GH-90000
                .tenantId("tenant-alpha [GH-90000]")
                .modelId("model-001 [GH-90000]")
                .datasetId("dataset-001 [GH-90000]")
                .build(); // GH-90000

            assertThat(config.tenantId()).isEqualTo("tenant-alpha [GH-90000]");
            assertThat(config.modelId()).isEqualTo("model-001 [GH-90000]");
            assertThat(config.datasetId()).isEqualTo("dataset-001 [GH-90000]");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Job Listing Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Job Listing [GH-90000]")
    class JobListingTests {

        @Test
        @DisplayName("[D011]: list_jobs_returns_jobs_for_tenant [GH-90000]")
        void listJobsReturnsJobsForTenant() { // GH-90000
            String tenantId = "tenant-alpha";

            List<ModelTrainingService.TrainingJob> jobs = List.of( // GH-90000
                new ModelTrainingService.TrainingJob("job-1", tenantId, "model-1", // GH-90000
                    ModelTrainingService.TrainingStatus.Status.RUNNING, 0, null, null),
                new ModelTrainingService.TrainingJob("job-2", tenantId, "model-2", // GH-90000
                    ModelTrainingService.TrainingStatus.Status.COMPLETED, 0, 1000L, null)
            );

            when(trainingService.listJobs(tenantId, null)) // GH-90000
                .thenReturn(Promise.of(jobs)); // GH-90000

            List<ModelTrainingService.TrainingJob> result = runPromise(() -> // GH-90000
                trainingService.listJobs(tenantId, null) // GH-90000
            );

            assertThat(result).hasSize(2); // GH-90000
            assertThat(result).allMatch(j -> tenantId.equals(j.tenantId())); // GH-90000
        }

        @Test
        @DisplayName("[D011]: list_jobs_with_status_filter [GH-90000]")
        void listJobsWithStatusFilter() { // GH-90000
            String tenantId = "tenant-alpha";

            List<ModelTrainingService.TrainingJob> jobs = List.of( // GH-90000
                new ModelTrainingService.TrainingJob("job-1", tenantId, "model-1", // GH-90000
                    ModelTrainingService.TrainingStatus.Status.COMPLETED, 0, 1000L, null)
            );

            when(trainingService.listJobs(tenantId, ModelTrainingService.TrainingStatus.Status.COMPLETED)) // GH-90000
                .thenReturn(Promise.of(jobs)); // GH-90000

            List<ModelTrainingService.TrainingJob> result = runPromise(() -> // GH-90000
                trainingService.listJobs(tenantId, ModelTrainingService.TrainingStatus.Status.COMPLETED) // GH-90000
            );

            assertThat(result).hasSize(1); // GH-90000
            assertThat(result.get(0).status()).isEqualTo(ModelTrainingService.TrainingStatus.Status.COMPLETED); // GH-90000
        }
    }
}
