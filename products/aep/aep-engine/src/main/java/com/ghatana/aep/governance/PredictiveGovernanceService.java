/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.governance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Predictive governance service for ML-based incident prediction.
 * Analyzes metrics and patterns to predict potential incidents before kill-switch is needed.
 *
 * @doc.type class
 * @doc.purpose ML-based incident prediction for proactive governance
 * @doc.layer product
 * @doc.pattern Service, Predictor
 */
public final class PredictiveGovernanceService {

    private static final Logger log = LoggerFactory.getLogger(PredictiveGovernanceService.class);

    private final Map<String, TenantMetrics> tenantMetrics = new ConcurrentHashMap<>();
    private final Map<String, List<IncidentPrediction>> predictions = new ConcurrentHashMap<>();
    private final double predictionThreshold;
    private final int predictionHorizonMinutes;
    private final int maxHistorySize;

    /**
     * Creates a predictive governance service with default settings.
     */
    public PredictiveGovernanceService() {
        this(0.75, 30, 1000);
    }

    /**
     * Creates a predictive governance service with custom settings.
     *
     * @param predictionThreshold minimum probability to trigger warning (0.0-1.0)
     * @param predictionHorizonMinutes how far ahead to predict (in minutes)
     * @param maxHistorySize maximum metric history entries per tenant
     */
    public PredictiveGovernanceService(double predictionThreshold, int predictionHorizonMinutes, int maxHistorySize) {
        this.predictionThreshold = predictionThreshold;
        this.predictionHorizonMinutes = predictionHorizonMinutes;
        this.maxHistorySize = maxHistorySize;
    }

    /**
     * Records a metric observation for a tenant.
     *
     * @param tenantId tenant identifier
     * @param metricName metric name
     * @param value metric value
     * @param timestamp when the metric was recorded
     */
    public void recordMetric(String tenantId, String metricName, double value, Instant timestamp) {
        TenantMetrics metrics = tenantMetrics.computeIfAbsent(tenantId, k -> new TenantMetrics(k));
        metrics.addObservation(metricName, value, timestamp);
    }

    /**
     * Generates incident predictions for a tenant.
     *
     * @param tenantId tenant identifier
     * @return list of incident predictions
     */
    public List<IncidentPrediction> predictIncidents(String tenantId) {
        TenantMetrics metrics = tenantMetrics.get(tenantId);
        if (metrics == null) {
            return List.of();
        }

        List<IncidentPrediction> newPredictions = new ArrayList<>();

        // Analyze various risk factors
        analyzeErrorRate(metrics, newPredictions);
        analyzeLatency(metrics, newPredictions);
        analyzeThroughput(metrics, newPredictions);
        analyzeResourceUsage(metrics, newPredictions);

        // Store predictions
        predictions.put(tenantId, newPredictions);

        // Prune old predictions
        pruneOldPredictions(tenantId);

        log.info("[predictive-governance] Generated {} predictions for tenant {}", 
            newPredictions.size(), tenantId);

        return List.copyOf(newPredictions);
    }

    /**
     * Analyzes error rate trends for incident prediction.
     */
    private void analyzeErrorRate(TenantMetrics metrics, List<IncidentPrediction> predictions) {
        List<MetricObservation> errorRateHistory = metrics.getHistory("error_rate");
        if (errorRateHistory.isEmpty()) {
            return;
        }

        if (errorRateHistory.size() < 5) {
            double currentValue = errorRateHistory.get(errorRateHistory.size() - 1).value();
            if (currentValue >= 0.1 && predictionThreshold <= 0.8) {
                predictions.add(new IncidentPrediction(
                    UUID.randomUUID().toString(),
                    "high_error_rate",
                    "Incident",
                    Instant.now().plus(predictionHorizonMinutes, ChronoUnit.MINUTES),
                    0.8,
                    Map.of(
                        "currentErrorRate", currentValue,
                        "threshold", 0.1,
                        "observationCount", errorRateHistory.size()
                    ),
                    "Elevated error rate observed: " + String.format("%.2f%%", currentValue * 100)
                ));
            }
            return;
        }

        double recentAvg = tail(errorRateHistory, 5).stream()
            .mapToDouble(MetricObservation::value)
            .average()
            .orElse(0.0);

        double historicalAvg = head(errorRateHistory, errorRateHistory.size() / 2).stream()
            .mapToDouble(MetricObservation::value)
            .average()
            .orElse(0.0);

        double trendRatio = historicalAvg > 0 ? recentAvg / historicalAvg : 1.0;

        // Predict incident if error rate is increasing significantly
        if (trendRatio > 1.5 && recentAvg > 0.1) {
            double probability = Math.min(0.95, trendRatio * 0.5);
            if (probability >= predictionThreshold) {
                predictions.add(new IncidentPrediction(
                    UUID.randomUUID().toString(),
                    "high_error_rate",
                    "Incident",
                    Instant.now().plus(predictionHorizonMinutes, ChronoUnit.MINUTES),
                    probability,
                    Map.of(
                        "currentErrorRate", recentAvg,
                        "trendRatio", trendRatio,
                        "threshold", 0.1
                    ),
                    "Error rate trending upward: " + String.format("%.2f%%", recentAvg * 100)
                ));
            }
        }
    }

    /**
     * Analyzes latency trends for incident prediction.
     */
    private void analyzeLatency(TenantMetrics metrics, List<IncidentPrediction> predictions) {
        List<MetricObservation> latencyHistory = metrics.getHistory("latency_ms");
        if (latencyHistory.size() < 5) {
            return;
        }

        double recentAvg = tail(latencyHistory, 5).stream()
            .mapToDouble(MetricObservation::value)
            .average()
            .orElse(0.0);

        double maxAcceptable = 5000.0; // 5 seconds

        if (recentAvg > maxAcceptable) {
            double probability = Math.min(0.95, recentAvg / maxAcceptable * 0.5);
            if (probability >= predictionThreshold) {
                predictions.add(new IncidentPrediction(
                    UUID.randomUUID().toString(),
                    "high_latency",
                    "Degradation",
                    Instant.now().plus(predictionHorizonMinutes, ChronoUnit.MINUTES),
                    probability,
                    Map.of(
                        "currentLatencyMs", recentAvg,
                        "thresholdMs", maxAcceptable
                    ),
                    "Latency exceeding threshold: " + String.format("%.0fms", recentAvg)
                ));
            }
        }
    }

    /**
     * Analyzes throughput trends for incident prediction.
     */
    private void analyzeThroughput(TenantMetrics metrics, List<IncidentPrediction> predictions) {
        List<MetricObservation> throughputHistory = metrics.getHistory("throughput_per_second");
        if (throughputHistory.size() < 5) {
            return;
        }

        double recentAvg = tail(throughputHistory, 5).stream()
            .mapToDouble(MetricObservation::value)
            .average()
            .orElse(0.0);

        double historicalAvg = head(throughputHistory, throughputHistory.size() / 2).stream()
            .mapToDouble(MetricObservation::value)
            .average()
            .orElse(0.0);

        // Predict incident if throughput is dropping significantly
        if (recentAvg < historicalAvg * 0.5 && historicalAvg > 100) {
            double probability = Math.min(0.95, (historicalAvg - recentAvg) / historicalAvg);
            if (probability >= predictionThreshold) {
                predictions.add(new IncidentPrediction(
                    UUID.randomUUID().toString(),
                    "throughput_drop",
                    "Degradation",
                    Instant.now().plus(predictionHorizonMinutes, ChronoUnit.MINUTES),
                    probability,
                    Map.of(
                        "currentThroughput", recentAvg,
                        "historicalThroughput", historicalAvg,
                        "dropPercentage", (historicalAvg - recentAvg) / historicalAvg * 100
                    ),
                    "Throughput dropped significantly: " + String.format("%.0f events/sec", recentAvg)
                ));
            }
        }
    }

    /**
     * Analyzes resource usage trends for incident prediction.
     */
    private void analyzeResourceUsage(TenantMetrics metrics, List<IncidentPrediction> predictions) {
        List<MetricObservation> cpuHistory = metrics.getHistory("cpu_usage_percent");
        List<MetricObservation> memoryHistory = metrics.getHistory("memory_usage_percent");

        if (cpuHistory.size() < 5 && memoryHistory.size() < 5) {
            return;
        }

        double recentCpu = cpuHistory.stream()
            .skip(Math.max(0, cpuHistory.size() - 5L))
            .mapToDouble(MetricObservation::value)
            .average()
            .orElse(0.0);

        double recentMemory = memoryHistory.stream()
            .skip(Math.max(0, memoryHistory.size() - 5L))
            .mapToDouble(MetricObservation::value)
            .average()
            .orElse(0.0);

        // Predict incident if resources are critically high
        if (recentCpu > 90.0 || recentMemory > 90.0) {
            double probability = Math.max(recentCpu, recentMemory) / 100.0;
            if (probability >= predictionThreshold) {
                predictions.add(new IncidentPrediction(
                    UUID.randomUUID().toString(),
                    "resource_exhaustion",
                    "Outage",
                    Instant.now().plus(predictionHorizonMinutes, ChronoUnit.MINUTES),
                    probability,
                    Map.of(
                        "cpuUsagePercent", recentCpu,
                        "memoryUsagePercent", recentMemory
                    ),
                    "Resource exhaustion imminent: CPU " + String.format("%.0f%%", recentCpu) + 
                        ", Memory " + String.format("%.0f%%", recentMemory)
                ));
            }
        }
    }

    /**
     * Prunes old predictions for a tenant.
     */
    private void pruneOldPredictions(String tenantId) {
        List<IncidentPrediction> tenantPredictions = predictions.get(tenantId);
        if (tenantPredictions != null) {
            Instant cutoff = Instant.now().minus(1, ChronoUnit.HOURS);
            tenantPredictions.removeIf(p -> p.predictedTime().isBefore(cutoff));
        }
    }

    /**
     * Gets recent predictions for a tenant.
     *
     * @param tenantId tenant identifier
     * @return list of recent predictions
     */
    public List<IncidentPrediction> getPredictions(String tenantId) {
        return List.copyOf(predictions.getOrDefault(tenantId, List.of()));
    }

    /**
     * Gets high-severity predictions (probability > 0.8) for a tenant.
     *
     * @param tenantId tenant identifier
     * @return list of high-severity predictions
     */
    public List<IncidentPrediction> getHighSeverityPredictions(String tenantId) {
        return predictions.getOrDefault(tenantId, List.of()).stream()
            .filter(p -> p.probability() > 0.8)
            .toList();
    }

    /**
     * Clears metrics and predictions for a tenant.
     *
     * @param tenantId tenant identifier
     */
    public void clearTenant(String tenantId) {
        tenantMetrics.remove(tenantId);
        predictions.remove(tenantId);
    }

    /**
     * Clears all metrics and predictions.
     */
    public void clearAll() {
        tenantMetrics.clear();
        predictions.clear();
    }

    private static List<MetricObservation> tail(List<MetricObservation> history, int count) {
        return history.subList(Math.max(0, history.size() - count), history.size());
    }

    private static List<MetricObservation> head(List<MetricObservation> history, int count) {
        return history.subList(0, Math.max(1, Math.min(count, history.size())));
    }

    /**
     * Tenant metrics container.
     */
    private static class TenantMetrics {
        private final String tenantId;
        private final Map<String, List<MetricObservation>> metricHistory = new ConcurrentHashMap<>();

        TenantMetrics(String tenantId) {
            this.tenantId = tenantId;
        }

        void addObservation(String metricName, double value, Instant timestamp) {
            metricHistory.computeIfAbsent(metricName, k -> new java.util.concurrent.CopyOnWriteArrayList<>())
                .add(new MetricObservation(metricName, value, timestamp));

            // Prune if over limit
            List<MetricObservation> history = metricHistory.get(metricName);
            if (history.size() > 1000) {
                history.remove(0);
            }
        }

        List<MetricObservation> getHistory(String metricName) {
            return List.copyOf(metricHistory.getOrDefault(metricName, List.of()));
        }
    }

    /**
     * Metric observation record.
     *
     * @param metricName metric name
     * @param value metric value
     * @param timestamp when observed
     */
    public record MetricObservation(
        String metricName,
        double value,
        Instant timestamp
    ) {}

    /**
     * Incident prediction record.
     *
     * @param id unique prediction identifier
     * @param type prediction type
     * @param severity incident severity
     * @param predictedTime when incident is predicted to occur
     * @param probability prediction probability (0.0-1.0)
     * @param factors contributing factors
     * @param description human-readable description
     */
    public record IncidentPrediction(
        String id,
        String type,
        String severity,
        Instant predictedTime,
        double probability,
        Map<String, Object> factors,
        String description
    ) {
        public IncidentPrediction {
            probability = Math.max(0.0, Math.min(1.0, probability));
            factors = Map.copyOf(factors);
        }
    }
}
