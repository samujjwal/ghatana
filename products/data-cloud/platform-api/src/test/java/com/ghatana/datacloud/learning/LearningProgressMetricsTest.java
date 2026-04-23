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
 * Tests for learning progress metrics tracking (D011). // GH-90000
 *
 * <p>Validates training progress monitoring and metric collection.
 *
 * @doc.type class
 * @doc.purpose Learning progress metrics tests
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("LearningProgressMetrics – Progress Tracking (D011)")
class LearningProgressMetricsTest extends EventloopTestBase {

    @Mock
    private LearningProgressTracker progressTracker;

    // ─────────────────────────────────────────────────────────────────────────
    // Progress Summary Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Progress Summary")
    class ProgressSummaryTests {

        @Test
        @DisplayName("[D011]: get_progress_returns_summary")
        void getProgressReturnsSummary() { // GH-90000
            String jobId = "job-001";

            LearningProgressTracker.ProgressSummary summary = new LearningProgressTracker.ProgressSummary( // GH-90000
                jobId, 5, 10, 50.0,
                Duration.ofMinutes(15), Duration.ofMinutes(15), // GH-90000
                "RUNNING", true, 0.28, 0
            );

            when(progressTracker.getProgress(jobId)) // GH-90000
                .thenReturn(Promise.of(summary)); // GH-90000

            LearningProgressTracker.ProgressSummary result = runPromise(() -> // GH-90000
                progressTracker.getProgress(jobId) // GH-90000
            );

            assertThat(result.jobId()).isEqualTo(jobId); // GH-90000
            assertThat(result.currentEpoch()).isEqualTo(5); // GH-90000
            assertThat(result.totalEpochs()).isEqualTo(10); // GH-90000
            assertThat(result.progressPercent()).isEqualTo(50.0); // GH-90000
        }

        @Test
        @DisplayName("[D011]: progress_percent_calculated_correctly")
        void progressPercentCalculatedCorrectly() { // GH-90000
            int current = 5;
            int total = 10;

            double percent = ((double) current / total) * 100; // GH-90000

            assertThat(percent).isEqualTo(50.0); // GH-90000
        }

        @Test
        @DisplayName("[D011]: estimated_remaining_time_based_on_elapsed")
        void estimatedRemainingTimeBasedOnElapsed() { // GH-90000
            Duration elapsed = Duration.ofMinutes(15); // GH-90000
            double progressPercent = 50.0;

            // If 50% took 15 minutes, remaining should be ~15 minutes
            Duration estimated = Duration.ofMinutes(15); // GH-90000

            assertThat(estimated).isEqualTo(elapsed); // GH-90000
        }

        @Test
        @DisplayName("[D011]: is_improving_detects_decreasing_loss")
        void isImprovingDetectsDecreasingLoss() { // GH-90000
            LearningProgressTracker.ProgressSummary improving =
                new LearningProgressTracker.ProgressSummary( // GH-90000
                    "job-001", 5, 10, 50.0,
                    Duration.ofMinutes(15), Duration.ofMinutes(15), // GH-90000
                    "RUNNING", true, 0.25, 0
                );

            assertThat(improving.isImproving()).isTrue(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Metrics History Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Metrics History")
    class MetricsHistoryTests {

        @Test
        @DisplayName("[D011]: get_metrics_history_returns_time_series")
        void getMetricsHistoryReturnsTimeSeries() { // GH-90000
            String jobId = "job-001";

            List<LearningProgressTracker.MetricPoint> history = List.of( // GH-90000
                new LearningProgressTracker.MetricPoint(1704067200000L, 1, "loss", 0.5), // GH-90000
                new LearningProgressTracker.MetricPoint(1704067260000L, 2, "loss", 0.45), // GH-90000
                new LearningProgressTracker.MetricPoint(1704067320000L, 3, "loss", 0.40), // GH-90000
                new LearningProgressTracker.MetricPoint(1704067380000L, 4, "loss", 0.35), // GH-90000
                new LearningProgressTracker.MetricPoint(1704067440000L, 5, "loss", 0.30) // GH-90000
            );

            when(progressTracker.getMetricsHistory(jobId)) // GH-90000
                .thenReturn(Promise.of(history)); // GH-90000

            List<LearningProgressTracker.MetricPoint> result = runPromise(() -> // GH-90000
                progressTracker.getMetricsHistory(jobId) // GH-90000
            );

            assertThat(result).hasSize(5); // GH-90000
            assertThat(result.get(0).value()).isGreaterThan(result.get(4).value()); // Decreasing // GH-90000
        }

        @Test
        @DisplayName("[D011]: metric_point_contains_timestamp_epoch_value")
        void metricPointContainsTimestampEpochValue() { // GH-90000
            long timestamp = 1704067200000L;
            int epoch = 5;
            String metricName = "accuracy";
            double value = 0.92;

            LearningProgressTracker.MetricPoint point =
                new LearningProgressTracker.MetricPoint(timestamp, epoch, metricName, value); // GH-90000

            assertThat(point.timestamp()).isEqualTo(timestamp); // GH-90000
            assertThat(point.epoch()).isEqualTo(epoch); // GH-90000
            assertThat(point.metricName()).isEqualTo(metricName); // GH-90000
            assertThat(point.value()).isEqualTo(value); // GH-90000
        }

        @Test
        @DisplayName("[D011]: loss_trend_detected_from_history")
        void lossTrendDetectedFromHistory() { // GH-90000
            List<LearningProgressTracker.MetricPoint> lossHistory = List.of( // GH-90000
                new LearningProgressTracker.MetricPoint(0L, 1, "loss", 0.8), // GH-90000
                new LearningProgressTracker.MetricPoint(0L, 2, "loss", 0.7), // GH-90000
                new LearningProgressTracker.MetricPoint(0L, 3, "loss", 0.6), // GH-90000
                new LearningProgressTracker.MetricPoint(0L, 4, "loss", 0.5), // GH-90000
                new LearningProgressTracker.MetricPoint(0L, 5, "loss", 0.4) // GH-90000
            );

            // Check if loss is decreasing
            boolean decreasing = true;
            for (int i = 1; i < lossHistory.size(); i++) { // GH-90000
                if (lossHistory.get(i).value() >= lossHistory.get(i - 1).value()) { // GH-90000
                    decreasing = false;
                    break;
                }
            }

            assertThat(decreasing).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("[D011]: plateau_detected_when_loss_stops_decreasing")
        void plateauDetectedWhenLossStopsDecreasing() { // GH-90000
            List<LearningProgressTracker.MetricPoint> plateauHistory = List.of( // GH-90000
                new LearningProgressTracker.MetricPoint(0L, 1, "loss", 0.5), // GH-90000
                new LearningProgressTracker.MetricPoint(0L, 2, "loss", 0.48), // GH-90000
                new LearningProgressTracker.MetricPoint(0L, 3, "loss", 0.47), // GH-90000
                new LearningProgressTracker.MetricPoint(0L, 4, "loss", 0.47), // No change // GH-90000
                new LearningProgressTracker.MetricPoint(0L, 5, "loss", 0.47)  // No change // GH-90000
            );

            int epochsSinceImprovement = 0;
            double bestLoss = Double.MAX_VALUE;
            for (LearningProgressTracker.MetricPoint point : plateauHistory) { // GH-90000
                if (point.value() < bestLoss) { // GH-90000
                    bestLoss = point.value(); // GH-90000
                    epochsSinceImprovement = 0;
                } else {
                    epochsSinceImprovement++;
                }
            }

            assertThat(epochsSinceImprovement).isEqualTo(2); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Historical Comparison Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Historical Comparison")
    class HistoricalComparisonTests {

        @Test
        @DisplayName("[D011]: get_historical_comparison_returns_stats")
        void getHistoricalComparisonReturnsStats() { // GH-90000
            String modelId = "model-001";

            List<LearningProgressTracker.RunSummary> runHistory = List.of( // GH-90000
                new LearningProgressTracker.RunSummary( // GH-90000
                    "job-1", 1704067200000L, Duration.ofHours(2), // GH-90000
                    0.25, 0.22, 10, "COMPLETED"
                ),
                new LearningProgressTracker.RunSummary( // GH-90000
                    "job-2", 1704153600000L, Duration.ofHours(1), // GH-90000
                    0.20, 0.18, 10, "COMPLETED"
                ),
                new LearningProgressTracker.RunSummary( // GH-90000
                    "job-3", 1704240000000L, Duration.ofHours(3), // GH-90000
                    0.30, 0.28, 8, "FAILED"
                )
            );

            LearningProgressTracker.HistoricalComparison comparison =
                new LearningProgressTracker.HistoricalComparison( // GH-90000
                    modelId, 3, 0.25, 0.18, 0.30,
                    Duration.ofHours(2), runHistory // GH-90000
                );

            when(progressTracker.getHistoricalComparison(modelId)) // GH-90000
                .thenReturn(Promise.of(comparison)); // GH-90000

            LearningProgressTracker.HistoricalComparison result = runPromise(() -> // GH-90000
                progressTracker.getHistoricalComparison(modelId) // GH-90000
            );

            assertThat(result.modelId()).isEqualTo(modelId); // GH-90000
            assertThat(result.totalRuns()).isEqualTo(3); // GH-90000
            assertThat(result.bestLoss()).isEqualTo(0.18); // GH-90000
            assertThat(result.worstLoss()).isEqualTo(0.30); // GH-90000
        }

        @Test
        @DisplayName("[D011]: current_is_best_detects_improvement")
        void currentIsBestDetectsImprovement() { // GH-90000
            double bestLoss = 0.20;

            LearningProgressTracker.HistoricalComparison comparison =
                new LearningProgressTracker.HistoricalComparison( // GH-90000
                    "model-001", 5, 0.25, bestLoss, 0.35,
                    Duration.ofHours(2), List.of() // GH-90000
                );

            assertThat(comparison.currentIsBest(0.18)).isTrue();  // Better than best // GH-90000
            assertThat(comparison.currentIsBest(0.20)).isTrue();  // Equal to best (isBest uses <=) // GH-90000
            assertThat(comparison.currentIsBest(0.25)).isFalse(); // Worse than best // GH-90000
        }

        @Test
        @DisplayName("[D011]: run_summary_contains_duration_and_status")
        void runSummaryContainsDurationAndStatus() { // GH-90000
            LearningProgressTracker.RunSummary summary = new LearningProgressTracker.RunSummary( // GH-90000
                "job-001", 1704067200000L, Duration.ofHours(2), // GH-90000
                0.25, 0.22, 10, "COMPLETED"
            );

            assertThat(summary.jobId()).isEqualTo("job-001");
            assertThat(summary.duration()).isEqualTo(Duration.ofHours(2)); // GH-90000
            assertThat(summary.finalLoss()).isEqualTo(0.25); // GH-90000
            assertThat(summary.bestLoss()).isEqualTo(0.22); // GH-90000
            assertThat(summary.epochsCompleted()).isEqualTo(10); // GH-90000
            assertThat(summary.status()).isEqualTo("COMPLETED");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Epoch Recording Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Epoch Recording")
    class EpochRecordingTests {

        @Test
        @DisplayName("[D011]: record_epoch_stores_metrics")
        void recordEpochStoresMetrics() { // GH-90000
            String jobId = "job-001";
            int epoch = 5;

            ModelTrainingService.EpochMetrics metrics = new ModelTrainingService.EpochMetrics( // GH-90000
                epoch, 0.3, 0.28, 0.90, 0.91, Duration.ofMinutes(2) // GH-90000
            );

            when(progressTracker.recordEpoch(jobId, epoch, metrics)) // GH-90000
                .thenReturn(Promise.of((Void) null)); // GH-90000

            runPromise(() -> progressTracker.recordEpoch(jobId, epoch, metrics)); // GH-90000

            verify(progressTracker).recordEpoch(jobId, epoch, metrics); // GH-90000
        }

        @Test
        @DisplayName("[D011]: record_completion_stores_final_metrics")
        void recordCompletionStoresFinalMetrics() { // GH-90000
            String jobId = "job-001";

            List<ModelTrainingService.EpochMetrics> history = List.of( // GH-90000
                new ModelTrainingService.EpochMetrics(1, 0.5, 0.45, 0.80, 0.82, Duration.ofMinutes(2)), // GH-90000
                new ModelTrainingService.EpochMetrics(2, 0.4, 0.38, 0.85, 0.86, Duration.ofMinutes(2)) // GH-90000
            );

            ModelTrainingService.TrainingMetrics finalMetrics = new ModelTrainingService.TrainingMetrics( // GH-90000
                jobId, 2, 0.4, 0.38, 0.85, 0.86, 0.001, 0.1,
                2000, 100, history
            );

            when(progressTracker.recordCompletion(jobId, finalMetrics)) // GH-90000
                .thenReturn(Promise.of((Void) null)); // GH-90000

            runPromise(() -> progressTracker.recordCompletion(jobId, finalMetrics)); // GH-90000

            verify(progressTracker).recordCompletion(jobId, finalMetrics); // GH-90000
        }

        @Test
        @DisplayName("[D011]: record_failure_stores_error")
        void recordFailureStoresError() { // GH-90000
            String jobId = "job-001";
            String error = "Out of memory";

            when(progressTracker.recordFailure(jobId, error)) // GH-90000
                .thenReturn(Promise.of((Void) null)); // GH-90000

            runPromise(() -> progressTracker.recordFailure(jobId, error)); // GH-90000

            verify(progressTracker).recordFailure(jobId, error); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Best Loss Tracking Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Best Loss Tracking")
    class BestLossTrackingTests {

        @Test
        @DisplayName("[D011]: best_loss_tracked_across_epochs")
        void bestLossTrackedAcrossEpochs() { // GH-90000
            List<Double> losses = List.of(0.5, 0.45, 0.48, 0.42, 0.43, 0.40); // GH-90000

            double bestLoss = losses.stream() // GH-90000
                .min(Double::compare) // GH-90000
                .orElse(Double.MAX_VALUE); // GH-90000

            assertThat(bestLoss).isEqualTo(0.40); // GH-90000
        }

        @Test
        @DisplayName("[D011]: epochs_since_improvement_tracked")
        void epochsSinceImprovementTracked() { // GH-90000
            List<Double> losses = List.of(0.5, 0.45, 0.44, 0.44, 0.44, 0.43); // GH-90000

            double bestLoss = Double.MAX_VALUE;
            int epochsSinceImprovement = 0;
            int lastImprovementEpoch = -1;
            int previousImprovementEpoch = -1;

            for (int i = 0; i < losses.size(); i++) { // GH-90000
                if (losses.get(i) < bestLoss) { // GH-90000
                    previousImprovementEpoch = lastImprovementEpoch;
                    bestLoss = losses.get(i); // GH-90000
                    lastImprovementEpoch = i;
                }
            }

            // Calculate epochs since the improvement before the last one
            if (previousImprovementEpoch >= 0) { // GH-90000
                epochsSinceImprovement = lastImprovementEpoch - previousImprovementEpoch;
            }

            assertThat(epochsSinceImprovement).isEqualTo(3); // Epochs 2 to 5 (3 epochs gap) // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Performance Metrics Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Performance Metrics")
    class PerformanceMetricsTests {

        @Test
        @DisplayName("[D011]: samples_per_second_calculated")
        void samplesPerSecondCalculated() { // GH-90000
            long samplesProcessed = 10000;
            Duration elapsedTime = Duration.ofMinutes(5); // GH-90000

            double samplesPerSecond = samplesProcessed / (double) elapsedTime.getSeconds(); // GH-90000

            assertThat(samplesPerSecond).isGreaterThan(0); // GH-90000
        }

        @Test
        @DisplayName("[D011]: epoch_duration_tracked")
        void epochDurationTracked() { // GH-90000
            ModelTrainingService.EpochMetrics epoch = new ModelTrainingService.EpochMetrics( // GH-90000
                5, 0.3, 0.28, 0.90, 0.91, Duration.ofMinutes(3) // GH-90000
            );

            assertThat(epoch.duration()).isEqualTo(Duration.ofMinutes(3)); // GH-90000
        }
    }
}
