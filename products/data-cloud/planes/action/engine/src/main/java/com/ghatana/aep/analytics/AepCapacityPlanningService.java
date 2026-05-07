/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.analytics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Capacity planning service for AEP (AEP-011.4).
 *
 * <p>Forecasts when current resources will be exhausted based on observed growth
 * rates and recommends scale-up actions.  Uses a simple linear growth model to
 * extrapolate resource usage to a given planning horizon.
 *
 * @doc.type    class
 * @doc.purpose Resource usage forecasting and capacity planning advisor
 * @doc.layer   product
 * @doc.pattern Advisor, Analytics
 */
public final class AepCapacityPlanningService {

    private static final Logger LOG = LoggerFactory.getLogger(AepCapacityPlanningService.class);

    private final double safetyMargin;
    private final int forecastHorizonSteps;

    private AepCapacityPlanningService(Builder builder) {
        this.safetyMargin        = builder.safetyMargin;
        this.forecastHorizonSteps = builder.forecastHorizonSteps;
    }

    // ── Planning ─────────────────────────────────────────────────────────────

    /**
     * Performs a capacity analysis for a resource metric series.
     *
     * @param tenantId    tenant identifier
     * @param resourceName human-readable resource name (e.g., "heap_mb", "throughput_ops")
     * @param history     historical usage observations (oldest first, at least 2 values)
     * @param capacity    current resource capacity (must be &gt;0)
     * @return capacity plan; never {@code null}
     */
    public CapacityPlan analyze(String tenantId, String resourceName,
                                 List<Double> history, double capacity) {
        Objects.requireNonNull(tenantId,      "tenantId must not be null");
        Objects.requireNonNull(resourceName,  "resourceName must not be null");
        Objects.requireNonNull(history,       "history must not be null");
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        if (history.size() < 2)
            throw new IllegalArgumentException("At least 2 observations required");

        // Linear regression to estimate growth rate
        double growthPerStep = estimateGrowthRate(history);
        double current       = history.get(history.size() - 1);
        double safeCapacity  = capacity * (1 - safetyMargin);

        // Extrapolate
        double[] forecast = new double[forecastHorizonSteps];
        for (int h = 1; h <= forecastHorizonSteps; h++) {
            forecast[h - 1] = current + h * growthPerStep;
        }

        // Find when capacity (with safety margin) will be exhausted
        int stepsToExhaustion = Integer.MAX_VALUE;
        for (int h = 0; h < forecastHorizonSteps; h++) {
            if (forecast[h] >= safeCapacity) {
                stepsToExhaustion = h + 1;
                break;
            }
        }

        double currentUsageRatio = current / capacity;
        String recommendation = buildRecommendation(
                resourceName, currentUsageRatio, stepsToExhaustion, growthPerStep, capacity);

        LOG.info("Capacity plan tenant={} resource={} currentRatio={:.1f}% growthPerStep={:.2f} stepsToExhaustion={}",
                tenantId, resourceName, currentUsageRatio * 100, growthPerStep,
                stepsToExhaustion == Integer.MAX_VALUE ? "N/A" : stepsToExhaustion);

        return new CapacityPlan(
                tenantId, resourceName, Instant.now(),
                current, capacity, currentUsageRatio,
                growthPerStep, forecast,
                stepsToExhaustion == Integer.MAX_VALUE ? -1 : stepsToExhaustion,
                recommendation
        );
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private double estimateGrowthRate(List<Double> history) {
        // Simple linear regression slope: sum((x_i - x_mean)(y_i - y_mean)) / sum((x_i - x_mean)^2)
        int n = history.size();
        double xMean = (n - 1) / 2.0;
        double yMean = history.stream().mapToDouble(Double::doubleValue).average().orElse(0);

        double numerator = 0, denominator = 0;
        for (int i = 0; i < n; i++) {
            double dx = i - xMean;
            numerator   += dx * (history.get(i) - yMean);
            denominator += dx * dx;
        }
        return denominator == 0 ? 0.0 : numerator / denominator;
    }

    private String buildRecommendation(String resource, double ratio, int stepsToExhaustion,
                                        double growth, double capacity) {
        if (ratio >= 0.9) {
            return String.format("CRITICAL: %s is at %.0f%% capacity. Scale up immediately.", resource, ratio * 100);
        }
        if (stepsToExhaustion <= 5) {
            return String.format("WARNING: %s will reach capacity in ~%d steps at current growth rate (%.2f/step). "
                    + "Plan scale-up now.", resource, stepsToExhaustion, growth);
        }
        if (ratio >= 0.7) {
            return String.format("ADVISORY: %s at %.0f%% — monitor and plan scale-up before hitting 85%%.", resource, ratio * 100);
        }
        return String.format("OK: %s at %.0f%% — no immediate action required.", resource, ratio * 100);
    }

    // ── Nested types ──────────────────────────────────────────────────────────

    /**
     * Capacity planning result.
     *
     * @param tenantId          tenant identifier
     * @param resourceName      resource being analyzed
     * @param generatedAt       when the plan was generated
     * @param currentUsage      most recent observed usage value
     * @param totalCapacity     total resource capacity
     * @param usageRatio        current usage / capacity [0, 1]
     * @param growthPerStep     estimated growth per time step
     * @param forecastUsage     predicted usage for each future step
     * @param stepsToExhaustion steps until safe capacity is reached (-1 = beyond horizon)
     * @param recommendation    actionable recommendation
     */
    public record CapacityPlan(
            String tenantId,
            String resourceName,
            Instant generatedAt,
            double currentUsage,
            double totalCapacity,
            double usageRatio,
            double growthPerStep,
            double[] forecastUsage,
            int stepsToExhaustion,
            String recommendation
    ) {
        /** Returns {@code true} if usage is within the safe operating zone. */
        public boolean isSafe() { return usageRatio < 0.85; }
    }

    // ── Builder ────────────────────────────────────────────────────────────────

    /** Returns a new builder. */
    public static Builder builder() { return new Builder(); }

    /**
     * Builder for {@link AepCapacityPlanningService}.
     */
    public static final class Builder {
        private double safetyMargin      = 0.15; // 15% headroom
        private int forecastHorizonSteps = 10;

        private Builder() {}

        /**
         * Safety margin: fraction of total capacity to reserve.
         *
         * @param margin value in [0, 1)
         * @return this builder
         */
        public Builder safetyMargin(double margin) {
            if (margin < 0 || margin >= 1) throw new IllegalArgumentException("margin must be in [0, 1)");
            this.safetyMargin = margin;
            return this;
        }

        /**
         * Number of future steps to forecast.
         *
         * @param steps positive integer
         * @return this builder
         */
        public Builder forecastHorizonSteps(int steps) {
            if (steps <= 0) throw new IllegalArgumentException("steps must be positive");
            this.forecastHorizonSteps = steps;
            return this;
        }

        public AepCapacityPlanningService build() { return new AepCapacityPlanningService(this); }
    }
}
