/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC AI Module — AI Metrics Collector
 */
package com.ghatana.yappc.ai.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;

/**
 * Publishes AI-specific operational metrics to the Prometheus/Micrometer stack.
 *
 * <p>Captures the key observability signals for YAPPC AI feature quality and cost:
 * <ul>
 *   <li><b>LLM calls</b> — total, by model and feature, success/failure rates</li>
 *   <li><b>Latency</b> — p50/p95/p99 time-to-first-token and full-response latency</li>
 *   <li><b>Token usage</b> — input and output token distributions by model</li>
 *   <li><b>Cost</b> — estimated USD cost per request, cumulative by tenant/feature</li>
 *   <li><b>Cache</b> — semantic cache hit/miss rates</li>
 *   <li><b>Fallback</b> — how often the AI fallback path triggers</li>
 * </ul>
 *
 * <p><b>Metric Naming Convention (OBS-001)</b></p>
 * {@code yappc.ai.<domain>.<operation>}, lowercase, dot-separated.
 * <ul>
 *   <li>{@code yappc.ai.llm.requests.total} (counter)</li>
 *   <li>{@code yappc.ai.llm.latency.seconds} (timer)</li>
 *   <li>{@code yappc.ai.llm.tokens.input} (distribution summary)</li>
 *   <li>{@code yappc.ai.llm.tokens.output} (distribution summary)</li>
 *   <li>{@code yappc.ai.llm.cost.usd} (distribution summary)</li>
 *   <li>{@code yappc.ai.cache.hits.total} (counter)</li>
 *   <li>{@code yappc.ai.cache.misses.total} (counter)</li>
 *   <li>{@code yappc.ai.fallback.total} (counter)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose AI-specific metrics collector for YAPPC LLM quality, cost and latency signals
 * @doc.layer product
 * @doc.pattern Facade
 */
public final class AIMetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(AIMetricsCollector.class);

    // Metric name constants
    private static final String LLM_REQUESTS      = "yappc.ai.llm.requests.total";
    private static final String LLM_ERRORS         = "yappc.ai.llm.errors.total";
    private static final String LLM_LATENCY        = "yappc.ai.llm.latency.seconds";
    private static final String LLM_TOKENS_INPUT   = "yappc.ai.llm.tokens.input";
    private static final String LLM_TOKENS_OUTPUT  = "yappc.ai.llm.tokens.output";
    private static final String LLM_COST_USD        = "yappc.ai.llm.cost.usd";
    private static final String CACHE_HITS          = "yappc.ai.cache.hits.total";
    private static final String CACHE_MISSES        = "yappc.ai.cache.misses.total";
    private static final String FALLBACK_TRIGGERED  = "yappc.ai.fallback.total";
    private static final String SUGGESTION_ACCEPTED = "yappc.ai.suggestion.accepted.total";
    private static final String SUGGESTION_REJECTED = "yappc.ai.suggestion.rejected.total";
        private static final String QUALITY_CONFIDENCE  = "yappc.ai.quality.confidence";
        private static final String QUALITY_LOW_CONFIDENCE = "yappc.ai.quality.low_confidence.total";
        private static final String QUALITY_DRIFT       = "yappc.ai.quality.drift.total";
        private static final String HALLUCINATION_BLOCKED = "yappc.ai.quality.hallucination.blocked.total";

    private final MeterRegistry registry;

    public AIMetricsCollector(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "MeterRegistry must not be null");
    }

    // ── LLM call tracking ────────────────────────────────────────────────────

    /**
     * Records a completed LLM call with its key metrics.
     *
     * @param model        model identifier (e.g. {@code "claude-3-sonnet"})
     * @param feature      product feature (e.g. {@code "suggestion"}, {@code "generation"})
     * @param tenantId     tenant scope
     * @param success      whether the call succeeded
     * @param latencyMs    wall-clock time from request to full response
     * @param inputTokens  number of input tokens consumed
     * @param outputTokens number of output tokens generated
     * @param costUsd      estimated USD cost for this call
     */
    public void recordLlmCall(String model, String feature, String tenantId,
                               boolean success, long latencyMs,
                               int inputTokens, int outputTokens, double costUsd) {
        Tags tags = Tags.of(
                Tag.of("model",   safe(model)),
                Tag.of("feature", safe(feature)),
                Tag.of("tenant",  safe(tenantId)),
                Tag.of("status",  success ? "success" : "error")
        );

        Counter.builder(LLM_REQUESTS)
                .description("Total LLM API calls")
                .tags(tags).register(registry).increment();

        if (!success) {
            Counter.builder(LLM_ERRORS)
                    .description("Total failed LLM API calls")
                    .tags(tags).register(registry).increment();
        }

        Timer.builder(LLM_LATENCY)
                .description("LLM response latency in seconds")
                .tags(tags)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry)
                .record(Duration.ofMillis(latencyMs));

        Tags tokenTags = Tags.of(Tag.of("model", safe(model)), Tag.of("tenant", safe(tenantId)));
        DistributionSummary.builder(LLM_TOKENS_INPUT)
                .description("Input token distribution per LLM call")
                .tags(tokenTags).register(registry).record(inputTokens);

        DistributionSummary.builder(LLM_TOKENS_OUTPUT)
                .description("Output token distribution per LLM call")
                .tags(tokenTags).register(registry).record(outputTokens);

        DistributionSummary.builder(LLM_COST_USD)
                .description("Estimated cost (USD) per LLM call")
                .tags(tokenTags)
                .baseUnit("usd").register(registry).record(costUsd);
    }

    /**
     * Records a failed LLM call (convenience overload when token counts are unknown).
     */
    public void recordLlmError(String model, String feature, String tenantId, String errorType) {
        Tags tags = Tags.of(
                Tag.of("model",      safe(model)),
                Tag.of("feature",    safe(feature)),
                Tag.of("tenant",     safe(tenantId)),
                Tag.of("error_type", safe(errorType))
        );
        Counter.builder(LLM_ERRORS)
                .description("Total failed LLM API calls")
                .tags(tags).register(registry).increment();
        log.warn("LLM call failed: model={} feature={} tenant={} error={}",
                model, feature, tenantId, errorType);
    }

    // ── Cache metrics ─────────────────────────────────────────────────────────

    /**
     * Records a semantic cache hit (prompt was served from cache without calling the LLM).
     *
     * @param feature feature that triggered the lookup
     * @param tenantId tenant scope
     */
    public void recordCacheHit(String feature, String tenantId) {
        Tags tags = Tags.of(Tag.of("feature", safe(feature)), Tag.of("tenant", safe(tenantId)));
        Counter.builder(CACHE_HITS)
                .description("Semantic cache hits")
                .tags(tags).register(registry).increment();
    }

    /**
     * Records a semantic cache miss (prompt had to be sent to the LLM).
     */
    public void recordCacheMiss(String feature, String tenantId) {
        Tags tags = Tags.of(Tag.of("feature", safe(feature)), Tag.of("tenant", safe(tenantId)));
        Counter.builder(CACHE_MISSES)
                .description("Semantic cache misses")
                .tags(tags).register(registry).increment();
    }

    // ── Fallback metrics ──────────────────────────────────────────────────────

    /**
     * Records that the AI fallback path was triggered (primary model unavailable or errored).
     *
     * @param model     primary model that failed
     * @param reason    short reason code (e.g. {@code "timeout"}, {@code  "rate_limit"})
     * @param tenantId  tenant scope
     */
    public void recordFallback(String model, String reason, String tenantId) {
        Tags tags = Tags.of(
                Tag.of("model",   safe(model)),
                Tag.of("reason",  safe(reason)),
                Tag.of("tenant",  safe(tenantId))
        );
        Counter.builder(FALLBACK_TRIGGERED)
                .description("AI fallback path triggers")
                .tags(tags).register(registry).increment();
        log.warn("AI fallback triggered: model={} reason={} tenant={}", model, reason, tenantId);
    }

    // ── Suggestion feedback ───────────────────────────────────────────────────

    /**
     * Records that a user accepted an AI suggestion.
     */
    public void recordSuggestionAccepted(String feature, String tenantId) {
        Tags tags = Tags.of(Tag.of("feature", safe(feature)), Tag.of("tenant", safe(tenantId)));
        Counter.builder(SUGGESTION_ACCEPTED)
                .description("AI suggestions accepted by users")
                .tags(tags).register(registry).increment();
    }

    /**
     * Records that a user rejected (dismissed) an AI suggestion.
     */
    public void recordSuggestionRejected(String feature, String tenantId) {
        Tags tags = Tags.of(Tag.of("feature", safe(feature)), Tag.of("tenant", safe(tenantId)));
        Counter.builder(SUGGESTION_REJECTED)
                .description("AI suggestions rejected by users")
                .tags(tags).register(registry).increment();
    }

    /**
     * Records confidence-driven quality signals used by drift and quality dashboards.
     */
    public void recordQualitySignal(
            String model,
            String feature,
            String tenantId,
            double confidence,
            double threshold) {
        double normalizedConfidence = normalize(confidence);
        double normalizedThreshold = normalize(threshold);

        Tags tags = Tags.of(
                Tag.of("model", safe(model)),
                Tag.of("feature", safe(feature)),
                Tag.of("tenant", safe(tenantId))
        );

        DistributionSummary.builder(QUALITY_CONFIDENCE)
                .description("AI response confidence distribution")
                .tags(tags)
                .register(registry)
                .record(normalizedConfidence);

        if (normalizedConfidence < normalizedThreshold) {
            Counter.builder(QUALITY_LOW_CONFIDENCE)
                    .description("Responses below confidence threshold")
                    .tags(tags)
                    .register(registry)
                    .increment();

            Counter.builder(QUALITY_DRIFT)
                    .description("Quality drift indicators based on low confidence")
                    .tags(tags)
                    .register(registry)
                    .increment();
        }
    }

    /**
     * Records that response output was blocked by hallucination controls.
     */
    public void recordHallucinationBlocked(String model, String feature, String tenantId, String reason) {
        Tags tags = Tags.of(
                Tag.of("model", safe(model)),
                Tag.of("feature", safe(feature)),
                Tag.of("tenant", safe(tenantId)),
                Tag.of("reason", safe(reason))
        );
        Counter.builder(HALLUCINATION_BLOCKED)
                .description("Responses blocked by hallucination controls")
                .tags(tags)
                .register(registry)
                .increment();
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private static String safe(String value) {
        return value != null ? value : "unknown";
    }

        private static double normalize(double value) {
                if (Double.isNaN(value) || Double.isInfinite(value)) {
                        return 0.0;
                }
                return Math.max(0.0, Math.min(1.0, value));
        }
}
