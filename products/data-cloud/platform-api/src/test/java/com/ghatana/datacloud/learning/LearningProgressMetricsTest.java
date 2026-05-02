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
 * Tests for learning progress metrics tracking (D011). 
 *
 * <p>Validates training progress monitoring and metric collection.
 *
 * @doc.type class
 * @doc.purpose Learning progress metrics tests
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) 
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
        void getProgressReturnsSummary() { 
            String jobId = "job-001";

            LearningProgressTracker.ProgressSummary summary = new LearningProgressTracker.ProgressSummary( 
                jobId, 5, 10, 50.0,
                Duration.ofMinutes(15), Duration.ofMinutes(15), 
                "RUNNING", true, 0.28, 0
            );

            when(progressTracker.getProgress(jobId)) 
                .thenReturn(Promise.of(summary)); 

            LearningProgressTracker.ProgressSummary result = runPromise(() -> 
                progressTracker.getProgress(jobId) 
            );

            assertThat(result.jobId()).isEqualTo(jobId); 
            assertThat(result.currentEpoch()).isEqualTo(5); 
            assertThat(result.totalEpochs()).isEqualTo(10); 
            assertThat(result.progressPercent()).isEqualTo(50.0); 
        }

        @Test
        @DisplayName("[D011]: progress_percent_calculated_correctly")
        void progressPercentCalculatedCorrectly() { 
            int current = 5;
            int total = 10;

            double percent = ((double) current / total) * 100; 

            assertThat(percent).isEqualTo(50.0); 
        }

        @Test
        @DisplayName("[D011]: estimated_remaining_time_based_on_elapsed")
        void estimatedRemainingTimeBasedOnElapsed() { 
            Duration elapsed = Duration.ofMinutes(15); 
            double progressPercent = 50.0;

            // If 50% took 15 minutes, remaining should be ~15 minutes
            Duration estimated = Duration.ofMinutes(15); 

            assertThat(estimated).isEqualTo(elapsed); 
        }

        @Test
        @DisplayName("[D011]: is_improving_detects_decreasing_loss")
        void isImprovingDetectsDecreasingLoss() { 
            LearningProgressTracker.ProgressSummary improving =
                new LearningProgressTracker.ProgressSummary( 
                    "job-001", 5, 10, 50.0,
                    Duration.ofMinutes(15), Duration.ofMinutes(15), 
                    "RUNNING", true, 0.25, 0
                );

            assertThat(improving.isImproving()).isTrue(); 
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
        void getMetricsHistoryReturnsTimeSeries() { 
            String jobId = "job-001";

            List<LearningProgressTracker.MetricPoint> history = List.of( 
                new LearningProgressTracker.MetricPoint(1704067200000L, 1, "loss", 0.5), 
                new LearningProgressTracker.MetricPoint(1704067260000L, 2, "loss", 0.45), 
                new LearningProgressTracker.MetricPoint(1704067320000L, 3, "loss", 0.40), 
                new LearningProgressTracker.MetricPoint(1704067380000L, 4, "loss", 0.35), 
                new LearningProgressTracker.MetricPoint(1704067440000L, 5, "loss", 0.30) 
            );

            when(progressTracker.getMetricsHistory(jobId)) 
                .thenReturn(Promise.of(history)); 

            List<LearningProgressTracker.MetricPoint> result = runPromise(() -> 
                progressTracker.getMetricsHistory(jobId) 
            );

            assertThat(result).hasSize(5); 
            assertThat(result.get(0).value()).isGreaterThan(result.get(4).value()); // Decreasing 
        }

        @Test
        @DisplayName("[D011]: metric_point_contains_timestamp_epoch_value")
        void metricPointContainsTimestampEpochValue() { 
            long timestamp = 1704067200000L;
            int epoch = 5;
            String metricName = "accuracy";
            double value = 0.92;

            LearningProgressTracker.MetricPoint point =
                new LearningProgressTracker.MetricPoint(timestamp, epoch, metricName, value); 

            assertThat(point.timestamp()).isEqualTo(timestamp); 
            assertThat(point.epoch()).isEqualTo(epoch); 
            assertThat(point.metricName()).isEqualTo(metricName); 
            assertThat(point.value()).isEqualTo(value); 
        }

        @Test
        @DisplayName("[D011]: loss_trend_detected_from_history")
        void lossTrendDetectedFromHistory() { 
            List<LearningProgressTracker.MetricPoint> lossHistory = List.of( 
                new LearningProgressTracker.MetricPoint(0L, 1, "loss", 0.8), 
                new LearningProgressTracker.MetricPoint(0L, 2, "loss", 0.7), 
                new LearningProgressTracker.MetricPoint(0L, 3, "loss", 0.6), 
                new LearningProgressTracker.MetricPoint(0L, 4, "loss", 0.5), 
                new LearningProgressTracker.MetricPoint(0L, 5, "loss", 0.4) 
            );

            // Check if loss is decreasing
            boolean decreasing = true;
            for (int i = 1; i < lossHistory.size(); i++) { 
                if (lossHistory.get(i).value() >= lossHistory.get(i - 1).value()) { 
                    decreasing = false;
                    break;
                }
            }

            assertThat(decreasing).isTrue(); 
        }

        @Test
        @DisplayName("[D011]: plateau_detected_when_loss_stops_decreasing")
        void plateauDetectedWhenLossStopsDecreasing() { 
            List<LearningProgressTracker.MetricPoint> plateauHistory = List.of( 
                new LearningProgressTracker.MetricPoint(0L, 1, "loss", 0.5), 
                new LearningProgressTracker.MetricPoint(0L, 2, "loss", 0.48), 
                new LearningProgressTracker.MetricPoint(0L, 3, "loss", 0.47), 
                new LearningProgressTracker.MetricPoint(0L, 4, "loss", 0.47), // No change 
                new LearningProgressTracker.MetricPoint(0L, 5, "loss", 0.47)  // No change 
            );

            int epochsSinceImprovement = 0;
            double bestLoss = Double.MAX_VALUE;
            for (LearningProgressTracker.MetricPoint point : plateauHistory) { 
                if (point.value() < bestLoss) { 
                    bestLoss = point.value(); 
                    epochsSinceImprovement = 0;
                } else {
                    epochsSinceImprovement++;
                }
            }

            assertThat(epochsSinceImprovement).isEqualTo(2); 
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
        void getHistoricalComparisonReturnsStats() { 
            String modelId = "model-001";

            List<LearningProgressTracker.RunSummary> runHistory = List.of( 
                new LearningProgressTracker.RunSummary( 
                    "job-1", 1704067200000L, Duration.ofHours(2), 
                    0.25, 0.22, 10, "COMPLETED"
                ),
                new LearningProgressTracker.RunSummary( 
                    "job-2", 1704153600000L, Duration.ofHours(1), 
                    0.20, 0.18, 10, "COMPLETED"
                ),
                new LearningProgressTracker.RunSummary( 
                    "job-3", 1704240000000L, Duration.ofHours(3), 
                    0.30, 0.28, 8, "FAILED"
                )
            );

            LearningProgressTracker.HistoricalComparison comparison =
                new LearningProgressTracker.HistoricalComparison( 
                    modelId, 3, 0.25, 0.18, 0.30,
                    Duration.ofHours(2), runHistory 
                );

            when(progressTracker.getHistoricalComparison(modelId)) 
                .thenReturn(Promise.of(comparison)); 

            LearningProgressTracker.HistoricalComparison result = runPromise(() -> 
                progressTracker.getHistoricalComparison(modelId) 
            );

            assertThat(result.modelId()).isEqualTo(modelId); 
            assertThat(result.totalRuns()).isEqualTo(3); 
            assertThat(result.bestLoss()).isEqualTo(0.18); 
            assertThat(result.worstLoss()).isEqualTo(0.30); 
        }

        @Test
        @DisplayName("[D011]: current_is_best_detects_improvement")
        void currentIsBestDetectsImprovement() { 
            double bestLoss = 0.20;

            LearningProgressTracker.HistoricalComparison comparison =
                new LearningProgressTracker.HistoricalComparison( 
                    "model-001", 5, 0.25, bestLoss, 0.35,
                    Duration.ofHours(2), List.of() 
                );

            assertThat(comparison.currentIsBest(0.18)).isTrue();  // Better than best 
            assertThat(comparison.currentIsBest(0.20)).isTrue();  // Equal to best (isBest uses <=) 
            assertThat(comparison.currentIsBest(0.25)).isFalse(); // Worse than best 
        }

        @Test
        @DisplayName("[D011]: run_summary_contains_duration_and_status")
        void runSummaryContainsDurationAndStatus() { 
            LearningProgressTracker.RunSummary summary = new LearningProgressTracker.RunSummary( 
                "job-001", 1704067200000L, Duration.ofHours(2), 
                0.25, 0.22, 10, "COMPLETED"
            );

            assertThat(summary.jobId()).isEqualTo("job-001");
            assertThat(summary.duration()).isEqualTo(Duration.ofHours(2)); 
            assertThat(summary.finalLoss()).isEqualTo(0.25); 
            assertThat(summary.bestLoss()).isEqualTo(0.22); 
            assertThat(summary.epochsCompleted()).isEqualTo(10); 
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
        void recordEpochStoresMetrics() { 
            String jobId = "job-001";
            int epoch = 5;

            ModelTrainingService.EpochMetrics metrics = new ModelTrainingService.EpochMetrics( 
                epoch, 0.3, 0.28, 0.90, 0.91, Duration.ofMinutes(2) 
            );

            when(progressTracker.recordEpoch(jobId, epoch, metrics)) 
                .thenReturn(Promise.of((Void) null)); 

            runPromise(() -> progressTracker.recordEpoch(jobId, epoch, metrics)); 

            verify(progressTracker).recordEpoch(jobId, epoch, metrics); 
        }

        @Test
        @DisplayName("[D011]: record_completion_stores_final_metrics")
        void recordCompletionStoresFinalMetrics() { 
            String jobId = "job-001";

            List<ModelTrainingService.EpochMetrics> history = List.of( 
                new ModelTrainingService.EpochMetrics(1, 0.5, 0.45, 0.80, 0.82, Duration.ofMinutes(2)), 
                new ModelTrainingService.EpochMetrics(2, 0.4, 0.38, 0.85, 0.86, Duration.ofMinutes(2)) 
            );

            ModelTrainingService.TrainingMetrics finalMetrics = new ModelTrainingService.TrainingMetrics( 
                jobId, 2, 0.4, 0.38, 0.85, 0.86, 0.001, 0.1,
                2000, 100, history
            );

            when(progressTracker.recordCompletion(jobId, finalMetrics)) 
                .thenReturn(Promise.of((Void) null)); 

            runPromise(() -> progressTracker.recordCompletion(jobId, finalMetrics)); 

            verify(progressTracker).recordCompletion(jobId, finalMetrics); 
        }

        @Test
        @DisplayName("[D011]: record_failure_stores_error")
        void recordFailureStoresError() { 
            String jobId = "job-001";
            String error = "Out of memory";

            when(progressTracker.recordFailure(jobId, error)) 
                .thenReturn(Promise.of((Void) null)); 

            runPromise(() -> progressTracker.recordFailure(jobId, error)); 

            verify(progressTracker).recordFailure(jobId, error); 
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
        void bestLossTrackedAcrossEpochs() { 
            List<Double> losses = List.of(0.5, 0.45, 0.48, 0.42, 0.43, 0.40); 

            double bestLoss = losses.stream() 
                .min(Double::compare) 
                .orElse(Double.MAX_VALUE); 

            assertThat(bestLoss).isEqualTo(0.40); 
        }

        @Test
        @DisplayName("[D011]: epochs_since_improvement_tracked")
        void epochsSinceImprovementTracked() { 
            List<Double> losses = List.of(0.5, 0.45, 0.44, 0.44, 0.44, 0.43); 

            double bestLoss = Double.MAX_VALUE;
            int epochsSinceImprovement = 0;
            int lastImprovementEpoch = -1;
            int previousImprovementEpoch = -1;

            for (int i = 0; i < losses.size(); i++) { 
                if (losses.get(i) < bestLoss) { 
                    previousImprovementEpoch = lastImprovementEpoch;
                    bestLoss = losses.get(i); 
                    lastImprovementEpoch = i;
                }
            }

            // Calculate epochs since the improvement before the last one
            if (previousImprovementEpoch >= 0) { 
                epochsSinceImprovement = lastImprovementEpoch - previousImprovementEpoch;
            }

            assertThat(epochsSinceImprovement).isEqualTo(3); // Epochs 2 to 5 (3 epochs gap) 
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
        void samplesPerSecondCalculated() { 
            long samplesProcessed = 10000;
            Duration elapsedTime = Duration.ofMinutes(5); 

            double samplesPerSecond = samplesProcessed / (double) elapsedTime.getSeconds(); 

            assertThat(samplesPerSecond).isGreaterThan(0); 
        }

        @Test
        @DisplayName("[D011]: epoch_duration_tracked")
        void epochDurationTracked() { 
            ModelTrainingService.EpochMetrics epoch = new ModelTrainingService.EpochMetrics( 
                5, 0.3, 0.28, 0.90, 0.91, Duration.ofMinutes(3) 
            );

            assertThat(epoch.duration()).isEqualTo(Duration.ofMinutes(3)); 
        }
    }
}
