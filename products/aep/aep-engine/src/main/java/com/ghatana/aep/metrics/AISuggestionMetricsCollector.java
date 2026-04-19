/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.metrics;

import com.ghatana.platform.observability.Metrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics collector for AI suggestion effectiveness.
 * <p>
 * Tracks click-through rate, adoption, and accuracy of AI-powered suggestions
 * (e.g., stage suggestions, pipeline recommendations).
 *
 * @doc.type class
 * @doc.purpose Track AI suggestion effectiveness metrics
 * @doc.layer product
 * @doc.pattern Observer
 */
public class AISuggestionMetricsCollector {

    private static final Logger logger = LoggerFactory.getLogger(AISuggestionMetricsCollector.class);

    private final Metrics metrics;
    private final Map<String, SuggestionStats> statsByType = new ConcurrentHashMap<>();

    public AISuggestionMetricsCollector(Metrics metrics) {
        this.metrics = metrics;
    }

    /**
     * Record that a suggestion was shown to the user.
     *
     * @param suggestionType type of suggestion (e.g., "stage", "pipeline")
     * @param suggestionId unique identifier for the suggestion
     * @param context additional context (e.g., event types, use case)
     */
    public void recordSuggestionShown(String suggestionType, String suggestionId, Map<String, Object> context) {
        SuggestionStats stats = statsByType.computeIfAbsent(suggestionType, ignored -> new SuggestionStats());
        stats.incrementShown();
        counter("ai.suggestion.shown", "type", suggestionType).increment();
        logger.debug("Suggestion shown: type={}, id={}", suggestionType, suggestionId);
    }

    /**
     * Record that a suggestion was accepted by the user.
     *
     * @param suggestionType type of suggestion
     * @param suggestionId unique identifier for the suggestion
     */
    public void recordSuggestionAccepted(String suggestionType, String suggestionId) {
        SuggestionStats stats = statsByType.computeIfAbsent(suggestionType, ignored -> new SuggestionStats());
        stats.incrementAccepted();
        counter("ai.suggestion.accepted", "type", suggestionType).increment();
        logger.debug("Suggestion accepted: type={}, id={}", suggestionType, suggestionId);
    }

    /**
     * Record that a suggestion was rejected by the user.
     *
     * @param suggestionType type of suggestion
     * @param suggestionId unique identifier for the suggestion
     * @param reason reason for rejection (e.g., "not_relevant", "incorrect")
     */
    public void recordSuggestionRejected(String suggestionType, String suggestionId, String reason) {
        SuggestionStats stats = statsByType.computeIfAbsent(suggestionType, ignored -> new SuggestionStats());
        stats.incrementRejected();
        counter("ai.suggestion.rejected", "type", suggestionType, "reason", reason).increment();
        logger.debug("Suggestion rejected: type={}, id={}, reason={}", suggestionType, suggestionId, reason);
    }

    /**
     * Record that a suggestion led to a successful outcome.
     *
     * @param suggestionType type of suggestion
     * @param suggestionId unique identifier for the suggestion
     */
    public void recordSuggestionSuccess(String suggestionType, String suggestionId) {
        SuggestionStats stats = statsByType.computeIfAbsent(suggestionType, ignored -> new SuggestionStats());
        stats.incrementSuccess();
        counter("ai.suggestion.success", "type", suggestionType).increment();
        logger.debug("Suggestion success: type={}, id={}", suggestionType, suggestionId);
    }

    /**
     * Record that a suggestion led to a failure or error.
     *
     * @param suggestionType type of suggestion
     * @param suggestionId unique identifier for the suggestion
     */
    public void recordSuggestionFailure(String suggestionType, String suggestionId) {
        SuggestionStats stats = statsByType.computeIfAbsent(suggestionType, ignored -> new SuggestionStats());
        stats.incrementFailure();
        counter("ai.suggestion.failure", "type", suggestionType).increment();
        logger.debug("Suggestion failure: type={}, id={}", suggestionType, suggestionId);
    }

    private Counter counter(String name, String... tags) {
        MeterRegistry registry = metrics.getRegistry();
        if (registry == null) {
            return Counter.builder(name).tags(tags).register(new SimpleMeterRegistry());
        }
        return Counter.builder(name)
            .tags(tags)
            .register(registry);
    }

    /**
     * Get statistics for a suggestion type.
     *
     * @param suggestionType type of suggestion
     * @return statistics for the suggestion type
     */
    public SuggestionStats getStats(String suggestionType) {
        return statsByType.getOrDefault(suggestionType, new SuggestionStats());
    }

    /**
     * Get click-through rate for a suggestion type.
     *
     * @param suggestionType type of suggestion
     * @return click-through rate (0.0 to 1.0)
     */
    public double getClickThroughRate(String suggestionType) {
        SuggestionStats stats = statsByType.getOrDefault(suggestionType, new SuggestionStats());
        long shown = stats.shown();
        if (shown == 0) return 0.0;
        return (double) stats.accepted() / shown;
    }

    /**
     * Get adoption rate for a suggestion type.
     *
     * @param suggestionType type of suggestion
     * @return adoption rate (0.0 to 1.0)
     */
    public double getAdoptionRate(String suggestionType) {
        SuggestionStats stats = statsByType.getOrDefault(suggestionType, new SuggestionStats());
        long accepted = stats.accepted();
        if (accepted == 0) return 0.0;
        return (double) stats.success() / accepted;
    }

    /**
     * Statistics for a suggestion type.
     */
    public static class SuggestionStats {
        private final AtomicLong shown = new AtomicLong(0);
        private final AtomicLong accepted = new AtomicLong(0);
        private final AtomicLong rejected = new AtomicLong(0);
        private final AtomicLong success = new AtomicLong(0);
        private final AtomicLong failure = new AtomicLong(0);

        public SuggestionStats() {}

        public void incrementShown() { shown.incrementAndGet(); }
        public void incrementAccepted() { accepted.incrementAndGet(); }
        public void incrementRejected() { rejected.incrementAndGet(); }
        public void incrementSuccess() { success.incrementAndGet(); }
        public void incrementFailure() { failure.incrementAndGet(); }

        public long shown() { return shown.get(); }
        public long accepted() { return accepted.get(); }
        public long rejected() { return rejected.get(); }
        public long success() { return success.get(); }
        public long failure() { return failure.get(); }

        public double acceptanceRate() {
            long total = shown.get();
            return total > 0 ? (double) accepted.get() / total : 0.0;
        }

        public double successRate() {
            long total = accepted.get();
            return total > 0 ? (double) success.get() / total : 0.0;
        }
    }
}
