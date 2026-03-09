/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.learning.review;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link InMemoryHumanReviewQueue}.
 */
@DisplayName("HumanReviewQueue")
class HumanReviewQueueTest extends EventloopTestBase {

    private InMemoryHumanReviewQueue queue;
    private final List<ReviewItem> notifiedItems = new ArrayList<>();

    @BeforeEach
    void setUp() {
        notifiedItems.clear();
        queue = new InMemoryHumanReviewQueue(new ReviewNotificationSpi() {
            @Override public void onItemEnqueued(ReviewItem item) { notifiedItems.add(item); }
            @Override public void onItemApproved(ReviewItem item) { notifiedItems.add(item); }
            @Override public void onItemRejected(ReviewItem item) { notifiedItems.add(item); }
        });
    }

    private ReviewItem createItem(String skillId, double confidence) {
        return ReviewItem.builder()
                .tenantId("test-tenant")
                .skillId(skillId)
                .proposedVersion("1.0.0")
                .confidenceScore(confidence)
                .evaluationSummary("Low confidence extraction")
                .context(Map.of("gate", "regression"))
                .build();
    }

    @Nested
    @DisplayName("Enqueue")
    class EnqueueTests {

        @Test
        @DisplayName("should enqueue item and trigger notification")
        void shouldEnqueueAndNotify() {
            ReviewItem item = createItem("skill-1", 0.45);

            ReviewItem result = runPromise(() -> queue.enqueue(item));

            assertThat(result.getStatus()).isEqualTo(ReviewStatus.PENDING);
            assertThat(result.getSkillId()).isEqualTo("skill-1");
            assertThat(result.getConfidenceScore()).isEqualTo(0.45);
            assertThat(notifiedItems).hasSize(1);
        }

        @Test
        @DisplayName("should assign unique review IDs")
        void shouldAssignUniqueIds() {
            ReviewItem item1 = createItem("skill-1", 0.3);
            ReviewItem item2 = createItem("skill-2", 0.5);

            runPromise(() -> queue.enqueue(item1));
            runPromise(() -> queue.enqueue(item2));

            assertThat(item1.getReviewId()).isNotEqualTo(item2.getReviewId());
        }
    }

    @Nested
    @DisplayName("Query")
    class QueryTests {

        @Test
        @DisplayName("should return all pending items")
        void shouldReturnAllPending() {
            runPromise(() -> queue.enqueue(createItem("s1", 0.3)));
            runPromise(() -> queue.enqueue(createItem("s2", 0.5)));
            runPromise(() -> queue.enqueue(createItem("s3", 0.65)));

            List<ReviewItem> pending = runPromise(() -> queue.getPending(null));
            assertThat(pending).hasSize(3);
        }

        @Test
        @DisplayName("should filter by confidence threshold")
        void shouldFilterByConfidence() {
            runPromise(() -> queue.enqueue(createItem("low", 0.2)));
            runPromise(() -> queue.enqueue(createItem("mid", 0.5)));
            runPromise(() -> queue.enqueue(createItem("high", 0.8)));

            List<ReviewItem> lowConf = runPromise(() ->
                    queue.getPending(ReviewFilter.lowConfidence(0.5)));

            assertThat(lowConf).hasSize(2);
            assertThat(lowConf).allMatch(i -> i.getConfidenceScore() <= 0.5);
        }

        @Test
        @DisplayName("should filter by tenant")
        void shouldFilterByTenant() {
            runPromise(() -> queue.enqueue(
                    ReviewItem.builder()
                            .tenantId("acme")
                            .skillId("s1").proposedVersion("1.0").confidenceScore(0.3)
                            .build()));
            runPromise(() -> queue.enqueue(
                    ReviewItem.builder()
                            .tenantId("globex")
                            .skillId("s2").proposedVersion("1.0").confidenceScore(0.4)
                            .build()));

            List<ReviewItem> acmeItems = runPromise(() ->
                    queue.getPending(ReviewFilter.forTenant("acme")));
            assertThat(acmeItems).hasSize(1);
            assertThat(acmeItems.get(0).getTenantId()).isEqualTo("acme");
        }

        @Test
        @DisplayName("should return pending count")
        void shouldReturnPendingCount() {
            runPromise(() -> queue.enqueue(createItem("s1", 0.3)));
            runPromise(() -> queue.enqueue(createItem("s2", 0.5)));

            long count = runPromise(queue::pendingCount);
            assertThat(count).isEqualTo(2);
        }

        @Test
        @DisplayName("should lookup by ID")
        void shouldLookupById() {
            ReviewItem item = createItem("s1", 0.4);
            runPromise(() -> queue.enqueue(item));

            ReviewItem found = runPromise(() -> queue.getById(item.getReviewId()));
            assertThat(found).isNotNull();
            assertThat(found.getSkillId()).isEqualTo("s1");
        }
    }

    @Nested
    @DisplayName("Approval and Rejection")
    class DecisionTests {

        @Test
        @DisplayName("should approve a pending item")
        void shouldApprovePendingItem() {
            ReviewItem item = createItem("s1", 0.45);
            runPromise(() -> queue.enqueue(item));

            ReviewDecision decision = ReviewDecision.approve("reviewer@ghatana.ai",
                    "Manually verified the policy is correct");
            ReviewItem approved = runPromise(() -> queue.approve(item.getReviewId(), decision));

            assertThat(approved.getStatus()).isEqualTo(ReviewStatus.APPROVED);
            assertThat(approved.getDecision()).isNotNull();
            assertThat(approved.getDecision().reviewer()).isEqualTo("reviewer@ghatana.ai");
            assertThat(approved.getDecidedAt()).isNotNull();
            // Approved items should not appear in pending
            long pendingCount = runPromise(queue::pendingCount);
            assertThat(pendingCount).isZero();
        }

        @Test
        @DisplayName("should reject a pending item")
        void shouldRejectPendingItem() {
            ReviewItem item = createItem("s1", 0.3);
            runPromise(() -> queue.enqueue(item));

            ReviewDecision decision = ReviewDecision.reject("reviewer@ghatana.ai",
                    "Policy contradicts existing procedure");
            ReviewItem rejected = runPromise(() -> queue.reject(item.getReviewId(), decision));

            assertThat(rejected.getStatus()).isEqualTo(ReviewStatus.REJECTED);
        }

        @Test
        @DisplayName("should trigger notification on approval")
        void shouldNotifyOnApproval() {
            ReviewItem item = createItem("s1", 0.5);
            runPromise(() -> queue.enqueue(item));
            notifiedItems.clear();

            runPromise(() -> queue.approve(item.getReviewId(),
                    ReviewDecision.approve("admin", "Looks good")));

            assertThat(notifiedItems).hasSize(1);
            assertThat(notifiedItems.get(0).getStatus()).isEqualTo(ReviewStatus.APPROVED);
        }
    }
}
