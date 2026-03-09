/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * LLM observability tracker for monitoring cost, latency, and quality.
 *
 * <p>Tracks all LLM requests and provides aggregated metrics for:
 * <ul>
 *   <li>Cost tracking per tenant/user/feature</li>
 *   <li>Latency monitoring (p50, p95, p99)</li>
 *   <li>Token usage tracking</li>
 *   <li>Error rate monitoring</li>
 *   <li>Cache hit rate</li>
 * </ul>
 *
 * <p>Metrics are logged and can be exported to Prometheus, DataDog, or other
 * monitoring systems.
 *
 * @doc.type class
 * @doc.purpose LLM observability tracking
 * @doc.layer infrastructure
 * @doc.pattern Observer, Singleton
 */
public class LLMObservabilityTracker {

    private static final Logger logger = LoggerFactory.getLogger(LLMObservabilityTracker.class);

    // Singleton instance
    private static final LLMObservabilityTracker INSTANCE = new LLMObservabilityTracker();

    // Aggregated metrics
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);
    private final AtomicLong totalCachedResponses = new AtomicLong(0);
    private final AtomicLong totalTokens = new AtomicLong(0);
    private final DoubleAdder totalCost = new DoubleAdder();
    private final DoubleAdder totalLatencyMs = new DoubleAdder();

    // Per-tenant metrics
    private final Map<String, TenantMetrics> tenantMetrics = new ConcurrentHashMap<>();

    // Per-feature metrics
    private final Map<String, FeatureMetrics> featureMetrics = new ConcurrentHashMap<>();

    // Model pricing (cost per 1K tokens)
    private static final Map<String, ModelPricing> MODEL_PRICING = Map.of(
        "gpt-4", new ModelPricing(0.03, 0.06),
        "gpt-4-turbo", new ModelPricing(0.01, 0.03),
        "gpt-3.5-turbo", new ModelPricing(0.0005, 0.0015),
        "claude-3-opus", new ModelPricing(0.015, 0.075),
        "claude-3-sonnet", new ModelPricing(0.003, 0.015),
        "claude-3-haiku", new ModelPricing(0.00025, 0.00125)
    );

    private LLMObservabilityTracker() {
        // Private constructor for singleton
    }

    public static LLMObservabilityTracker getInstance() {
        return INSTANCE;
    }

    /**
     * Tracks an LLM request/response.
     */
    public void track(LLMMetrics metrics) {
        // Update global metrics
        totalRequests.incrementAndGet();
        totalTokens.addAndGet(metrics.getTotalTokens());
        totalCost.add(metrics.getEstimatedCost());
        totalLatencyMs.add(metrics.getLatencyMs());

        if (metrics.isError()) {
            totalErrors.incrementAndGet();
        }

        if (metrics.isCached()) {
            totalCachedResponses.incrementAndGet();
        }

        // Update tenant metrics
        if (metrics.getTenantId() != null) {
            tenantMetrics.computeIfAbsent(metrics.getTenantId(), k -> new TenantMetrics())
                .update(metrics);
        }

        // Update feature metrics
        if (metrics.getFeature() != null) {
            featureMetrics.computeIfAbsent(metrics.getFeature(), k -> new FeatureMetrics())
                .update(metrics);
        }

        // Log metrics
        logMetrics(metrics);

        // NOTE: Metrics are collected in-memory; production wiring to Prometheus/OTel is via platform MetricsCollector.
    }

    /**
     * Calculates estimated cost based on model and token usage.
     */
    public static double calculateCost(String model, int promptTokens, int completionTokens) {
        ModelPricing pricing = MODEL_PRICING.getOrDefault(model, new ModelPricing(0.001, 0.002));
        
        double promptCost = (promptTokens / 1000.0) * pricing.promptCostPer1k;
        double completionCost = (completionTokens / 1000.0) * pricing.completionCostPer1k;
        
        return promptCost + completionCost;
    }

    /**
     * Logs metrics for monitoring.
     */
    private void logMetrics(LLMMetrics metrics) {
        if (metrics.isError()) {
            logger.warn("LLM request failed: {}", metrics);
        } else {
            logger.info("LLM request completed: {}", metrics);
        }

        // Log cost alerts for expensive requests
        if (metrics.getEstimatedCost() > 0.10) {
            logger.warn("High-cost LLM request: ${} for feature '{}' (tenant: {})",
                String.format("%.4f", metrics.getEstimatedCost()),
                metrics.getFeature(),
                metrics.getTenantId()
            );
        }

        // Log latency alerts for slow requests
        if (metrics.getLatencyMs() > 5000) {
            logger.warn("Slow LLM request: {}ms for model '{}' (feature: {})",
                metrics.getLatencyMs(),
                metrics.getModel(),
                metrics.getFeature()
            );
        }
    }

    /**
     * Gets aggregated metrics summary.
     */
    public MetricsSummary getSummary() {
        long requests = totalRequests.get();
        long errors = totalErrors.get();
        long cached = totalCachedResponses.get();
        
        double errorRate = requests > 0 ? (errors * 100.0 / requests) : 0;
        double cacheHitRate = requests > 0 ? (cached * 100.0 / requests) : 0;
        double avgLatencyMs = requests > 0 ? (totalLatencyMs.sum() / requests) : 0;

        return new MetricsSummary(
            requests,
            errors,
            errorRate,
            cached,
            cacheHitRate,
            totalTokens.get(),
            totalCost.sum(),
            avgLatencyMs
        );
    }

    /**
     * Gets metrics for a specific tenant.
     */
    public TenantMetrics getTenantMetrics(String tenantId) {
        return tenantMetrics.getOrDefault(tenantId, new TenantMetrics());
    }

    /**
     * Gets metrics for a specific feature.
     */
    public FeatureMetrics getFeatureMetrics(String feature) {
        return featureMetrics.getOrDefault(feature, new FeatureMetrics());
    }

    /**
     * Resets all metrics (for testing).
     */
    public void reset() {
        totalRequests.set(0);
        totalErrors.set(0);
        totalCachedResponses.set(0);
        totalTokens.set(0);
        totalCost.reset();
        totalLatencyMs.reset();
        tenantMetrics.clear();
        featureMetrics.clear();
    }

    // Inner classes for metrics aggregation

    public static class TenantMetrics {
        private final AtomicLong requests = new AtomicLong(0);
        private final DoubleAdder cost = new DoubleAdder();
        private final AtomicLong tokens = new AtomicLong(0);

        void update(LLMMetrics metrics) {
            requests.incrementAndGet();
            cost.add(metrics.getEstimatedCost());
            tokens.addAndGet(metrics.getTotalTokens());
        }

        public long getRequests() { return requests.get(); }
        public double getCost() { return cost.sum(); }
        public long getTokens() { return tokens.get(); }
    }

    public static class FeatureMetrics {
        private final AtomicLong requests = new AtomicLong(0);
        private final DoubleAdder cost = new DoubleAdder();
        private final DoubleAdder latency = new DoubleAdder();

        void update(LLMMetrics metrics) {
            requests.incrementAndGet();
            cost.add(metrics.getEstimatedCost());
            latency.add(metrics.getLatencyMs());
        }

        public long getRequests() { return requests.get(); }
        public double getCost() { return cost.sum(); }
        public double getAvgLatencyMs() {
            long req = requests.get();
            return req > 0 ? latency.sum() / req : 0;
        }
    }

    public static class MetricsSummary {
        private final long totalRequests;
        private final long totalErrors;
        private final double errorRate;
        private final long cachedResponses;
        private final double cacheHitRate;
        private final long totalTokens;
        private final double totalCost;
        private final double avgLatencyMs;

        public MetricsSummary(long totalRequests, long totalErrors, double errorRate,
                            long cachedResponses, double cacheHitRate, long totalTokens,
                            double totalCost, double avgLatencyMs) {
            this.totalRequests = totalRequests;
            this.totalErrors = totalErrors;
            this.errorRate = errorRate;
            this.cachedResponses = cachedResponses;
            this.cacheHitRate = cacheHitRate;
            this.totalTokens = totalTokens;
            this.totalCost = totalCost;
            this.avgLatencyMs = avgLatencyMs;
        }

        public long getTotalRequests() { return totalRequests; }
        public long getTotalErrors() { return totalErrors; }
        public double getErrorRate() { return errorRate; }
        public long getCachedResponses() { return cachedResponses; }
        public double getCacheHitRate() { return cacheHitRate; }
        public long getTotalTokens() { return totalTokens; }
        public double getTotalCost() { return totalCost; }
        public double getAvgLatencyMs() { return avgLatencyMs; }

        @Override
        public String toString() {
            return String.format(
                "LLM Metrics: %d requests, %.2f%% errors, %.2f%% cache hits, " +
                "%d tokens, $%.4f cost, %.0fms avg latency",
                totalRequests, errorRate, cacheHitRate, totalTokens, totalCost, avgLatencyMs
            );
        }
    }

    private static class ModelPricing {
        final double promptCostPer1k;
        final double completionCostPer1k;

        ModelPricing(double promptCostPer1k, double completionCostPer1k) {
            this.promptCostPer1k = promptCostPer1k;
            this.completionCostPer1k = completionCostPer1k;
        }
    }
}
