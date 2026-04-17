package com.ghatana.agent.learning.review;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("InMemoryHumanReviewQueue")
class InMemoryHumanReviewQueueTest extends EventloopTestBase {

    @Test
    @DisplayName("expired items lazily transition to EXPIRED and drop from pending lists")
    void expiredItemsDropFromPendingLists() {
        InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue();
        ReviewItem item = ReviewItem.builder()
            .reviewId("expired-review")
            .tenantId("tenant-a")
            .skillId("skill-1")
            .proposedVersion("v1")
            .expiresAt(Instant.now().minusSeconds(5))
            .build();

        runPromise(() -> queue.enqueue(item));

        assertThat(runPromise(() -> queue.getPending(ReviewFilter.forTenant("tenant-a")))).isEmpty();
        ReviewItem reloaded = runPromise(() -> queue.getById("expired-review"));
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getStatus()).isEqualTo(ReviewStatus.EXPIRED);
        assertThat(runPromise(queue::pendingCount)).isZero();
    }

    @Test
    @DisplayName("approve rejects items that have already expired")
    void approveRejectsExpiredItems() {
        InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue();
        ReviewItem item = ReviewItem.builder()
            .reviewId("expired-approve")
            .tenantId("tenant-a")
            .skillId("skill-1")
            .proposedVersion("v1")
            .expiresAt(Instant.now().minusSeconds(5))
            .build();
        runPromise(() -> queue.enqueue(item));

        assertThatThrownBy(() -> runPromise(() -> queue.approve(
            "expired-approve",
            ReviewDecision.approve("alice", "too late")
        )))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not pending: EXPIRED");
    }
}