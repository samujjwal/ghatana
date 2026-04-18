package com.ghatana.agent.learning.review;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@DisplayName("DataCloudHumanReviewQueue Performance")
@ExtendWith(MockitoExtension.class)
class DataCloudHumanReviewQueuePerformanceTest extends EventloopTestBase {

    @Mock
    private DataCloudClient dataCloudClient;

    private DataCloudHumanReviewQueue queue;

    @BeforeEach
    void setUp() {
        queue = new DataCloudHumanReviewQueue(dataCloudClient, ReviewNotificationSpi.NOOP);
    }

    @Test
    @DisplayName("enqueue completes within the latency budget")
    void enqueueCompletesWithinLatencyBudget() {
        ReviewItem item = reviewItem("review-latency");
        when(dataCloudClient.save(eq(DataCloudHumanReviewQueue.STORAGE_TENANT),
            eq(DataCloudHumanReviewQueue.COLLECTION), any()))
            .thenReturn(Promise.of(entityFrom(item)));

        ReviewItem persisted = runPromise(() -> queue.enqueue(item));
        long medianMillis = medianMillis(() -> runPromise(() -> queue.enqueue(reviewItem("review-latency"))), 5);

        assertThat(persisted.getReviewId()).isEqualTo("review-latency");
        assertThat(medianMillis).isLessThan(50L);
    }

    @Test
    @DisplayName("getPending returns 1000 pending items within the latency budget")
    void getPendingReturnsThousandItemsWithinLatencyBudget() {
        List<DataCloudClient.Entity> entities = IntStream.range(0, 1_000)
            .mapToObj(index -> entityFrom(reviewItem("review-" + index)))
            .toList();
        when(dataCloudClient.query(eq(DataCloudHumanReviewQueue.STORAGE_TENANT),
            eq(DataCloudHumanReviewQueue.COLLECTION), any()))
            .thenReturn(Promise.of(entities));

        List<ReviewItem> pending = runPromise(() -> queue.getPending(
            new ReviewFilter("tenant-a", null, null, null, 1_000)));
        long medianMillis = medianMillis(() -> runPromise(() -> queue.getPending(
            new ReviewFilter("tenant-a", null, null, null, 1_000))), 5);

        assertThat(pending).hasSize(1_000);
        assertThat(medianMillis).isLessThan(100L);
    }

    private long medianMillis(Supplier<?> operation, int iterations) {
        operation.get();
        long[] timings = new long[iterations];
        for (int index = 0; index < iterations; index++) {
            long startedAt = System.nanoTime();
            operation.get();
            timings[index] = (System.nanoTime() - startedAt) / 1_000_000L;
        }
        java.util.Arrays.sort(timings);
        return timings[iterations / 2];
    }

    private ReviewItem reviewItem(String reviewId) {
        return ReviewItem.builder()
            .reviewId(reviewId)
            .tenantId("tenant-a")
            .skillId("skill-1")
            .proposedVersion("v2")
            .itemType(ReviewItemType.POLICY)
            .confidenceScore(0.25)
            .context(Map.of("reason", "latency-check"))
            .createdAt(Instant.parse("2026-04-17T00:00:00Z"))
            .build();
    }

    private DataCloudClient.Entity entityFrom(ReviewItem item) {
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
        data.put("status", ReviewStatus.PENDING.name());
        return new DataCloudClient.Entity(
            item.getReviewId(),
            DataCloudHumanReviewQueue.COLLECTION,
            data,
            item.getCreatedAt(),
            item.getCreatedAt(),
            1L
        );
    }
}