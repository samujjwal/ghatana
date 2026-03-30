package com.ghatana.phr.ai.agents;

import com.ghatana.kernel.observability.ExplainabilityFramework;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Detects abnormal lab value combinations for a patient.
 *
 * @doc.type class
 * @doc.purpose Detect clinically significant lab anomalies with explainable output
 * @doc.layer product
 * @doc.pattern Agent
 */
public class LabAnomalyDetectionAgent {

    private static final double HIGH_RISK_THRESHOLD = 0.75;

    // Reference ranges are intentionally conservative for first-pass screening.
    private static final Map<String, Range> REFERENCE_RANGES = Map.of(
        "hemoglobin", new Range(12.0, 17.5),
        "wbc", new Range(4.0, 11.0),
        "platelets", new Range(150.0, 450.0),
        "creatinine", new Range(0.6, 1.3),
        "glucose_fasting", new Range(70.0, 100.0)
    );

    private final ExplainabilityFramework explainabilityFramework;

    public LabAnomalyDetectionAgent(ExplainabilityFramework explainabilityFramework) {
        this.explainabilityFramework = explainabilityFramework;
    }

    public LabAnomalyResult detect(String patientId, Map<String, Double> labValues) {
        Objects.requireNonNull(patientId, "patientId cannot be null");
        Objects.requireNonNull(labValues, "labValues cannot be null");

        List<DetectedAnomaly> anomalies = new ArrayList<>();
        double riskAccumulator = 0.0;

        for (Map.Entry<String, Double> entry : labValues.entrySet()) {
            Range range = REFERENCE_RANGES.get(entry.getKey());
            if (range == null) {
                continue;
            }
            double value = entry.getValue();
            if (value < range.min || value > range.max) {
                double normalizedDeviation = normalizeDeviation(value, range);
                riskAccumulator += Math.min(1.0, normalizedDeviation);
                anomalies.add(new DetectedAnomaly(
                    entry.getKey(),
                    value,
                    range.min,
                    range.max,
                    normalizedDeviation > 0.5 ? Severity.HIGH : Severity.MODERATE
                ));
            }
        }

        double riskScore = anomalies.isEmpty() ? 0.0 : Math.min(1.0, riskAccumulator / anomalies.size());
        boolean needsHumanReview = riskScore >= HIGH_RISK_THRESHOLD;

        LabAnomalyResult result = new LabAnomalyResult(
            "lab-anomaly-" + patientId + "-" + Instant.now().toEpochMilli(),
            patientId,
            anomalies,
            riskScore,
            needsHumanReview,
            needsHumanReview ? "Escalate to clinician review" : "Monitor and repeat labs"
        );

        if (explainabilityFramework != null) {
            explainabilityFramework.recordDecisionExplanation(
                result.decisionId(),
                ExplainabilityFramework.Explanation.builder()
                    .decisionId(result.decisionId())
                    .summary("Lab anomaly screening completed")
                    .detailedReasoning("Detected " + anomalies.size() + " anomalies across submitted lab panel")
                    .featureContributions(Map.of(
                        "anomaly_count", (double) anomalies.size(),
                        "risk_score", riskScore
                    ))
                    .confidence(Math.max(0.5, 1.0 - (0.1 * anomalies.size())))
                    .modelId("phr-lab-anomaly-v1")
                    .metadata(Map.of("patientId", patientId))
                    .build()
            );
        }

        return result;
    }

    private static double normalizeDeviation(double value, Range range) {
        double lowerDeviation = range.min - value;
        double upperDeviation = value - range.max;
        double deviation = Math.max(0.0, Math.max(lowerDeviation, upperDeviation));
        double span = Math.max(1e-6, range.max - range.min);
        return deviation / span;
    }

    private record Range(double min, double max) {}

    public enum Severity { LOW, MODERATE, HIGH, CRITICAL }

    public record DetectedAnomaly(
        String marker,
        double observedValue,
        double referenceMin,
        double referenceMax,
        Severity severity
    ) {}

    public record LabAnomalyResult(
        String decisionId,
        String patientId,
        List<DetectedAnomaly> anomalies,
        double riskScore,
        boolean requiresHumanReview,
        String recommendation
    ) {}
}
