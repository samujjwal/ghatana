package com.ghatana.phr.ai.agents;

import com.ghatana.kernel.observability.ExplainabilityFramework;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Predicts near-term readmission risk and enforces human review on high-risk predictions.
 *
 * @doc.type class
 * @doc.purpose Estimate 30-day readmission risk with explainable, human-in-the-loop outputs
 * @doc.layer product
 * @doc.pattern Agent
 */
public class ReadmissionRiskAgent {

    private static final double HUMAN_REVIEW_THRESHOLD = 0.70;

    private final ExplainabilityFramework explainabilityFramework;

    public ReadmissionRiskAgent(ExplainabilityFramework explainabilityFramework) {
        this.explainabilityFramework = explainabilityFramework;
    }

    public ReadmissionRiskResult score(String patientId, ReadmissionFeatures features) {
        Objects.requireNonNull(patientId, "patientId cannot be null");
        Objects.requireNonNull(features, "features cannot be null");

        double score = 0.0;
        score += Math.min(0.25, features.priorAdmissionsLast12Months() * 0.05);
        score += Math.min(0.20, features.comorbidityCount() * 0.03);
        score += features.missedFollowUps() ? 0.15 : 0.0;
        score += features.medicationAdherenceRatio() < 0.8 ? 0.15 : 0.0;
        score += features.socialRiskFactorScore();

        double normalized = Math.min(1.0, Math.max(0.0, score));
        RiskBand band = toBand(normalized);
        boolean requiresHumanReview = normalized >= HUMAN_REVIEW_THRESHOLD;

        ReadmissionRiskResult result = new ReadmissionRiskResult(
            "readmission-risk-" + patientId + "-" + Instant.now().toEpochMilli(),
            patientId,
            normalized,
            band,
            requiresHumanReview,
            requiresHumanReview
                ? "Case manager review required before discharge"
                : "Standard post-discharge follow-up"
        );

        if (explainabilityFramework != null) {
            explainabilityFramework.recordDecisionExplanation(
                result.decisionId(),
                ExplainabilityFramework.Explanation.builder()
                    .decisionId(result.decisionId())
                    .summary("Readmission risk scoring completed")
                    .detailedReasoning("Risk score derived from prior admissions, comorbidity burden, adherence and social factors")
                    .featureContributions(Map.of(
                        "prior_admissions", Math.min(0.25, features.priorAdmissionsLast12Months() * 0.05),
                        "comorbidity", Math.min(0.20, features.comorbidityCount() * 0.03),
                        "social_risk", features.socialRiskFactorScore()
                    ))
                    .confidence(0.82)
                    .modelId("phr-readmission-risk-v1")
                    .metadata(Map.of("patientId", patientId, "riskBand", band.name()))
                    .build()
            );
        }

        return result;
    }

    private static RiskBand toBand(double score) {
        if (score >= 0.85) return RiskBand.CRITICAL;
        if (score >= 0.70) return RiskBand.HIGH;
        if (score >= 0.40) return RiskBand.MODERATE;
        return RiskBand.LOW;
    }

    public enum RiskBand { LOW, MODERATE, HIGH, CRITICAL }

    public record ReadmissionFeatures(
        int priorAdmissionsLast12Months,
        int comorbidityCount,
        boolean missedFollowUps,
        double medicationAdherenceRatio,
        double socialRiskFactorScore
    ) {}

    public record ReadmissionRiskResult(
        String decisionId,
        String patientId,
        double riskScore,
        RiskBand riskBand,
        boolean requiresHumanReview,
        String recommendation
    ) {}
}
