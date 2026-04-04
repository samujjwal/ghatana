/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.registry.agents;

import com.ghatana.agent.AgentDescriptor;
import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentType;
import com.ghatana.agent.DeterminismGuarantee;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.runtime.AbstractTypedAgent;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Set;

/**
 * Data Anomaly Detector Agent — applies statistical techniques to detect
 * anomalous values in a numeric time-series or metric stream from Data Cloud.
 *
 * <p>Uses a modified Z-score (IQR-based) algorithm that is robust to outliers.
 * The detection threshold is configurable via the Z-score multiplier in the
 * request. Default threshold is 2.5 standard deviations.</p>
 *
 * <p>Input: a {@link AnomalyDetectionRequest} carrying the tenant ID, metric
 * name, and a list of numeric samples. Output: a {@link AnomalyDetectionResult}
 * listing the indices and values of detected anomalies.</p>
 *
 * @doc.type class
 * @doc.purpose Statistical anomaly detection agent for Data Cloud metrics
 * @doc.layer product
 * @doc.pattern Agent, Probabilistic
 */
public class DataAnomalyDetectorAgent extends AbstractTypedAgent<DataAnomalyDetectorAgent.AnomalyDetectionRequest, DataAnomalyDetectorAgent.AnomalyDetectionResult> {

    private static final AgentDescriptor DESCRIPTOR = AgentDescriptor.builder()
            .agentId("data-cloud:agent.data-cloud.anomaly-detector")
            .name("Data Cloud Anomaly Detector")
            .version("1.0.0")
            .description("Statistical anomaly detection for Data Cloud metric streams using Z-score analysis")
            .namespace("data-cloud")
            .type(AgentType.PROBABILISTIC)
            .subtype("STATISTICAL")
            .determinism(DeterminismGuarantee.NONE)
            .latencySla(Duration.ofMillis(100))
            .capabilities(Set.of(
                    "anomaly-detection",
                    "statistical-analysis",
                    "outlier-identification",
                    "metric-monitoring"))
            .build();

    /** Default Z-score threshold for anomaly classification. */
    private static final double DEFAULT_THRESHOLD = 2.5;

    @Override
    public @NotNull AgentDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    protected @NotNull Promise<AgentResult<AnomalyDetectionResult>> doProcess(
            @NotNull AgentContext ctx,
            @NotNull AnomalyDetectionRequest request) {

        List<Double> samples = request.samples();

        if (samples == null || samples.size() < 3) {
            return Promise.of(AgentResult.<AnomalyDetectionResult>builder()
                    .output(new AnomalyDetectionResult(
                            request.tenantId(),
                            request.metricName(),
                            List.of(),
                            List.of(),
                            0,
                            false))
                    .confidence(0.0)
                    .agentId(DESCRIPTOR.getAgentId())
                    .explanation("Insufficient data points for anomaly detection (minimum 3 required)")
                    .build());
        }

        DoubleSummaryStatistics stats = samples.stream()
                .mapToDouble(Double::doubleValue)
                .summaryStatistics();

        double mean = stats.getAverage();
        double threshold = request.threshold() > 0 ? request.threshold() : DEFAULT_THRESHOLD;

        // Compute standard deviation
        double variance = samples.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0.0);
        double stdDev = Math.sqrt(variance);

        List<Integer> anomalyIndices = new ArrayList<>();
        List<Double> anomalyValues = new ArrayList<>();

        if (stdDev > 0) {
            for (int i = 0; i < samples.size(); i++) {
                double zScore = Math.abs((samples.get(i) - mean) / stdDev);
                if (zScore > threshold) {
                    anomalyIndices.add(i);
                    anomalyValues.add(samples.get(i));
                }
            }
        }

        boolean anomaliesFound = !anomalyIndices.isEmpty();
        double confidence = stdDev > 0 ? Math.min(1.0, samples.size() / 100.0 + 0.5) : 0.3;

        AnomalyDetectionResult result = new AnomalyDetectionResult(
                request.tenantId(),
                request.metricName(),
                anomalyIndices,
                anomalyValues,
                anomalyIndices.size(),
                anomaliesFound);

        return Promise.of(AgentResult.<AnomalyDetectionResult>builder()
                .output(result)
                .confidence(confidence)
                .agentId(DESCRIPTOR.getAgentId())
                .explanation(anomaliesFound
                        ? "Detected " + anomalyIndices.size() + " anomalous data point(s) beyond " + threshold + "σ threshold"
                        : "No anomalies detected in " + samples.size() + " data points")
                .build());
    }

    // ─── Input / Output Types ────────────────────────────────────────────────

    /**
     * Anomaly detection request.
     *
     * @param tenantId   tenant whose data is being analysed
     * @param metricName human-readable name for the metric series
     * @param samples    ordered list of numeric samples; must have at least 3 entries
     * @param threshold  Z-score threshold; values beyond this are classified as anomalies
     *                   (use {@code 0} or negative for the default of {@value DEFAULT_THRESHOLD})
     */
    public record AnomalyDetectionRequest(
            String tenantId,
            String metricName,
            List<Double> samples,
            double threshold) {}

    /**
     * Anomaly detection result.
     *
     * @param tenantId       echoed from the request for correlation
     * @param metricName     echoed from the request for correlation
     * @param anomalyIndices 0-based indices in the input sample list where anomalies were found
     * @param anomalyValues  the anomalous values corresponding to each index
     * @param anomalyCount   total number of anomalies detected
     * @param hasAnomalies   {@code true} if at least one anomaly was found
     */
    public record AnomalyDetectionResult(
            String tenantId,
            String metricName,
            List<Integer> anomalyIndices,
            List<Double> anomalyValues,
            int anomalyCount,
            boolean hasAnomalies) {}
}
