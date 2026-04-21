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

@DisplayName("DataCloudHumanReviewQueue")
@ExtendWith(MockitoExtension.class)
class DataCloudHumanReviewQueueTest extends EventloopTestBase {

    @Mock
    private DataCloudClient dataCloudClient;

    @Mock
    private ReviewNotificationSpi notificationSpi;

    @Captor
    private ArgumentCaptor<Map<String, Object>> recordCaptor;

    private DataCloudHumanReviewQueue queue;

    @BeforeEach
    void setUp() {
        queue = new DataCloudHumanReviewQueue(dataCloudClient, notificationSpi);
    }

    @Test
    @DisplayName("enqueue persists review items in the shared collection")
    void enqueuePersistsReviewItem() {
        ReviewItem item = ReviewItem.builder()
            .reviewId("review-1")
            .tenantId("tenant-a")
            .skillId("skill-1")
            .proposedVersion("v2")
            .itemType(ReviewItemType.POLICY)
            .confidenceScore(0.42)
            .evaluationSummary("Needs review")
            .context(Map.of("reason", "low-confidence"))
            .build();

        when(dataCloudClient.save(eq(DataCloudHumanReviewQueue.STORAGE_TENANT),
                eq(DataCloudHumanReviewQueue.COLLECTION), any()))
            .thenReturn(Promise.of(entityFrom(item, ReviewStatus.PENDING, null, null)));

        ReviewItem persisted = runPromise(() -> queue.enqueue(item));

        assertThat(persisted.getReviewId()).isEqualTo("review-1");
        verify(dataCloudClient).save(eq(DataCloudHumanReviewQueue.STORAGE_TENANT),
            eq(DataCloudHumanReviewQueue.COLLECTION), recordCaptor.capture());
        assertThat(recordCaptor.getValue()).containsEntry("tenantId", "tenant-a");
        assertThat(recordCaptor.getValue()).containsEntry("status", ReviewStatus.PENDING.name());
        verify(notificationSpi).onItemEnqueued(persisted);
    }

    @Test
    @DisplayName("getPending filters by tenant and confidence")
    void getPendingFiltersByTenantAndConfidence() {
        ReviewItem pending = ReviewItem.builder()
            .reviewId("review-pending")
            .tenantId("tenant-a")
            .skillId("skill-1")
            .proposedVersion("v2")
            .confidenceScore(0.30)
            .build();
        ReviewItem wrongTenant = ReviewItem.builder()
            .reviewId("review-other")
            .tenantId("tenant-b")
            .skillId("skill-2")
            .proposedVersion("v3")
            .confidenceScore(0.20)
            .build();
        ReviewItem approved = ReviewItem.builder()
            .reviewId("review-approved")
            .tenantId("tenant-a")
            .skillId("skill-3")
            .proposedVersion("v4")
            .confidenceScore(0.25)
            .status(ReviewStatus.APPROVED)
            .decision(ReviewDecision.approve("alice", "looks good"))
            .decidedAt(Instant.now())
            .build();

        when(dataCloudClient.query(eq(DataCloudHumanReviewQueue.STORAGE_TENANT),
                eq(DataCloudHumanReviewQueue.COLLECTION), any()))
            .thenReturn(Promise.of(List.of(
                entityFrom(pending, ReviewStatus.PENDING, null, null),
                entityFrom(wrongTenant, ReviewStatus.PENDING, null, null),
                entityFrom(approved, ReviewStatus.APPROVED, approved.getDecision(), approved.getDecidedAt())
            )));

        List<ReviewItem> items = runPromise(() -> queue.getPending(
            new ReviewFilter("tenant-a", ReviewItemType.POLICY, 0.35, null, 10)));

        assertThat(items).singleElement().extracting(ReviewItem::getReviewId)
            .isEqualTo("review-pending");
    }

    @Test
    @DisplayName("approve persists the decision and notifies reviewers")
    void approvePersistsDecision() {
        ReviewItem item = ReviewItem.builder()
            .reviewId("review-approve")
            .tenantId("tenant-a")
            .skillId("skill-1")
            .proposedVersion("v2")
            .confidenceScore(0.55)
            .build();
        ReviewDecision decision = new ReviewDecision("alice", "approved", Instant.now(), "ship it");

        when(dataCloudClient.findById(eq(DataCloudHumanReviewQueue.STORAGE_TENANT),
                eq(DataCloudHumanReviewQueue.COLLECTION), eq("review-approve")))
            .thenReturn(Promise.of(Optional.of(entityFrom(item, ReviewStatus.PENDING, null, null))));
        when(dataCloudClient.save(eq(DataCloudHumanReviewQueue.STORAGE_TENANT),
                eq(DataCloudHumanReviewQueue.COLLECTION), any()))
            .thenReturn(Promise.of(entityFrom(item, ReviewStatus.APPROVED, decision, decision.decidedAt())));

        ReviewItem approved = runPromise(() -> queue.approve("review-approve", decision));

        assertThat(approved.getStatus()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(approved.getDecision()).isEqualTo(decision);
        verify(notificationSpi).onItemApproved(approved);
    }

    @Test
    @DisplayName("findOverdue only returns active items older than the cutoff")
    void findOverdueReturnsOnlyActiveExpiredItems() {
        Instant oldTimestamp = Instant.now().minusSeconds(7200);
        Instant recentTimestamp = Instant.now().minusSeconds(60);

        ReviewItem overdue = ReviewItem.builder()
            .reviewId("review-overdue")
            .tenantId("tenant-a")
            .skillId("skill-1")
            .proposedVersion("v2")
            .createdAt(oldTimestamp)
            .build();
        ReviewItem fresh = ReviewItem.builder()
            .reviewId("review-fresh")
            .tenantId("tenant-a")
            .skillId("skill-2")
            .proposedVersion("v3")
            .createdAt(recentTimestamp)
            .build();
        ReviewItem rejected = ReviewItem.builder()
            .reviewId("review-rejected")
            .tenantId("tenant-a")
            .skillId("skill-3")
            .proposedVersion("v4")
            .createdAt(oldTimestamp)
            .status(ReviewStatus.REJECTED)
            .build();

        when(dataCloudClient.query(eq(DataCloudHumanReviewQueue.STORAGE_TENANT),
                eq(DataCloudHumanReviewQueue.COLLECTION), any()))
            .thenReturn(Promise.of(List.of(
                entityFrom(overdue, ReviewStatus.PENDING, null, null),
                entityFrom(fresh, ReviewStatus.PENDING, null, null),
                entityFrom(rejected, ReviewStatus.REJECTED, null, oldTimestamp)
            )));

        List<ReviewItem> overdueItems = runPromise(() -> queue.findOverdue(1800, "tenant-a"));

        assertThat(overdueItems).singleElement().extracting(ReviewItem::getReviewId)
            .isEqualTo("review-overdue");
    }

    @Test
    @DisplayName("escalate persists the updated status and notifies listeners")
    void escalatePersistsUpdatedStatus() {
        ReviewItem item = ReviewItem.builder()
            .reviewId("review-escalate")
            .tenantId("tenant-a")
            .skillId("skill-1")
            .proposedVersion("v2")
            .confidenceScore(0.55)
            .build();

        when(dataCloudClient.findById(eq(DataCloudHumanReviewQueue.STORAGE_TENANT),
                eq(DataCloudHumanReviewQueue.COLLECTION), eq("review-escalate")))
            .thenReturn(Promise.of(Optional.of(entityFrom(item, ReviewStatus.PENDING, null, null))));
        when(dataCloudClient.save(eq(DataCloudHumanReviewQueue.STORAGE_TENANT),
                eq(DataCloudHumanReviewQueue.COLLECTION), any()))
            .thenReturn(Promise.of(entityFrom(item, ReviewStatus.ESCALATED, null, Instant.now())));

        ReviewItem escalated = runPromise(() -> queue.escalate("review-escalate"));

        assertThat(escalated.getStatus()).isEqualTo(ReviewStatus.ESCALATED);
        verify(notificationSpi).onItemEscalated(escalated);
    }

    @Test
    @DisplayName("getPending marks explicitly expired items as EXPIRED and excludes them")
    void getPendingExpiresElapsedItems() {
        Instant createdAt = Instant.now().minusSeconds(3600);
        Instant expiresAt = Instant.now().minusSeconds(60);
        ReviewItem expired = ReviewItem.builder()
            .reviewId("review-expired")
            .tenantId("tenant-a")
            .skillId("skill-1")
            .proposedVersion("v2")
            .createdAt(createdAt)
            .expiresAt(expiresAt)
            .build();

        when(dataCloudClient.query(eq(DataCloudHumanReviewQueue.STORAGE_TENANT),
                eq(DataCloudHumanReviewQueue.COLLECTION), any()))
            .thenReturn(Promise.of(List.of(entityFrom(expired, ReviewStatus.PENDING, null, null))));
        when(dataCloudClient.save(eq(DataCloudHumanReviewQueue.STORAGE_TENANT),
                eq(DataCloudHumanReviewQueue.COLLECTION), any()))
            .thenReturn(Promise.of(entityFrom(expired, ReviewStatus.EXPIRED, null, Instant.now())));

        List<ReviewItem> pending = runPromise(() -> queue.getPending(ReviewFilter.forTenant("tenant-a")));

        assertThat(pending).isEmpty();
        verify(dataCloudClient).save(eq(DataCloudHumanReviewQueue.STORAGE_TENANT),
            eq(DataCloudHumanReviewQueue.COLLECTION), recordCaptor.capture());
        assertThat(recordCaptor.getValue()).containsEntry("status", ReviewStatus.EXPIRED.name());
        assertThat(recordCaptor.getValue()).containsEntry("expiresAt", expiresAt.toString());
    }

    private DataCloudClient.Entity entityFrom(ReviewItem item,
                                              ReviewStatus status,
                                              ReviewDecision decision,
                                              Instant decidedAt) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", item.getReviewId());
        data.put("reviewId", item.getReviewId());
        data.put("tenantId", item.getTenantId());
        data.put("skillId", item.getSkillId());
        data.put("proposedVersion", item.getProposedVersion());
        data.put("itemType", item.getItemType().name());
        data.put("confidenceScore", item.getConfidenceScore());
        data.put("context", item.getContext());
        data.put("createdAt", item.getCreatedAt().toString());
        if (item.getExpiresAt() != null) {
            data.put("expiresAt", item.getExpiresAt().toString());
        }
        data.put("status", status.name());
        if (item.getEvaluationSummary() != null) {
            data.put("evaluationSummary", item.getEvaluationSummary());
        }
        if (decision != null) {
            data.put("decision", Map.of(
                "reviewer", decision.reviewer(),
                "rationale", decision.rationale(),
                "decidedAt", decision.decidedAt().toString(),
                "notes", decision.notes() != null ? decision.notes() : ""
            ));
        }
        if (decidedAt != null) {
            data.put("decidedAt", decidedAt.toString());
        }
        return new DataCloudClient.Entity(
            item.getReviewId(),
            DataCloudHumanReviewQueue.COLLECTION,
            data,
            item.getCreatedAt(),
            decidedAt != null ? decidedAt : item.getCreatedAt(),
            1L
        );
    }
}