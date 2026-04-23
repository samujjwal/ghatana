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
@ExtendWith(MockitoExtension.class) // GH-90000
class DataCloudHumanReviewQueuePerformanceTest extends EventloopTestBase {

    @Mock
    private DataCloudClient dataCloudClient;

    private DataCloudHumanReviewQueue queue;

    @BeforeEach
    void setUp() { // GH-90000
        queue = new DataCloudHumanReviewQueue(dataCloudClient, ReviewNotificationSpi.NOOP); // GH-90000
    }

    @Test
    @DisplayName("enqueue completes within the latency budget")
    void enqueueCompletesWithinLatencyBudget() { // GH-90000
        ReviewItem item = reviewItem("review-latency");
        when(dataCloudClient.save(eq(DataCloudHumanReviewQueue.STORAGE_TENANT), // GH-90000
            eq(DataCloudHumanReviewQueue.COLLECTION), any())) // GH-90000
            .thenReturn(Promise.of(entityFrom(item))); // GH-90000

        ReviewItem persisted = runPromise(() -> queue.enqueue(item)); // GH-90000
        long medianMillis = medianMillis(() -> runPromise(() -> queue.enqueue(reviewItem("review-latency"))), 5);

        assertThat(persisted.getReviewId()).isEqualTo("review-latency");
        assertThat(medianMillis).isLessThan(50L); // GH-90000
    }

    @Test
    @DisplayName("getPending returns 1000 pending items within the latency budget")
    void getPendingReturnsThousandItemsWithinLatencyBudget() { // GH-90000
        List<DataCloudClient.Entity> entities = IntStream.range(0, 1_000) // GH-90000
            .mapToObj(index -> entityFrom(reviewItem("review-" + index))) // GH-90000
            .toList(); // GH-90000
        when(dataCloudClient.query(eq(DataCloudHumanReviewQueue.STORAGE_TENANT), // GH-90000
            eq(DataCloudHumanReviewQueue.COLLECTION), any())) // GH-90000
            .thenReturn(Promise.of(entities)); // GH-90000

        List<ReviewItem> pending = runPromise(() -> queue.getPending( // GH-90000
            new ReviewFilter("tenant-a", null, null, null, 1_000))); // GH-90000
        long medianMillis = medianMillis(() -> runPromise(() -> queue.getPending( // GH-90000
            new ReviewFilter("tenant-a", null, null, null, 1_000))), 5); // GH-90000

        assertThat(pending).hasSize(1_000); // GH-90000
        assertThat(medianMillis).isLessThan(100L); // GH-90000
    }

    private long medianMillis(Supplier<?> operation, int iterations) { // GH-90000
        operation.get(); // GH-90000
        long[] timings = new long[iterations];
        for (int index = 0; index < iterations; index++) { // GH-90000
            long startedAt = System.nanoTime(); // GH-90000
            operation.get(); // GH-90000
            timings[index] = (System.nanoTime() - startedAt) / 1_000_000L; // GH-90000
        }
        java.util.Arrays.sort(timings); // GH-90000
        return timings[iterations / 2];
    }

    private ReviewItem reviewItem(String reviewId) { // GH-90000
        return ReviewItem.builder() // GH-90000
            .reviewId(reviewId) // GH-90000
            .tenantId("tenant-a")
            .skillId("skill-1")
            .proposedVersion("v2")
            .itemType(ReviewItemType.POLICY) // GH-90000
            .confidenceScore(0.25) // GH-90000
            .context(Map.of("reason", "latency-check")) // GH-90000
            .createdAt(Instant.parse("2026-04-17T00:00:00Z"))
            .build(); // GH-90000
    }

    private DataCloudClient.Entity entityFrom(ReviewItem item) { // GH-90000
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
        data.put("status", ReviewStatus.PENDING.name()); // GH-90000
        return new DataCloudClient.Entity( // GH-90000
            item.getReviewId(), // GH-90000
            DataCloudHumanReviewQueue.COLLECTION,
            data,
            item.getCreatedAt(), // GH-90000
            item.getCreatedAt(), // GH-90000
            1L
        );
    }
}