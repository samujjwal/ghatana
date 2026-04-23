package com.ghatana.datacloud.launcher.learning;

import com.ghatana.datacloud.brain.BrainContext;
import com.ghatana.datacloud.brain.DataCloudBrain;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DataCloudLearningBridge} (DC-7). // GH-90000
 *
 * @doc.type class
 * @doc.purpose Unit tests for the DataCloud learning bridge
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudLearningBridge (DC-7)")
class DataCloudLearningBridgeTest {

    private DataCloudBrain mockBrain;
    private DataCloudLearningBridge bridge;

    @BeforeEach
    void setUp() { // GH-90000
        mockBrain = mock(DataCloudBrain.class); // GH-90000
        bridge = new DataCloudLearningBridge(mockBrain); // GH-90000
    }

    // ==================== Construction ====================

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("null brain throws IllegalArgumentException")
        void nullBrain_throws() { // GH-90000
            assertThatThrownBy(() -> new DataCloudLearningBridge(null)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("brain");
        }

        @Test
        @DisplayName("initial status shows RUNNING=false and lastRunTime=never")
        void initialStatus_isCorrect() { // GH-90000
            Map<String, Object> status = bridge.getStatus(); // GH-90000
            assertThat(status.get("running")).isEqualTo(false);
            assertThat(status.get("lastRunTime")).isEqualTo("never");
            assertThat(status.get("intervalMinutes")).isEqualTo(DataCloudLearningBridge.INTERVAL_MINUTES);
        }

        @Test
        @DisplayName("initial review queue is empty")
        void initialReviewQueue_isEmpty() { // GH-90000
            assertThat(bridge.getReviewQueue()).isEmpty(); // GH-90000
        }
    }

    // ==================== runLearning ====================

    @Nested
    @DisplayName("runLearning()")
    class RunLearningTests {

        @Test
        @DisplayName("returns COMPLETED status on success")
        void runLearning_success_returnsCompleted() { // GH-90000
            DataCloudBrain.LearningResult result = DataCloudBrain.LearningResult.builder() // GH-90000
                .patternsDiscovered(List.of()) // GH-90000
                .patternsUpdated(List.of()) // GH-90000
                .patternsDeprecated(List.of()) // GH-90000
                .recordsAnalyzed(100L) // GH-90000
                .learningTimeMs(50L) // GH-90000
                .build(); // GH-90000
            when(mockBrain.learn(any(DataCloudBrain.LearningConfig.class), any(BrainContext.class))) // GH-90000
                .thenReturn(Promise.of(result)); // GH-90000

            Map<String, Object> summary = bridge.runLearning("tenant-1", true); // GH-90000

            assertThat(summary.get("status")).isEqualTo("COMPLETED");
            assertThat(summary.get("tenantId")).isEqualTo("tenant-1");
            assertThat(summary.get("manual")).isEqualTo(true);
            assertThat(((Number) summary.get("patternsDiscovered")).intValue()).isEqualTo(0);
            assertThat(((Number) summary.get("recordsAnalyzed")).longValue()).isEqualTo(100L);
            assertThat(summary.get("ranAt")).isNotNull();
        }

        @Test
        @DisplayName("SKIPPED when already running (concurrent guard)")
        void runLearning_alreadyRunning_returnsSkipped() throws Exception { // GH-90000
            // Block the bridge so a second call sees it as running
            DataCloudBrain.LearningResult result = DataCloudBrain.LearningResult.builder() // GH-90000
                .patternsDiscovered(List.of()) // GH-90000
                .patternsUpdated(List.of()) // GH-90000
                .patternsDeprecated(List.of()) // GH-90000
                .build(); // GH-90000
            // Make brain.learn() slow — use a latch // GH-90000
            java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1); // GH-90000
            java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1); // GH-90000

            when(mockBrain.learn(any(), any())).thenAnswer(inv -> { // GH-90000
                entered.countDown(); // GH-90000
                release.await(); // GH-90000
                return Promise.of(result); // GH-90000
            });

            Thread firstThread = new Thread(() -> bridge.runLearning("t1", true)); // GH-90000
            firstThread.start(); // GH-90000
            entered.await(); // wait until first thread is inside runLearning // GH-90000

            Map<String, Object> secondResult = bridge.runLearning("t2", true); // GH-90000
            assertThat(secondResult.get("status")).isEqualTo("SKIPPED");

            release.countDown(); // GH-90000
            firstThread.join(2_000); // GH-90000
        }

        @Test
        @DisplayName("FAILED status when brain.learn() throws")
        void runLearning_brainThrows_returnsFailed() { // GH-90000
            when(mockBrain.learn(any(DataCloudBrain.LearningConfig.class), any(BrainContext.class))) // GH-90000
                .thenThrow(new RuntimeException("brain exploded"));

            Map<String, Object> summary = bridge.runLearning("default", false); // GH-90000

            assertThat(summary.get("status")).isEqualTo("FAILED");
            assertThat(summary.get("error").toString()).contains("brain exploded");
        }

        @Test
        @DisplayName("lastResult is updated after a successful run")
        void runLearning_updatesLastResult() { // GH-90000
            DataCloudBrain.LearningResult result = DataCloudBrain.LearningResult.builder() // GH-90000
                .patternsDiscovered(List.of()) // GH-90000
                .patternsUpdated(List.of()) // GH-90000
                .patternsDeprecated(List.of()) // GH-90000
                .recordsAnalyzed(42L) // GH-90000
                .build(); // GH-90000
            when(mockBrain.learn(any(), any())).thenReturn(Promise.of(result)); // GH-90000

            bridge.runLearning("t", false); // GH-90000

            Map<String, Object> status = bridge.getStatus(); // GH-90000
            Map<?, ?> lastResult = (Map<?, ?>) status.get("lastResult");
            assertThat(lastResult.get("status")).isEqualTo("COMPLETED");
        }

        @Test
        @DisplayName("lastRunTime is set after a successful run")
        void runLearning_setsLastRunTime() { // GH-90000
            when(mockBrain.learn(any(), any())).thenReturn(Promise.of( // GH-90000
                DataCloudBrain.LearningResult.builder() // GH-90000
                    .patternsDiscovered(List.of()).patternsUpdated(List.of()) // GH-90000
                    .patternsDeprecated(List.of()).build() // GH-90000
            ));

            bridge.runLearning("t", false); // GH-90000

            Map<String, Object> status = bridge.getStatus(); // GH-90000
            assertThat(status.get("lastRunTime")).isNotEqualTo("never");
        }
    }

    // ==================== Review Queue ====================

    @Nested
    @DisplayName("Review Queue")
    class ReviewQueueTests {

        @Test
        @DisplayName("approveReview: returns true and updates status to APPROVED")
        void approveReview_found_returnsTrue() { // GH-90000
            // seedReviewItem("pat-1") → runLearning seeds key "review-pat-1"
            seedReviewItem("pat-1");
            String reviewKey = "review-pat-1";

            boolean ok = bridge.approveReview(reviewKey); // GH-90000

            assertThat(ok).isTrue(); // GH-90000
            Map<String, Object> item = bridge.getReviewQueue().get(reviewKey); // GH-90000
            assertThat(item.get("status")).isEqualTo("APPROVED");
            assertThat(item.get("reviewedAt")).isNotNull();
        }

        @Test
        @DisplayName("rejectReview: returns true and updates status to REJECTED")
        void rejectReview_found_returnsTrue() { // GH-90000
            seedReviewItem("pat-2");
            String reviewKey = "review-pat-2";

            boolean ok = bridge.rejectReview(reviewKey); // GH-90000

            assertThat(ok).isTrue(); // GH-90000
            Map<String, Object> item = bridge.getReviewQueue().get(reviewKey); // GH-90000
            assertThat(item.get("status")).isEqualTo("REJECTED");
        }

        @Test
        @DisplayName("approveReview: returns false when item not found")
        void approveReview_notFound_returnsFalse() { // GH-90000
            assertThat(bridge.approveReview("no-such-id")).isFalse();
        }

        @Test
        @DisplayName("rejectReview: returns false when item not found")
        void rejectReview_notFound_returnsFalse() { // GH-90000
            assertThat(bridge.rejectReview("no-such-id")).isFalse();
        }

        @Test
        @DisplayName("getReviewQueue returns unmodifiable snapshot")
        void getReviewQueue_returnsSnapshot() { // GH-90000
            assertThat(bridge.getReviewQueue()).isEmpty(); // GH-90000
        }

        /**
         * Seeds the review queue by running a learning cycle with a mock brain that returns
         * a low-confidence pattern with the given {@code patternId}. The resulting review key
         * will be {@code "review-" + patternId}.
         */
        private void seedReviewItem(String patternId) { // GH-90000
            com.ghatana.datacloud.pattern.PatternRecord lowConfPattern =
                com.ghatana.datacloud.pattern.PatternRecord.builder() // GH-90000
                    .id(patternId) // GH-90000
                    .name("test-pattern-" + patternId) // GH-90000
                    .confidence(0.3f) // GH-90000
                    .build(); // GH-90000
            DataCloudBrain.LearningResult resultWithLowConf =
                DataCloudBrain.LearningResult.builder() // GH-90000
                    .patternsDiscovered(List.of(lowConfPattern)) // GH-90000
                    .patternsUpdated(List.of()) // GH-90000
                    .patternsDeprecated(List.of()) // GH-90000
                    .build(); // GH-90000
            when(mockBrain.learn(any(), any())).thenReturn(Promise.of(resultWithLowConf)); // GH-90000
            bridge.runLearning("t", false); // GH-90000
        }
    }

    // ==================== Status Reporting ====================

    @Nested
    @DisplayName("Status Reporting")
    class StatusReportingTests {

        @Test
        @DisplayName("status always contains required keys")
        void status_alwaysContainsRequiredKeys() { // GH-90000
            Map<String, Object> status = bridge.getStatus(); // GH-90000

            assertThat(status.containsKey("running")).isTrue();
            assertThat(status.containsKey("lastRunTime")).isTrue();
            assertThat(status.containsKey("nextScheduledRun")).isTrue();
            assertThat(status.containsKey("intervalMinutes")).isTrue();
            assertThat(status.containsKey("pendingReviews")).isTrue();
            assertThat(status.containsKey("lastResult")).isTrue();
        }

        @Test
        @DisplayName("initial lastResult has status=NOT_RUN")
        void status_initialLastResult_isNotRun() { // GH-90000
            Map<String, Object> status = bridge.getStatus(); // GH-90000
            Map<?, ?> lastResult = (Map<?, ?>) status.get("lastResult");
            assertThat(lastResult.get("status")).isEqualTo("NOT_RUN");
        }

        @Test
        @DisplayName("intervalMinutes matches INTERVAL_MINUTES constant")
        void status_intervalMinutes_matchesConstant() { // GH-90000
            Map<String, Object> status = bridge.getStatus(); // GH-90000
            assertThat(((Number) status.get("intervalMinutes")).longValue())
                .isEqualTo(DataCloudLearningBridge.INTERVAL_MINUTES); // GH-90000
        }
    }

    // ==================== AutoCloseable ====================

    @Nested
    @DisplayName("AutoCloseable")
    class CloseTests {

        @Test
        @DisplayName("close() does not throw")
        void close_doesNotThrow() { // GH-90000
            bridge.close(); // should not throw // GH-90000
        }
    }

    // ==================== Hardening (P3.7.1) ==================== // GH-90000

    @Nested
    @DisplayName("Hardening")
    class HardeningTests {

        @Test
        @DisplayName("purgeCompletedReviews: removes APPROVED and REJECTED items from queue")
        void purgeCompleted_removesApprovedAndRejected() { // GH-90000
            seedReviewItem("a");
            seedReviewItem("b");
            seedReviewItem("c");
            bridge.approveReview("review-a");
            bridge.rejectReview("review-b");
            // "review-c" stays PENDING

            int purged = bridge.purgeCompletedReviews(); // GH-90000

            assertThat(purged).isEqualTo(2); // GH-90000
            assertThat(bridge.getReviewQueue()).containsOnlyKeys("review-c");
        }

        @Test
        @DisplayName("purgeCompletedReviews: returns 0 when no completed reviews exist")
        void purgeCompleted_returnsZeroWhenEmpty() { // GH-90000
            seedReviewItem("x");
            // leave it PENDING

            int purged = bridge.purgeCompletedReviews(); // GH-90000

            assertThat(purged).isEqualTo(0); // GH-90000
            assertThat(bridge.getReviewQueue()).hasSize(1); // GH-90000
        }

        @Test
        @DisplayName("purgeCompletedReviews: idempotent — second call returns 0")
        void purgeCompleted_idempotent() { // GH-90000
            seedReviewItem("y");
            bridge.approveReview("review-y");

            bridge.purgeCompletedReviews(); // GH-90000
            int secondPurge = bridge.purgeCompletedReviews(); // GH-90000

            assertThat(secondPurge).isEqualTo(0); // GH-90000
            assertThat(bridge.getReviewQueue()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("review queue cap: items beyond MAX_REVIEW_QUEUE_SIZE are not enqueued")
        void queueCap_enforcedOnEnqueue() { // GH-90000
            // Build a result with many low-confidence patterns to overflow the cap
            java.util.List<com.ghatana.datacloud.pattern.PatternRecord> manyPatterns =
                new java.util.ArrayList<>(); // GH-90000
            for (int i = 0; i < DataCloudLearningBridge.MAX_REVIEW_QUEUE_SIZE + 10; i++) { // GH-90000
                manyPatterns.add(com.ghatana.datacloud.pattern.PatternRecord.builder() // GH-90000
                    .id("pattern-" + i) // GH-90000
                    .name("p" + i) // GH-90000
                    .confidence(0.1f) // GH-90000
                    .build()); // GH-90000
            }
            DataCloudBrain.LearningResult bigResult = DataCloudBrain.LearningResult.builder() // GH-90000
                .patternsDiscovered(manyPatterns) // GH-90000
                .patternsUpdated(List.of()) // GH-90000
                .patternsDeprecated(List.of()) // GH-90000
                .build(); // GH-90000
            when(mockBrain.learn(any(), any())).thenReturn(Promise.of(bigResult)); // GH-90000

            bridge.runLearning("t", false); // GH-90000

            assertThat(bridge.getReviewQueue().size()) // GH-90000
                .isLessThanOrEqualTo(DataCloudLearningBridge.MAX_REVIEW_QUEUE_SIZE); // GH-90000
        }

        /**
         * Seeds the review queue by running a learning cycle that yields one low-confidence pattern.
         */
        private void seedReviewItem(String patternId) { // GH-90000
            com.ghatana.datacloud.pattern.PatternRecord lowConfPattern =
                com.ghatana.datacloud.pattern.PatternRecord.builder() // GH-90000
                    .id(patternId) // GH-90000
                    .name("test-pattern-" + patternId) // GH-90000
                    .confidence(0.3f) // GH-90000
                    .build(); // GH-90000
            DataCloudBrain.LearningResult resultWithLowConf =
                DataCloudBrain.LearningResult.builder() // GH-90000
                    .patternsDiscovered(List.of(lowConfPattern)) // GH-90000
                    .patternsUpdated(List.of()) // GH-90000
                    .patternsDeprecated(List.of()) // GH-90000
                    .build(); // GH-90000
            when(mockBrain.learn(any(), any())).thenReturn(Promise.of(resultWithLowConf)); // GH-90000
            bridge.runLearning("t", false); // GH-90000
        }
    }
}
