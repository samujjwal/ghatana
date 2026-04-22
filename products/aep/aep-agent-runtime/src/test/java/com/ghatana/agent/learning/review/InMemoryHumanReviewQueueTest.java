package com.ghatana.agent.learning.review;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("InMemoryHumanReviewQueue [GH-90000]")
class InMemoryHumanReviewQueueTest extends EventloopTestBase {

    @Test
    @DisplayName("expired items lazily transition to EXPIRED and drop from pending lists [GH-90000]")
    void expiredItemsDropFromPendingLists() { // GH-90000
        InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(); // GH-90000
        ReviewItem item = ReviewItem.builder() // GH-90000
            .reviewId("expired-review [GH-90000]")
            .tenantId("tenant-a [GH-90000]")
            .skillId("skill-1 [GH-90000]")
            .proposedVersion("v1 [GH-90000]")
            .expiresAt(Instant.now().minusSeconds(5)) // GH-90000
            .build(); // GH-90000

        runPromise(() -> queue.enqueue(item)); // GH-90000

        assertThat(runPromise(() -> queue.getPending(ReviewFilter.forTenant("tenant-a [GH-90000]")))).isEmpty();
        ReviewItem reloaded = runPromise(() -> queue.getById("expired-review [GH-90000]"));
        assertThat(reloaded).isNotNull(); // GH-90000
        assertThat(reloaded.getStatus()).isEqualTo(ReviewStatus.EXPIRED); // GH-90000
        assertThat(runPromise(queue::pendingCount)).isZero(); // GH-90000
    }

    @Test
    @DisplayName("approve rejects items that have already expired [GH-90000]")
    void approveRejectsExpiredItems() { // GH-90000
        InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(); // GH-90000
        ReviewItem item = ReviewItem.builder() // GH-90000
            .reviewId("expired-approve [GH-90000]")
            .tenantId("tenant-a [GH-90000]")
            .skillId("skill-1 [GH-90000]")
            .proposedVersion("v1 [GH-90000]")
            .expiresAt(Instant.now().minusSeconds(5)) // GH-90000
            .build(); // GH-90000
        runPromise(() -> queue.enqueue(item)); // GH-90000

        assertThatThrownBy(() -> runPromise(() -> queue.approve( // GH-90000
            "expired-approve",
            ReviewDecision.approve("alice", "too late") // GH-90000
        )))
            .isInstanceOf(IllegalStateException.class) // GH-90000
            .hasMessageContaining("not pending: EXPIRED [GH-90000]");
    }

    @Test
    @DisplayName("escalate notifies the configured review notification SPI [GH-90000]")
    void escalateNotifiesReviewNotificationSpi() { // GH-90000
        AtomicReference<String> escalatedReviewId = new AtomicReference<>(); // GH-90000
        ReviewNotificationSpi notificationSpi = new ReviewNotificationSpi() { // GH-90000
            @Override public void onItemEnqueued(ReviewItem item) {} // GH-90000
            @Override public void onItemApproved(ReviewItem item) {} // GH-90000
            @Override public void onItemRejected(ReviewItem item) {} // GH-90000
            @Override public void onItemEscalated(ReviewItem item) { // GH-90000
                escalatedReviewId.set(item.getReviewId()); // GH-90000
            }
        };

        InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(notificationSpi); // GH-90000
        ReviewItem item = ReviewItem.builder() // GH-90000
            .reviewId("escalated-review [GH-90000]")
            .tenantId("tenant-a [GH-90000]")
            .skillId("skill-1 [GH-90000]")
            .proposedVersion("v1 [GH-90000]")
            .build(); // GH-90000

        runPromise(() -> queue.enqueue(item)); // GH-90000
        ReviewItem escalated = runPromise(() -> queue.escalate("escalated-review [GH-90000]"));

        assertThat(escalated.getStatus()).isEqualTo(ReviewStatus.ESCALATED); // GH-90000
        assertThat(escalatedReviewId.get()).isEqualTo("escalated-review [GH-90000]");
    }
}