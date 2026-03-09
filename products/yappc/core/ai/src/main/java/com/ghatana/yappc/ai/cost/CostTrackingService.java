package com.ghatana.yappc.ai.cost;

import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @doc.type class
 * @doc.purpose Tracks token usage and calculates costs for AI model interactions.
 * @doc.layer product
 * @doc.pattern Service
 */
public class CostTrackingService {
    private final Eventloop eventloop;
    private final Map<String, ModelPricing> pricingRegistry = new ConcurrentHashMap<>();
    // In-memory storage for demo purposes. In production, this would be a TimescaleDB or ClickHouse.
    private final Map<String, UserCostState> userCosts = new ConcurrentHashMap<>();

    public record ModelPricing(double inputCostPer1k, double outputCostPer1k) {}
    
    public record UsageRecord(
        String model,
        int inputTokens,
        int outputTokens,
        double cost,
        Instant timestamp,
        String featureId
    ) {}

    private static class UserCostState {
        double totalCost = 0.0;
        double monthlyBudget = 50.0; // Default budget
        // Simple list for demo
        final java.util.List<UsageRecord> history = new java.util.ArrayList<>();
    }

    public CostTrackingService(Eventloop eventloop) {
        this.eventloop = eventloop;
        initializePricing();
    }

    private void initializePricing() {
        // Standard pricing (approximate)
        pricingRegistry.put("gpt-4", new ModelPricing(0.03, 0.06));
        pricingRegistry.put("gpt-3.5-turbo", new ModelPricing(0.0005, 0.0015));
        pricingRegistry.put("claude-3-opus", new ModelPricing(0.015, 0.075));
        pricingRegistry.put("claude-3-sonnet", new ModelPricing(0.003, 0.015));
    }

    /**
     * Tracks usage for a specific request.
     */
    public Promise<Double> trackUsage(String userId, String model, int inputTokens, int outputTokens, String featureId) {
        return Promise.ofBlocking(eventloop, () -> {
            ModelPricing pricing = pricingRegistry.getOrDefault(model, new ModelPricing(0.0, 0.0));
            
            double cost = (inputTokens / 1000.0 * pricing.inputCostPer1k) +
                          (outputTokens / 1000.0 * pricing.outputCostPer1k);

            userCosts.compute(userId, (k, v) -> {
                if (v == null) v = new UserCostState();
                v.totalCost += cost;
                v.history.add(new UsageRecord(model, inputTokens, outputTokens, cost, Instant.now(), featureId));
                return v;
            });

            return cost;
        });
    }

    /**
     * Gets current usage stats for a user.
     */
    public Promise<Map<String, Object>> getUserStats(String userId) {
        return Promise.ofBlocking(eventloop, () -> {
            UserCostState state = userCosts.get(userId);
            if (state == null) {
                return Map.of(
                    "totalCost", 0.0,
                    "budget", 50.0,
                    "usageCount", 0,
                    "status", "OK"
                );
            }

            String status = state.totalCost > state.monthlyBudget ? "EXCEEDED" :
                           state.totalCost > (state.monthlyBudget * 0.8) ? "WARNING" : "OK";

            return Map.of(
                "totalCost", state.totalCost,
                "budget", state.monthlyBudget,
                "usageCount", state.history.size(),
                "status", status,
                "recentHistory", state.history.stream()
                    .sorted((a, b) -> b.timestamp.compareTo(a.timestamp))
                    .limit(10)
                    .toList()
            );
        });
    }

    /**
     * Sets a budget for a user.
     */
    public Promise<Void> setBudget(String userId, double budget) {
        return Promise.ofBlocking(eventloop, () -> {
            userCosts.compute(userId, (k, v) -> {
                if (v == null) v = new UserCostState();
                v.monthlyBudget = budget;
                return v;
            });
            return null;
        });
    }

    /**
     * Recommends a model based on budget status.
     */
    public Promise<String> recommendModel(String userId, String preferredModel) {
        return getUserStats(userId).map(stats -> {
            String status = (String) stats.get("status");
            if ("EXCEEDED".equals(status)) {
                // Downgrade logic
                if (preferredModel.startsWith("gpt-4")) return "gpt-3.5-turbo";
                if (preferredModel.contains("opus")) return "claude-3-sonnet";
            }
            return preferredModel;
        });
    }
}
