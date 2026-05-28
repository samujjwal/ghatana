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
 * Unit tests for {@link DataCloudLearningBridge} (DC-7). 
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
    void setUp() { 
        mockBrain = mock(DataCloudBrain.class); 
        bridge = new DataCloudLearningBridge(mockBrain, null); 
    }

    // ==================== Construction ====================

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("null brain throws IllegalArgumentException")
        void nullBrain_throws() { 
            assertThatThrownBy(() -> new DataCloudLearningBridge(null, null)) 
                .isInstanceOf(IllegalArgumentException.class) 
                .hasMessageContaining("brain");
        }

        @Test
        @DisplayName("initial status shows RUNNING=false and lastRunTime=never")
        void initialStatus_isCorrect() { 
            Map<String, Object> status = bridge.getStatus(); 
            assertThat(status.get("running")).isEqualTo(false);
            assertThat(status.get("lastRunTime")).isEqualTo("never");
            assertThat(status.get("intervalMinutes")).isEqualTo(DataCloudLearningBridge.INTERVAL_MINUTES);
        }

        @Test
        @DisplayName("initial review queue is empty")
        void initialReviewQueue_isEmpty() { 
            assertThat(bridge.getReviewQueue()).isEmpty(); 
        }
    }

    // ==================== runLearning ====================

    @Nested
    @DisplayName("runLearning()")
    class RunLearningTests {

        @Test
        @DisplayName("returns COMPLETED status on success")
        void runLearning_success_returnsCompleted() { 
            DataCloudBrain.LearningResult result = DataCloudBrain.LearningResult.builder() 
                .patternsDiscovered(List.of()) 
                .patternsUpdated(List.of()) 
                .patternsDeprecated(List.of()) 
                .recordsAnalyzed(100L) 
                .learningTimeMs(50L) 
                .build(); 
            when(mockBrain.learn(any(DataCloudBrain.LearningConfig.class), any(BrainContext.class))) 
                .thenReturn(Promise.of(result)); 

            Map<String, Object> summary = bridge.runLearning("tenant-1", true); 

            assertThat(summary.get("status")).isEqualTo("COMPLETED");
            assertThat(summary.get("tenantId")).isEqualTo("tenant-1");
            assertThat(summary.get("manual")).isEqualTo(true);
            assertThat(((Number) summary.get("patternsDiscovered")).intValue()).isEqualTo(0);
            assertThat(((Number) summary.get("recordsAnalyzed")).longValue()).isEqualTo(100L);
            assertThat(summary.get("ranAt")).isNotNull();
        }

        @Test
        @DisplayName("SKIPPED when already running (concurrent guard)")
        void runLearning_alreadyRunning_returnsSkipped() throws Exception { 
            // Block the bridge so a second call sees it as running
            DataCloudBrain.LearningResult result = DataCloudBrain.LearningResult.builder() 
                .patternsDiscovered(List.of()) 
                .patternsUpdated(List.of()) 
                .patternsDeprecated(List.of()) 
                .build(); 
            // Make brain.learn() slow — use a latch 
            java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1); 
            java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1); 

            when(mockBrain.learn(any(), any())).thenAnswer(inv -> { 
                entered.countDown(); 
                release.await(); 
                return Promise.of(result); 
            });

            Thread firstThread = new Thread(() -> bridge.runLearning("t1", true)); 
            firstThread.start(); 
            entered.await(); // wait until first thread is inside runLearning 

            Map<String, Object> secondResult = bridge.runLearning("t2", true); 
            assertThat(secondResult.get("status")).isEqualTo("SKIPPED");

            release.countDown(); 
            firstThread.join(2_000); 
        }

        @Test
        @DisplayName("FAILED status when brain.learn() throws")
        void runLearning_brainThrows_returnsFailed() { 
            when(mockBrain.learn(any(DataCloudBrain.LearningConfig.class), any(BrainContext.class))) 
                .thenThrow(new RuntimeException("brain exploded"));

            Map<String, Object> summary = bridge.runLearning("default", false); 

            assertThat(summary.get("status")).isEqualTo("FAILED");
            assertThat(summary.get("error").toString()).contains("brain exploded");
        }

        @Test
        @DisplayName("lastResult is updated after a successful run")
        void runLearning_updatesLastResult() { 
            DataCloudBrain.LearningResult result = DataCloudBrain.LearningResult.builder() 
                .patternsDiscovered(List.of()) 
                .patternsUpdated(List.of()) 
                .patternsDeprecated(List.of()) 
                .recordsAnalyzed(42L) 
                .build(); 
            when(mockBrain.learn(any(), any())).thenReturn(Promise.of(result)); 

            bridge.runLearning("t", false); 

            Map<String, Object> status = bridge.getStatus(); 
            Map<?, ?> lastResult = (Map<?, ?>) status.get("lastResult");
            assertThat(lastResult.get("status")).isEqualTo("COMPLETED");
        }

        @Test
        @DisplayName("lastRunTime is set after a successful run")
        void runLearning_setsLastRunTime() { 
            when(mockBrain.learn(any(), any())).thenReturn(Promise.of( 
                DataCloudBrain.LearningResult.builder() 
                    .patternsDiscovered(List.of()).patternsUpdated(List.of()) 
                    .patternsDeprecated(List.of()).build() 
            ));

            bridge.runLearning("t", false); 

            Map<String, Object> status = bridge.getStatus(); 
            assertThat(status.get("lastRunTime")).isNotEqualTo("never");
        }
    }

    // ==================== Review Queue ====================

    @Nested
    @DisplayName("Review Queue")
    class ReviewQueueTests {

        @Test
        @DisplayName("approveReview: returns true and updates status to APPROVED")
        void approveReview_found_returnsTrue() { 
            // seedReviewItem("pat-1") → runLearning seeds key "review-pat-1"
            seedReviewItem("pat-1");
            String reviewKey = "review-pat-1";

            boolean ok = bridge.approveReview("tenant-1", "user-1", reviewKey); 

            assertThat(ok).isTrue(); 
            Map<String, Object> item = bridge.getReviewQueue().get(reviewKey); 
            assertThat(item.get("status")).isEqualTo("APPROVED");
            assertThat(item.get("reviewedAt")).isNotNull();
            assertThat(item.get("reviewedBy")).isEqualTo("user-1");
        }

        @Test
        @DisplayName("rejectReview: returns true and updates status to REJECTED")
        void rejectReview_found_returnsTrue() { 
            seedReviewItem("pat-2");
            String reviewKey = "review-pat-2";

            boolean ok = bridge.rejectReview("tenant-1", "user-1", reviewKey, "test reason"); 

            assertThat(ok).isTrue(); 
            Map<String, Object> item = bridge.getReviewQueue().get(reviewKey); 
            assertThat(item.get("status")).isEqualTo("REJECTED");
            assertThat(item.get("reviewedBy")).isEqualTo("user-1");
        }

        @Test
        @DisplayName("approveReview: same decision is idempotent and preserves reviewedAt")
        void approveReview_sameDecision_isIdempotent() {
            seedReviewItem("pat-3");
            String reviewKey = "review-pat-3";

            assertThat(bridge.approveReview("tenant-1", "user-1", reviewKey)).isTrue();
            String firstReviewedAt = (String) bridge.getReviewQueue().get(reviewKey).get("reviewedAt");

            assertThat(bridge.approveReview("tenant-1", "user-1", reviewKey)).isTrue();
            Map<String, Object> item = bridge.getReviewQueue().get(reviewKey);

            assertThat(item.get("status")).isEqualTo("APPROVED");
            assertThat(item.get("reviewedAt")).isEqualTo(firstReviewedAt);
        }

        @Test
        @DisplayName("rejectReview: conflicting terminal decision is rejected")
        void rejectReview_afterApproval_returnsFalse() {
            seedReviewItem("pat-4");
            String reviewKey = "review-pat-4";

            assertThat(bridge.approveReview("tenant-1", "user-1", reviewKey)).isTrue();
            assertThat(bridge.rejectReview("tenant-1", "user-1", reviewKey, "test")).isFalse();
            Map<String, Object> item = bridge.getReviewQueue().get(reviewKey);

            assertThat(item.get("status")).isEqualTo("APPROVED");
        }

        @Test
        @DisplayName("approveReview: returns false when item not found")
        void approveReview_notFound_returnsFalse() { 
            assertThat(bridge.approveReview("tenant-1", "user-1", "no-such-id")).isFalse();
        }

        @Test
        @DisplayName("rejectReview: returns false when item not found")
        void rejectReview_notFound_returnsFalse() { 
            assertThat(bridge.rejectReview("tenant-1", "user-1", "no-such-id", "test")).isFalse();
        }

        @Test
        @DisplayName("getReviewQueue returns unmodifiable snapshot")
        void getReviewQueue_returnsSnapshot() { 
            assertThat(bridge.getReviewQueue()).isEmpty(); 
        }

        /**
         * Seeds the review queue by running a learning cycle with a mock brain that returns
         * a low-confidence pattern with the given {@code patternId}. The resulting review key
         * will be {@code "review-" + patternId}.
         */
        private void seedReviewItem(String patternId) { 
            com.ghatana.datacloud.pattern.PatternRecord lowConfPattern =
                com.ghatana.datacloud.pattern.PatternRecord.builder() 
                    .id(patternId) 
                    .name("test-pattern-" + patternId) 
                    .confidence(0.3f) 
                    .build(); 
            DataCloudBrain.LearningResult resultWithLowConf =
                DataCloudBrain.LearningResult.builder() 
                    .patternsDiscovered(List.of(lowConfPattern)) 
                    .patternsUpdated(List.of()) 
                    .patternsDeprecated(List.of()) 
                    .build(); 
            when(mockBrain.learn(any(), any())).thenReturn(Promise.of(resultWithLowConf)); 
            bridge.runLearning("t", false); 
        }
    }

    // ==================== Status Reporting ====================

    @Nested
    @DisplayName("Status Reporting")
    class StatusReportingTests {

        @Test
        @DisplayName("status always contains required keys")
        void status_alwaysContainsRequiredKeys() { 
            Map<String, Object> status = bridge.getStatus(); 

            assertThat(status.containsKey("running")).isTrue();
            assertThat(status.containsKey("lastRunTime")).isTrue();
            assertThat(status.containsKey("nextScheduledRun")).isTrue();
            assertThat(status.containsKey("intervalMinutes")).isTrue();
            assertThat(status.containsKey("pendingReviews")).isTrue();
            assertThat(status.containsKey("lastResult")).isTrue();
        }

        @Test
        @DisplayName("initial lastResult has status=NOT_RUN")
        void status_initialLastResult_isNotRun() { 
            Map<String, Object> status = bridge.getStatus(); 
            Map<?, ?> lastResult = (Map<?, ?>) status.get("lastResult");
            assertThat(lastResult.get("status")).isEqualTo("NOT_RUN");
        }

        @Test
        @DisplayName("intervalMinutes matches INTERVAL_MINUTES constant")
        void status_intervalMinutes_matchesConstant() { 
            Map<String, Object> status = bridge.getStatus(); 
            assertThat(((Number) status.get("intervalMinutes")).longValue())
                .isEqualTo(DataCloudLearningBridge.INTERVAL_MINUTES); 
        }
    }

    // ==================== AutoCloseable ====================

    @Nested
    @DisplayName("AutoCloseable")
    class CloseTests {

        @Test
        @DisplayName("close() does not throw")
        void close_doesNotThrow() { 
            bridge.close(); // should not throw 
        }
    }

    // ==================== Hardening (P3.7.1) ==================== 

    @Nested
    @DisplayName("Hardening")
    class HardeningTests {

        @Test
        @DisplayName("purgeCompletedReviews: removes APPROVED and REJECTED items from queue")
        void purgeCompleted_removesApprovedAndRejected() { 
            seedReviewItem("a");
            seedReviewItem("b");
            seedReviewItem("c");
            bridge.approveReview("tenant-1", "user-1", "review-a");
            bridge.rejectReview("tenant-1", "user-1", "review-b", "test");
            // "review-c" stays PENDING

            int purged = bridge.purgeCompletedReviews(); 

            assertThat(purged).isEqualTo(2); 
            assertThat(bridge.getReviewQueue()).containsOnlyKeys("review-c");
        }

        @Test
        @DisplayName("purgeCompletedReviews: returns 0 when no completed reviews exist")
        void purgeCompleted_returnsZeroWhenEmpty() { 
            seedReviewItem("x");
            // leave it PENDING

            int purged = bridge.purgeCompletedReviews(); 

            assertThat(purged).isEqualTo(0); 
            assertThat(bridge.getReviewQueue()).hasSize(1); 
        }

        @Test
        @DisplayName("purgeCompletedReviews: idempotent — second call returns 0")
        void purgeCompleted_idempotent() { 
            seedReviewItem("y");
            bridge.approveReview("tenant-1", "user-1", "review-y");

            bridge.purgeCompletedReviews(); 
            int secondPurge = bridge.purgeCompletedReviews(); 

            assertThat(secondPurge).isEqualTo(0); 
            assertThat(bridge.getReviewQueue()).isEmpty(); 
        }

        @Test
        @DisplayName("review queue cap: items beyond MAX_REVIEW_QUEUE_SIZE are not enqueued")
        void queueCap_enforcedOnEnqueue() { 
            // Build a result with many low-confidence patterns to overflow the cap
            java.util.List<com.ghatana.datacloud.pattern.PatternRecord> manyPatterns =
                new java.util.ArrayList<>(); 
            for (int i = 0; i < DataCloudLearningBridge.MAX_REVIEW_QUEUE_SIZE + 10; i++) { 
                manyPatterns.add(com.ghatana.datacloud.pattern.PatternRecord.builder() 
                    .id("pattern-" + i) 
                    .name("p" + i) 
                    .confidence(0.1f) 
                    .build()); 
            }
            DataCloudBrain.LearningResult bigResult = DataCloudBrain.LearningResult.builder() 
                .patternsDiscovered(manyPatterns) 
                .patternsUpdated(List.of()) 
                .patternsDeprecated(List.of()) 
                .build(); 
            when(mockBrain.learn(any(), any())).thenReturn(Promise.of(bigResult)); 

            bridge.runLearning("t", false); 

            assertThat(bridge.getReviewQueue().size()) 
                .isLessThanOrEqualTo(DataCloudLearningBridge.MAX_REVIEW_QUEUE_SIZE); 
        }

        /**
         * Seeds the review queue by running a learning cycle that yields one low-confidence pattern.
         */
        private void seedReviewItem(String patternId) { 
            com.ghatana.datacloud.pattern.PatternRecord lowConfPattern =
                com.ghatana.datacloud.pattern.PatternRecord.builder() 
                    .id(patternId) 
                    .name("test-pattern-" + patternId) 
                    .confidence(0.3f) 
                    .build(); 
            DataCloudBrain.LearningResult resultWithLowConf =
                DataCloudBrain.LearningResult.builder() 
                    .patternsDiscovered(List.of(lowConfPattern)) 
                    .patternsUpdated(List.of()) 
                    .patternsDeprecated(List.of()) 
                    .build(); 
            when(mockBrain.learn(any(), any())).thenReturn(Promise.of(resultWithLowConf)); 
            bridge.runLearning("t", false); 
        }
    }
}
