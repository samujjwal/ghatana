package com.ghatana.agent.learning.review;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

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

    @Test
    @DisplayName("escalate notifies the configured review notification SPI")
    void escalateNotifiesReviewNotificationSpi() { 
        AtomicReference<String> escalatedReviewId = new AtomicReference<>(); 
        ReviewNotificationSpi notificationSpi = new ReviewNotificationSpi() { 
            @Override public void onItemEnqueued(ReviewItem item) {} 
            @Override public void onItemApproved(ReviewItem item) {} 
            @Override public void onItemRejected(ReviewItem item) {} 
            @Override public void onItemEscalated(ReviewItem item) { 
                escalatedReviewId.set(item.getReviewId()); 
            }
        };

        InMemoryHumanReviewQueue queue = new InMemoryHumanReviewQueue(notificationSpi); 
        ReviewItem item = ReviewItem.builder() 
            .reviewId("escalated-review")
            .tenantId("tenant-a")
            .skillId("skill-1")
            .proposedVersion("v1")
            .build(); 

        runPromise(() -> queue.enqueue(item)); 
        ReviewItem escalated = runPromise(() -> queue.escalate("escalated-review"));

        assertThat(escalated.getStatus()).isEqualTo(ReviewStatus.ESCALATED); 
        assertThat(escalatedReviewId.get()).isEqualTo("escalated-review");
    }
}