package com.ghatana.datacloud.client.feedback;

import io.activej.promise.Promise;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Default implementation of FeedbackCollector.
 *
 * @doc.type class
 * @doc.purpose Default feedback collection implementation
 * @doc.layer core
 */
@Slf4j
@RequiredArgsConstructor
public class DefaultFeedbackCollector implements FeedbackCollector {

    private final ConcurrentLinkedQueue<FeedbackEvent> pendingEvents = new ConcurrentLinkedQueue<>();
    private final List<FeedbackEvent> processedEvents = Collections.synchronizedList(new ArrayList<>());
    private final AtomicLong collectedCount = new AtomicLong();
    private final AtomicLong processedCount = new AtomicLong();

    @Override
    public Promise<CollectionResult> collect(FeedbackEvent event) {
        pendingEvents.add(event);
        collectedCount.incrementAndGet();
        return Promise.of(new CollectionResult(
                event.getId(),
                CollectionResult.CollectionStatus.ACCEPTED,
                "Feedback collected",
                pendingEvents.size()
        ));
    }

    @Override
    public Promise<BatchCollectionResult> collectBatch(List<FeedbackEvent> events) {
        pendingEvents.addAll(events);
        collectedCount.addAndGet(events.size());

        List<CollectionResult> results = events.stream()
                .map(e -> new CollectionResult(
                        e.getId(),
                        CollectionResult.CollectionStatus.ACCEPTED,
                        "Feedback collected",
                        pendingEvents.size()
                ))
                .collect(Collectors.toList());

        return Promise.of(new BatchCollectionResult(
                events.size(),
                events.size(),
                0,
                0,
                results,
                0
        ));
    }

    @Override
    public Promise<List<FeedbackEvent>> getPending(int limit) {
        List<FeedbackEvent> result = new ArrayList<>();
        int count = 0;
        for (FeedbackEvent event : pendingEvents) {
            if (count >= limit) break;
            result.add(event);
            count++;
        }
        return Promise.of(result);
    }

    @Override
    public Promise<List<FeedbackEvent>> getPendingForTenant(String tenantId, int limit) {
        List<FeedbackEvent> result = pendingEvents.stream()
                .filter(e -> tenantId.equals(e.getTenantId()))
                .limit(limit)
                .collect(Collectors.toList());
        return Promise.of(result);
    }

    @Override
    public Promise<Integer> markProcessed(List<String> eventIds) {
        int count = 0;
        List<FeedbackEvent> toRemove = new ArrayList<>();
        for (FeedbackEvent event : pendingEvents) {
            if (eventIds.contains(event.getId())) {
                toRemove.add(event);
                processedEvents.add(event);
                count++;
            }
        }
        pendingEvents.removeAll(toRemove);
        processedCount.addAndGet(count);
        return Promise.of(count);
    }

    @Override
    public Promise<List<FeedbackEvent>> getHistoryFor(String referenceId, Instant since, int limit) {
        List<FeedbackEvent> history = processedEvents.stream()
                .filter(e -> referenceId.equals(e.getReferenceId()))
                .filter(e -> since == null || e.getTimestamp().isAfter(since))
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .limit(limit)
                .collect(Collectors.toList());
        return Promise.of(history);
    }

    @Override
    public Promise<FeedbackSummary> aggregate(String referenceId) {
        List<FeedbackEvent> events = processedEvents.stream()
                .filter(e -> referenceId.equals(e.getReferenceId()))
                .collect(Collectors.toList());

        int total = events.size();
        double avgScore = events.stream().mapToDouble(FeedbackEvent::getScore).average().orElse(0.0);

        int positive = (int) events.stream().filter(e -> e.getSentiment() == FeedbackEvent.Sentiment.POSITIVE).count();
        int negative = (int) events.stream().filter(e -> e.getSentiment() == FeedbackEvent.Sentiment.NEGATIVE).count();
        int neutral = (int) events.stream().filter(e -> e.getSentiment() == FeedbackEvent.Sentiment.NEUTRAL).count();

        return Promise.of(FeedbackSummary.builder()
                .referenceId(referenceId)
                .totalFeedbackCount(total)
                .positiveCount(positive)
                .negativeCount(negative)
                .neutralCount(neutral)
                .averageScore(avgScore)
                .sentimentScore(avgScore) // Simplified
                .byType(new HashMap<>()) // Simplified
                .bySource(new HashMap<>()) // Simplified
                .firstFeedbackAt(events.isEmpty() ? null : events.get(events.size() - 1).getTimestamp())
                .lastFeedbackAt(events.isEmpty() ? null : events.get(0).getTimestamp())
                .commonTags(Collections.emptyList())
                .mostRecentComment(events.stream().map(FeedbackEvent::getComment).filter(Objects::nonNull).findFirst())
                .build());
    }

    @Override
    public Promise<CollectorStatistics> getStatistics() {
        return Promise.of(CollectorStatistics.builder()
                .totalCollected(collectedCount.get())
                .totalProcessed(processedCount.get())
                .totalRejected(0)
                .currentBufferSize(pendingEvents.size())
                .maxBufferSize(Integer.MAX_VALUE)
                .bufferUtilization(0.0)
                .duplicatesFiltered(0)
                .avgProcessingTime(Duration.ZERO)
                .countByType(new HashMap<>())
                .countBySentiment(new HashMap<>())
                .lastCollectionTime(Instant.now())
                .lastFlushTime(Instant.now())
                .build());
    }

    @Override
    public Promise<Void> flush() {
        return Promise.complete();
    }
}

