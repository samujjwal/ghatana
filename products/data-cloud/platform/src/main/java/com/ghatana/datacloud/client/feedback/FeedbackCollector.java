/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.datacloud.client.feedback;

import io.activej.promise.Promise;
import lombok.Builder;
import lombok.Value;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service Provider Interface for collecting and aggregating feedback events.
 *
 * <p>The FeedbackCollector serves as the central aggregation point for all
 * feedback signals entering the learning system. It provides buffering,
 * deduplication, and batching capabilities to efficiently process feedback.
 *
 * <h2>Collection Flow</h2>
 * <pre>
 *     Sources                    Collector                    Processing
 *     ───────                    ─────────                    ──────────
 *     
 *     User ────────┐
 *                  │
 *     System ──────┼────►  Buffer ────► Batch ────► LearningLoop
 *                  │          │
 *     External ────┘          ▼
 *                         Dedupe
 * </pre>
 *
 * <h2>Implementation Requirements</h2>
 * <ul>
 *   <li>Thread-safe for concurrent feedback submission</li>
 *   <li>Non-blocking with back-pressure support</li>
 *   <li>Configurable buffering and batching</li>
 *   <li>Observable via metrics</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose SPI for feedback collection and aggregation
 * @doc.layer core
 * @doc.pattern Producer-Consumer, Buffer
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 * @see FeedbackEvent
 * @see LearningLoop
 */
public interface FeedbackCollector {

    /**
     * Collects a single feedback event.
     *
     * @param event the feedback event to collect
     * @return Promise completing when the event is accepted
     */
    Promise<CollectionResult> collect(FeedbackEvent event);

    /**
     * Collects multiple feedback events in a batch.
     *
     * @param events the feedback events to collect
     * @return Promise containing batch collection results
     */
    Promise<BatchCollectionResult> collectBatch(List<FeedbackEvent> events);

    /**
     * Creates explicit positive feedback for a reference.
     *
     * @param referenceId the ID of the item receiving feedback
     * @param referenceType the type of the referenced item
     * @param tenantId the tenant ID
     * @param comment optional comment
     * @return Promise with collection result
     */
    default Promise<CollectionResult> recordPositive(
            String referenceId,
            FeedbackEvent.ReferenceType referenceType,
            String tenantId,
            String comment) {
        return collect(FeedbackEvent.builder()
                .referenceId(referenceId)
                .referenceType(referenceType)
                .tenantId(tenantId)
                .feedbackType(FeedbackEvent.FeedbackType.EXPLICIT)
                .source(FeedbackEvent.FeedbackSource.USER)
                .sentiment(FeedbackEvent.Sentiment.POSITIVE)
                .score(1.0)
                .comment(comment)
                .build());
    }

    /**
     * Creates explicit negative feedback for a reference.
     *
     * @param referenceId the ID of the item receiving feedback
     * @param referenceType the type of the referenced item
     * @param tenantId the tenant ID
     * @param comment optional comment
     * @param corrections optional corrections
     * @return Promise with collection result
     */
    default Promise<CollectionResult> recordNegative(
            String referenceId,
            FeedbackEvent.ReferenceType referenceType,
            String tenantId,
            String comment,
            Map<String, Object> corrections) {
        return collect(FeedbackEvent.builder()
                .referenceId(referenceId)
                .referenceType(referenceType)
                .tenantId(tenantId)
                .feedbackType(FeedbackEvent.FeedbackType.EXPLICIT)
                .source(FeedbackEvent.FeedbackSource.USER)
                .sentiment(FeedbackEvent.Sentiment.NEGATIVE)
                .score(-1.0)
                .comment(comment)
                .corrections(corrections)
                .build());
    }

    /**
     * Records an outcome observation for a prediction.
     *
     * @param predictionId the prediction ID
     * @param tenantId the tenant ID
     * @param expected the expected outcome
     * @param actual the actual outcome
     * @param accuracy accuracy score (0.0 to 1.0)
     * @return Promise with collection result
     */
    default Promise<CollectionResult> recordOutcome(
            String predictionId,
            String tenantId,
            String expected,
            String actual,
            double accuracy) {
        return collect(FeedbackEvent.builder()
                .referenceId(predictionId)
                .referenceType(FeedbackEvent.ReferenceType.PREDICTION)
                .tenantId(tenantId)
                .feedbackType(FeedbackEvent.FeedbackType.OUTCOME)
                .source(FeedbackEvent.FeedbackSource.SYSTEM)
                .expectedOutcome(expected)
                .outcome(actual)
                .score(accuracy)
                .sentiment(FeedbackEvent.Sentiment.fromScore(accuracy * 2 - 1))
                .build());
    }

    /**
     * Records implicit behavioral feedback.
     *
     * @param referenceId the referenced item ID
     * @param referenceType the type of referenced item
     * @param tenantId the tenant ID
     * @param behaviorType the type of behavior observed
     * @param value the behavior value (interpretation depends on type)
     * @return Promise with collection result
     */
    default Promise<CollectionResult> recordImplicit(
            String referenceId,
            FeedbackEvent.ReferenceType referenceType,
            String tenantId,
            String behaviorType,
            double value) {
        return collect(FeedbackEvent.builder()
                .referenceId(referenceId)
                .referenceType(referenceType)
                .tenantId(tenantId)
                .feedbackType(FeedbackEvent.FeedbackType.IMPLICIT)
                .source(FeedbackEvent.FeedbackSource.SYSTEM)
                .category(behaviorType)
                .score(value)
                .build());
    }

    /**
     * Gets pending (unprocessed) feedback events.
     *
     * @param limit maximum events to return
     * @return Promise containing pending events
     */
    Promise<List<FeedbackEvent>> getPending(int limit);

    /**
     * Gets pending feedback for a specific tenant.
     *
     * @param tenantId the tenant ID
     * @param limit maximum events to return
     * @return Promise containing pending events
     */
    Promise<List<FeedbackEvent>> getPendingForTenant(String tenantId, int limit);

    /**
     * Marks events as processed.
     *
     * @param eventIds the event IDs to mark
     * @return Promise indicating success
     */
    Promise<Integer> markProcessed(List<String> eventIds);

    /**
     * Gets feedback history for a reference.
     *
     * @param referenceId the referenced item ID
     * @param since only include feedback after this time
     * @param limit maximum events to return
     * @return Promise containing feedback history
     */
    Promise<List<FeedbackEvent>> getHistoryFor(String referenceId, Instant since, int limit);

    /**
     * Aggregates feedback for a reference into a summary.
     *
     * @param referenceId the referenced item ID
     * @return Promise containing the aggregated summary
     */
    Promise<FeedbackSummary> aggregate(String referenceId);

    /**
     * Gets collector statistics.
     *
     * @return Promise containing statistics
     */
    Promise<CollectorStatistics> getStatistics();

    /**
     * Flushes the collector buffer.
     *
     * @return Promise completing when buffer is flushed
     */
    Promise<Void> flush();

    // ═══════════════════════════════════════════════════════════════════════════
    // Result Types
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Result of collecting a single feedback event.
     */
    record CollectionResult(
            String eventId,
            CollectionStatus status,
            String message,
            int bufferSize
    ) {
        public enum CollectionStatus {
            /** Event accepted for processing */
            ACCEPTED,
            /** Event accepted but deduplicated */
            DEDUPLICATED,
            /** Event rejected due to buffer full */
            REJECTED_BUFFER_FULL,
            /** Event rejected due to validation failure */
            REJECTED_INVALID,
            /** Event rejected due to rate limiting */
            REJECTED_RATE_LIMITED
        }

        public boolean isAccepted() {
            return status == CollectionStatus.ACCEPTED
                    || status == CollectionStatus.DEDUPLICATED;
        }
    }

    /**
     * Result of collecting a batch of feedback events.
     */
    record BatchCollectionResult(
            int totalSubmitted,
            int accepted,
            int deduplicated,
            int rejected,
            List<CollectionResult> individualResults,
            long processingTimeMs
    ) {
        public double acceptanceRate() {
            return totalSubmitted > 0
                    ? (double) (accepted + deduplicated) / totalSubmitted
                    : 0.0;
        }
    }

    /**
     * Aggregated summary of feedback for a reference.
     */
    @Value
    @Builder
    class FeedbackSummary {
        String referenceId;
        int totalFeedbackCount;
        int positiveCount;
        int negativeCount;
        int neutralCount;
        double averageScore;
        double sentimentScore;
        Map<FeedbackEvent.FeedbackType, Integer> byType;
        Map<FeedbackEvent.FeedbackSource, Integer> bySource;
        Instant firstFeedbackAt;
        Instant lastFeedbackAt;
        List<String> commonTags;
        Optional<String> mostRecentComment;

        /**
         * Calculates the overall satisfaction rate.
         *
         * @return ratio of positive to total (excluding neutral)
         */
        public double getSatisfactionRate() {
            int rated = positiveCount + negativeCount;
            return rated > 0 ? (double) positiveCount / rated : 0.5;
        }

        /**
         * Calculates the net promoter score style metric.
         *
         * @return NPS-like score (-100 to 100)
         */
        public double getNetScore() {
            return totalFeedbackCount > 0
                    ? ((double) (positiveCount - negativeCount) / totalFeedbackCount) * 100
                    : 0.0;
        }
    }

    /**
     * Statistics for the feedback collector.
     */
    @Value
    @Builder
    class CollectorStatistics {
        long totalCollected;
        long totalProcessed;
        long totalRejected;
        int currentBufferSize;
        int maxBufferSize;
        double bufferUtilization;
        long duplicatesFiltered;
        Duration avgProcessingTime;
        Map<FeedbackEvent.FeedbackType, Long> countByType;
        Map<FeedbackEvent.Sentiment, Long> countBySentiment;
        Instant lastCollectionTime;
        Instant lastFlushTime;
    }
}
