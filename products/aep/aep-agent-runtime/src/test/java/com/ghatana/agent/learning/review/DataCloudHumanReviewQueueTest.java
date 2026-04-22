package com.ghatana.agent.learning.review;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("DataCloudHumanReviewQueue [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class DataCloudHumanReviewQueueTest extends EventloopTestBase {

    @Mock
    private DataCloudClient dataCloudClient;

    @Mock
    private ReviewNotificationSpi notificationSpi;

    @Captor
    private ArgumentCaptor<Map<String, Object>> recordCaptor;

    private DataCloudHumanReviewQueue queue;

    @BeforeEach
    void setUp() { // GH-90000
        queue = new DataCloudHumanReviewQueue(dataCloudClient, notificationSpi); // GH-90000
    }

    @Test
    @DisplayName("enqueue persists review items in the shared collection [GH-90000]")
    void enqueuePersistsReviewItem() { // GH-90000
        ReviewItem item = ReviewItem.builder() // GH-90000
            .reviewId("review-1 [GH-90000]")
            .tenantId("tenant-a [GH-90000]")
            .skillId("skill-1 [GH-90000]")
            .proposedVersion("v2 [GH-90000]")
            .itemType(ReviewItemType.POLICY) // GH-90000
            .confidenceScore(0.42) // GH-90000
            .evaluationSummary("Needs review [GH-90000]")
            .context(Map.of("reason", "low-confidence")) // GH-90000
            .build(); // GH-90000

        when(dataCloudClient.save(eq(DataCloudHumanReviewQueue.STORAGE_TENANT), // GH-90000
                eq(DataCloudHumanReviewQueue.COLLECTION), any())) // GH-90000
            .thenReturn(Promise.of(entityFrom(item, ReviewStatus.PENDING, null, null))); // GH-90000

        ReviewItem persisted = runPromise(() -> queue.enqueue(item)); // GH-90000

        assertThat(persisted.getReviewId()).isEqualTo("review-1 [GH-90000]");
        verify(dataCloudClient).save(eq(DataCloudHumanReviewQueue.STORAGE_TENANT), // GH-90000
            eq(DataCloudHumanReviewQueue.COLLECTION), recordCaptor.capture()); // GH-90000
        assertThat(recordCaptor.getValue()).containsEntry("tenantId", "tenant-a"); // GH-90000
        assertThat(recordCaptor.getValue()).containsEntry("status", ReviewStatus.PENDING.name()); // GH-90000
        verify(notificationSpi).onItemEnqueued(persisted); // GH-90000
    }

    @Test
    @DisplayName("getPending filters by tenant and confidence [GH-90000]")
    void getPendingFiltersByTenantAndConfidence() { // GH-90000
        ReviewItem pending = ReviewItem.builder() // GH-90000
            .reviewId("review-pending [GH-90000]")
            .tenantId("tenant-a [GH-90000]")
            .skillId("skill-1 [GH-90000]")
            .proposedVersion("v2 [GH-90000]")
            .confidenceScore(0.30) // GH-90000
            .build(); // GH-90000
        ReviewItem wrongTenant = ReviewItem.builder() // GH-90000
            .reviewId("review-other [GH-90000]")
            .tenantId("tenant-b [GH-90000]")
            .skillId("skill-2 [GH-90000]")
            .proposedVersion("v3 [GH-90000]")
            .confidenceScore(0.20) // GH-90000
            .build(); // GH-90000
        ReviewItem approved = ReviewItem.builder() // GH-90000
            .reviewId("review-approved [GH-90000]")
            .tenantId("tenant-a [GH-90000]")
            .skillId("skill-3 [GH-90000]")
            .proposedVersion("v4 [GH-90000]")
            .confidenceScore(0.25) // GH-90000
            .status(ReviewStatus.APPROVED) // GH-90000
            .decision(ReviewDecision.approve("alice", "looks good")) // GH-90000
            .decidedAt(Instant.now()) // GH-90000
            .build(); // GH-90000

        when(dataCloudClient.query(eq(DataCloudHumanReviewQueue.STORAGE_TENANT), // GH-90000
                eq(DataCloudHumanReviewQueue.COLLECTION), any())) // GH-90000
            .thenReturn(Promise.of(List.of( // GH-90000
                entityFrom(pending, ReviewStatus.PENDING, null, null), // GH-90000
                entityFrom(wrongTenant, ReviewStatus.PENDING, null, null), // GH-90000
                entityFrom(approved, ReviewStatus.APPROVED, approved.getDecision(), approved.getDecidedAt()) // GH-90000
            )));

        List<ReviewItem> items = runPromise(() -> queue.getPending( // GH-90000
            new ReviewFilter("tenant-a", ReviewItemType.POLICY, 0.35, null, 10))); // GH-90000

        assertThat(items).singleElement().extracting(ReviewItem::getReviewId) // GH-90000
            .isEqualTo("review-pending [GH-90000]");
    }

    @Test
    @DisplayName("approve persists the decision and notifies reviewers [GH-90000]")
    void approvePersistsDecision() { // GH-90000
        ReviewItem item = ReviewItem.builder() // GH-90000
            .reviewId("review-approve [GH-90000]")
            .tenantId("tenant-a [GH-90000]")
            .skillId("skill-1 [GH-90000]")
            .proposedVersion("v2 [GH-90000]")
            .confidenceScore(0.55) // GH-90000
            .build(); // GH-90000
        ReviewDecision decision = new ReviewDecision("alice", "approved", Instant.now(), "ship it"); // GH-90000

        when(dataCloudClient.findById(eq(DataCloudHumanReviewQueue.STORAGE_TENANT), // GH-90000
                eq(DataCloudHumanReviewQueue.COLLECTION), eq("review-approve [GH-90000]")))
            .thenReturn(Promise.of(Optional.of(entityFrom(item, ReviewStatus.PENDING, null, null)))); // GH-90000
        when(dataCloudClient.save(eq(DataCloudHumanReviewQueue.STORAGE_TENANT), // GH-90000
                eq(DataCloudHumanReviewQueue.COLLECTION), any())) // GH-90000
            .thenReturn(Promise.of(entityFrom(item, ReviewStatus.APPROVED, decision, decision.decidedAt()))); // GH-90000

        ReviewItem approved = runPromise(() -> queue.approve("review-approve", decision)); // GH-90000

        assertThat(approved.getStatus()).isEqualTo(ReviewStatus.APPROVED); // GH-90000
        assertThat(approved.getDecision()).isEqualTo(decision); // GH-90000
        verify(notificationSpi).onItemApproved(approved); // GH-90000
    }

    @Test
    @DisplayName("findOverdue only returns active items older than the cutoff [GH-90000]")
    void findOverdueReturnsOnlyActiveExpiredItems() { // GH-90000
        Instant oldTimestamp = Instant.now().minusSeconds(7200); // GH-90000
        Instant recentTimestamp = Instant.now().minusSeconds(60); // GH-90000

        ReviewItem overdue = ReviewItem.builder() // GH-90000
            .reviewId("review-overdue [GH-90000]")
            .tenantId("tenant-a [GH-90000]")
            .skillId("skill-1 [GH-90000]")
            .proposedVersion("v2 [GH-90000]")
            .createdAt(oldTimestamp) // GH-90000
            .build(); // GH-90000
        ReviewItem fresh = ReviewItem.builder() // GH-90000
            .reviewId("review-fresh [GH-90000]")
            .tenantId("tenant-a [GH-90000]")
            .skillId("skill-2 [GH-90000]")
            .proposedVersion("v3 [GH-90000]")
            .createdAt(recentTimestamp) // GH-90000
            .build(); // GH-90000
        ReviewItem rejected = ReviewItem.builder() // GH-90000
            .reviewId("review-rejected [GH-90000]")
            .tenantId("tenant-a [GH-90000]")
            .skillId("skill-3 [GH-90000]")
            .proposedVersion("v4 [GH-90000]")
            .createdAt(oldTimestamp) // GH-90000
            .status(ReviewStatus.REJECTED) // GH-90000
            .build(); // GH-90000

        when(dataCloudClient.query(eq(DataCloudHumanReviewQueue.STORAGE_TENANT), // GH-90000
                eq(DataCloudHumanReviewQueue.COLLECTION), any())) // GH-90000
            .thenReturn(Promise.of(List.of( // GH-90000
                entityFrom(overdue, ReviewStatus.PENDING, null, null), // GH-90000
                entityFrom(fresh, ReviewStatus.PENDING, null, null), // GH-90000
                entityFrom(rejected, ReviewStatus.REJECTED, null, oldTimestamp) // GH-90000
            )));

        List<ReviewItem> overdueItems = runPromise(() -> queue.findOverdue(1800, "tenant-a")); // GH-90000

        assertThat(overdueItems).singleElement().extracting(ReviewItem::getReviewId) // GH-90000
            .isEqualTo("review-overdue [GH-90000]");
    }

    @Test
    @DisplayName("escalate persists the updated status and notifies listeners [GH-90000]")
    void escalatePersistsUpdatedStatus() { // GH-90000
        ReviewItem item = ReviewItem.builder() // GH-90000
            .reviewId("review-escalate [GH-90000]")
            .tenantId("tenant-a [GH-90000]")
            .skillId("skill-1 [GH-90000]")
            .proposedVersion("v2 [GH-90000]")
            .confidenceScore(0.55) // GH-90000
            .build(); // GH-90000

        when(dataCloudClient.findById(eq(DataCloudHumanReviewQueue.STORAGE_TENANT), // GH-90000
                eq(DataCloudHumanReviewQueue.COLLECTION), eq("review-escalate [GH-90000]")))
            .thenReturn(Promise.of(Optional.of(entityFrom(item, ReviewStatus.PENDING, null, null)))); // GH-90000
        when(dataCloudClient.save(eq(DataCloudHumanReviewQueue.STORAGE_TENANT), // GH-90000
                eq(DataCloudHumanReviewQueue.COLLECTION), any())) // GH-90000
            .thenReturn(Promise.of(entityFrom(item, ReviewStatus.ESCALATED, null, Instant.now()))); // GH-90000

        ReviewItem escalated = runPromise(() -> queue.escalate("review-escalate [GH-90000]"));

        assertThat(escalated.getStatus()).isEqualTo(ReviewStatus.ESCALATED); // GH-90000
        verify(notificationSpi).onItemEscalated(escalated); // GH-90000
    }

    @Test
    @DisplayName("getPending marks explicitly expired items as EXPIRED and excludes them [GH-90000]")
    void getPendingExpiresElapsedItems() { // GH-90000
        Instant createdAt = Instant.now().minusSeconds(3600); // GH-90000
        Instant expiresAt = Instant.now().minusSeconds(60); // GH-90000
        ReviewItem expired = ReviewItem.builder() // GH-90000
            .reviewId("review-expired [GH-90000]")
            .tenantId("tenant-a [GH-90000]")
            .skillId("skill-1 [GH-90000]")
            .proposedVersion("v2 [GH-90000]")
            .createdAt(createdAt) // GH-90000
            .expiresAt(expiresAt) // GH-90000
            .build(); // GH-90000

        when(dataCloudClient.query(eq(DataCloudHumanReviewQueue.STORAGE_TENANT), // GH-90000
                eq(DataCloudHumanReviewQueue.COLLECTION), any())) // GH-90000
            .thenReturn(Promise.of(List.of(entityFrom(expired, ReviewStatus.PENDING, null, null)))); // GH-90000
        when(dataCloudClient.save(eq(DataCloudHumanReviewQueue.STORAGE_TENANT), // GH-90000
                eq(DataCloudHumanReviewQueue.COLLECTION), any())) // GH-90000
            .thenReturn(Promise.of(entityFrom(expired, ReviewStatus.EXPIRED, null, Instant.now()))); // GH-90000

        List<ReviewItem> pending = runPromise(() -> queue.getPending(ReviewFilter.forTenant("tenant-a [GH-90000]")));

        assertThat(pending).isEmpty(); // GH-90000
        verify(dataCloudClient).save(eq(DataCloudHumanReviewQueue.STORAGE_TENANT), // GH-90000
            eq(DataCloudHumanReviewQueue.COLLECTION), recordCaptor.capture()); // GH-90000
        assertThat(recordCaptor.getValue()).containsEntry("status", ReviewStatus.EXPIRED.name()); // GH-90000
        assertThat(recordCaptor.getValue()).containsEntry("expiresAt", expiresAt.toString()); // GH-90000
    }

    private DataCloudClient.Entity entityFrom(ReviewItem item, // GH-90000
                                              ReviewStatus status,
                                              ReviewDecision decision,
                                              Instant decidedAt) {
        Map<String, Object> data = new LinkedHashMap<>(); // GH-90000
        data.put("id", item.getReviewId()); // GH-90000
        data.put("reviewId", item.getReviewId()); // GH-90000
        data.put("tenantId", item.getTenantId()); // GH-90000
        data.put("skillId", item.getSkillId()); // GH-90000
        data.put("proposedVersion", item.getProposedVersion()); // GH-90000
        data.put("itemType", item.getItemType().name()); // GH-90000
        data.put("confidenceScore", item.getConfidenceScore()); // GH-90000
        data.put("context", item.getContext()); // GH-90000
        data.put("createdAt", item.getCreatedAt().toString()); // GH-90000
        if (item.getExpiresAt() != null) { // GH-90000
            data.put("expiresAt", item.getExpiresAt().toString()); // GH-90000
        }
        data.put("status", status.name()); // GH-90000
        if (item.getEvaluationSummary() != null) { // GH-90000
            data.put("evaluationSummary", item.getEvaluationSummary()); // GH-90000
        }
        if (decision != null) { // GH-90000
            data.put("decision", Map.of( // GH-90000
                "reviewer", decision.reviewer(), // GH-90000
                "rationale", decision.rationale(), // GH-90000
                "decidedAt", decision.decidedAt().toString(), // GH-90000
                "notes", decision.notes() != null ? decision.notes() : "" // GH-90000
            ));
        }
        if (decidedAt != null) { // GH-90000
            data.put("decidedAt", decidedAt.toString()); // GH-90000
        }
        return new DataCloudClient.Entity( // GH-90000
            item.getReviewId(), // GH-90000
            DataCloudHumanReviewQueue.COLLECTION,
            data,
            item.getCreatedAt(), // GH-90000
            decidedAt != null ? decidedAt : item.getCreatedAt(), // GH-90000
            1L
        );
    }
}