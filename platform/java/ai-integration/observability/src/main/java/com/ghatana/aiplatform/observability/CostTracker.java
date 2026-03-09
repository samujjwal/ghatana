package com.ghatana.aiplatform.observability;

import com.ghatana.platform.observability.MetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks AI inference costs (tokens, requests) attributed per tenant and model.
 *
 * <p><b>Purpose</b><br>
 * Provides cost visibility for multi-tenant LLM serving:
 * - Per-tenant token consumption tracking
 * - Per-model cost attribution
 * - Cost anomaly detection (sudden usage spikes)
 * - Chargeback/billing support
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * CostTracker costTracker = new CostTracker(metrics);
 * costTracker.setTokenPricing("gpt-4", 0.03, 0.06);  // input_tokens, output_tokens
 *
 * // Record inference
 * costTracker.recordInference(
 *     "tenant-123",
 *     "gpt-4",
 *     150,  // input tokens
 *     50    // output tokens
 * );
 *
 * // Get cost summary
 * CostTracker.CostSummary summary = costTracker.getCostSummary("tenant-123");
 * System.out.println("Total cost: $" + summary.totalCostUSD);
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * Part of ai-platform observability. Used by:
 * - Billing systems for chargeback
 * - Cost optimization analysis
 * - Budget alerts and rate limiting
 * - Usage dashboards
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe: uses ConcurrentHashMap and atomic counters.
 *
 * @doc.type class
 * @doc.purpose AI inference cost tracking and attribution
 * @doc.layer platform
 * @doc.pattern Service
 */
public class CostTracker {

    private static final Logger log = LoggerFactory.getLogger(CostTracker.class);

    private final MetricsCollector metrics;

    // model -> TokenPricing
    private final ConcurrentHashMap<String, TokenPricing> modelPricing = new ConcurrentHashMap<>();

    // tenant:model -> CostWindow
    private final ConcurrentHashMap<String, CostWindow> costWindows = new ConcurrentHashMap<>();

    // Global cost summary: tenant -> CostSummary
    private final ConcurrentHashMap<String, TenantCostSummary> tenantSummaries = new ConcurrentHashMap<>();

    /**
     * Constructor.
     *
     * @param metrics MetricsCollector for observability
     */
    public CostTracker(MetricsCollector metrics) {
        this.metrics = metrics;
    }

    /**
     * Set token pricing for a model.
     *
     * GIVEN: Model identifier and token costs
     * WHEN: setTokenPricing() is called
     * THEN: Pricing stored for cost calculation
     *
     * @param modelName model identifier (e.g., "gpt-4", "claude-2")
     * @param inputTokenPriceUSD price per input token
     * @param outputTokenPriceUSD price per output token
     */
    public void setTokenPricing(String modelName, double inputTokenPriceUSD, double outputTokenPriceUSD) {
        if (modelName == null) {
            throw new NullPointerException("Model name cannot be null");
        }
        if (inputTokenPriceUSD < 0 || outputTokenPriceUSD < 0) {
            throw new IllegalArgumentException("Prices cannot be negative");
        }

        modelPricing.put(modelName, new TokenPricing(inputTokenPriceUSD, outputTokenPriceUSD));
        log.debug("Set pricing for {}: input=${}, output=${}", modelName, inputTokenPriceUSD, outputTokenPriceUSD);
    }

    /**
     * Record inference usage.
     *
     * GIVEN: Tenant, model, and token counts
     * WHEN: recordInference() is called
     * THEN: Cost tracked and attributed to tenant/model
     *
     * @param tenantId tenant identifier
     * @param modelName model identifier
     * @param inputTokens number of input tokens
     * @param outputTokens number of output tokens
     */
    public void recordInference(String tenantId, String modelName, long inputTokens, long outputTokens) {
        if (tenantId == null || modelName == null) {
            throw new NullPointerException("tenant and model cannot be null");
        }
        if (inputTokens < 0 || outputTokens < 0) {
            throw new IllegalArgumentException("Token counts cannot be negative");
        }

        String key = tenantId + ":" + modelName;
        CostWindow window = costWindows.computeIfAbsent(key, k -> new CostWindow());

        TokenPricing pricing = modelPricing.get(modelName);
        if (pricing == null) {
            log.warn("No pricing configured for model {}", modelName);
            return;
        }

        double inputCost = inputTokens * pricing.inputTokenPrice;
        double outputCost = outputTokens * pricing.outputTokenPrice;
        double totalCost = inputCost + outputCost;

        window.recordInference(inputTokens, outputTokens, totalCost);

        // Update tenant summary
        TenantCostSummary summary = tenantSummaries.computeIfAbsent(tenantId, k -> new TenantCostSummary());
        summary.addCost(totalCost);
        summary.incrementInferenceCount();

        // Emit metrics
        metrics.incrementCounter(
            "ai.cost.inference",
            "tenant", tenantId,
            "model", modelName
        );
        metrics.recordTimer(
            "ai.cost.tokens",
            inputTokens + outputTokens,
            "tenant", tenantId,
            "model", modelName
        );
        metrics.recordTimer(
            "ai.cost.usd",
            (long)(totalCost * 100),  // Store as cents
            "tenant", tenantId,
            "model", modelName
        );

        log.debug("Recorded inference for {}:{}: input={}, output={}, cost=${:.6f}",
            tenantId, modelName, inputTokens, outputTokens, totalCost);
    }

    /**
     * Get cost summary for a tenant.
     *
     * @param tenantId tenant identifier
     * @return cost summary, or empty map if no usage
     */
    public CostSummary getCostSummary(String tenantId) {
        TenantCostSummary summary = tenantSummaries.get(tenantId);

        if (summary == null) {
            return new CostSummary(0.0, 0, Collections.emptyMap());
        }

        // Aggregate per-model costs
        Map<String, Double> perModel = new HashMap<>();
        for (Map.Entry<String, CostWindow> entry : costWindows.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(tenantId + ":")) {
                String model = key.substring((tenantId + ":").length());
                perModel.put(model, entry.getValue().getTotalCostUSD());
            }
        }

        return new CostSummary(
            summary.getTotalCostUSD(),
            summary.getInferenceCount(),
            perModel
        );
    }

    /**
     * Get per-model cost breakdown for tenant.
     *
     * @param tenantId tenant identifier
     * @return map of model -> cost
     */
    public Map<String, Double> getPerModelCosts(String tenantId) {
        Map<String, Double> perModel = new HashMap<>();

        for (Map.Entry<String, CostWindow> entry : costWindows.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(tenantId + ":")) {
                String model = key.substring((tenantId + ":").length());
                perModel.put(model, entry.getValue().getTotalCostUSD());
            }
        }

        return perModel;
    }

    /**
     * Detect cost anomalies (sudden usage spikes).
     *
     * GIVEN: Tenant and baseline average cost
     * WHEN: detectAnomaly() is called
     * THEN: Returns true if current cost significantly exceeds baseline
     *
     * @param tenantId tenant identifier
     * @param baselineAvgUSD baseline average cost (e.g., daily average)
     * @param deviationThreshold multiplier for anomaly (e.g., 2.0 = 2x baseline)
     * @return true if current cost exceeds threshold
     */
    public boolean detectAnomaly(String tenantId, double baselineAvgUSD, double deviationThreshold) {
        CostSummary summary = getCostSummary(tenantId);
        double currentCost = summary.totalCostUSD;
        double anomalyThreshold = baselineAvgUSD * deviationThreshold;

        boolean isAnomaly = currentCost > anomalyThreshold;

        if (isAnomaly) {
            metrics.incrementCounter(
                "ai.cost.anomaly",
                "tenant", tenantId,
                "severity", currentCost > anomalyThreshold * 2 ? "high" : "medium"
            );
            log.warn("Cost anomaly detected for {}: current=${:.2f} vs threshold=${:.2f}",
                tenantId, currentCost, anomalyThreshold);
        }

        return isAnomaly;
    }

    /**
     * Reset cost tracking for a tenant (e.g., daily reset).
     *
     * @param tenantId tenant identifier
     */
    public void resetTenantCosts(String tenantId) {
        // Remove all cost windows for tenant
        costWindows.entrySet().removeIf(e -> e.getKey().startsWith(tenantId + ":"));

        // Reset summary
        tenantSummaries.remove(tenantId);

        metrics.incrementCounter(
            "ai.cost.reset",
            "tenant", tenantId
        );
        log.debug("Reset costs for tenant {}", tenantId);
    }

    /**
     * Token pricing configuration.
     */
    private static class TokenPricing {
        final double inputTokenPrice;
        final double outputTokenPrice;

        TokenPricing(double inputTokenPrice, double outputTokenPrice) {
            this.inputTokenPrice = inputTokenPrice;
            this.outputTokenPrice = outputTokenPrice;
        }
    }

    /**
     * Cost tracking window for a tenant:model pair.
     */
    private static class CostWindow {
        private final AtomicLong inputTokens = new AtomicLong(0);
        private final AtomicLong outputTokens = new AtomicLong(0);
        private final AtomicLong totalCostCents = new AtomicLong(0);  // Store as cents for precision
        private final long createdAtMs = System.currentTimeMillis();

        void recordInference(long input, long output, double costUSD) {
            inputTokens.addAndGet(input);
            outputTokens.addAndGet(output);
            totalCostCents.addAndGet((long)(costUSD * 100));
        }

        double getTotalCostUSD() {
            return totalCostCents.get() / 100.0;
        }

        long getInputTokens() {
            return inputTokens.get();
        }

        long getOutputTokens() {
            return outputTokens.get();
        }
    }

    /**
     * Tenant cost summary.
     */
    private static class TenantCostSummary {
        private final AtomicLong totalCostCents = new AtomicLong(0);
        private final AtomicLong inferenceCount = new AtomicLong(0);

        void addCost(double costUSD) {
            totalCostCents.addAndGet((long)(costUSD * 100));
        }

        void incrementInferenceCount() {
            inferenceCount.incrementAndGet();
        }

        double getTotalCostUSD() {
            return totalCostCents.get() / 100.0;
        }

        long getInferenceCount() {
            return inferenceCount.get();
        }
    }

    /**
     * Cost summary result (immutable).
     */
    public static class CostSummary {
        public final double totalCostUSD;
        public final long inferenceCount;
        public final Map<String, Double> perModelCosts;

        public CostSummary(double totalCostUSD, long inferenceCount, Map<String, Double> perModelCosts) {
            this.totalCostUSD = totalCostUSD;
            this.inferenceCount = inferenceCount;
            this.perModelCosts = Collections.unmodifiableMap(perModelCosts);
        }

        public double getAverageCostPerInference() {
            return inferenceCount > 0 ? totalCostUSD / inferenceCount : 0.0;
        }
    }
}
