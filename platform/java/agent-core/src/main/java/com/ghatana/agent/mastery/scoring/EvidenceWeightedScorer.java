/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery.scoring;

import com.ghatana.agent.mastery.MasteryScore;
import org.jetbrains.annotations.NotNull;

/**
 * Evidence-weighted scorer for mastery confidence.
 *
 * <p>Computes mastery scores by weighting different types of evidence:
 * <ul>
 *   <li>Correctness: weighted by successful execution rate</li>
 *   <li>Freshness: weighted by recency of evidence</li>
 *   <li>Applicability: weighted by context match</li>
 *   <li>Safety: weighted by safety test results</li>
 *   <li>Transferability: weighted by cross-context success</li>
 *   <li>Evidence strength: weighted by total evidence weight</li>
 *   <li>Regression stability: weighted by regression test consistency</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Evidence-weighted scorer for mastery confidence
 * @doc.layer agent-core
 * @doc.pattern Scorer
 */
public final class EvidenceWeightedScorer implements MasteryScorer {

    private static final double DEFAULT_CORRECTNESS_WEIGHT = 0.25;
    private static final double DEFAULT_FRESHNESS_WEIGHT = 0.15;
    private static final double DEFAULT_APPLICABILITY_WEIGHT = 0.15;
    private static final double DEFAULT_SAFETY_WEIGHT = 0.20;
    private static final double DEFAULT_TRANSFERABILITY_WEIGHT = 0.10;
    private static final double DEFAULT_EVIDENCE_WEIGHT = 0.10;
    private static final double DEFAULT_REGRESSION_WEIGHT = 0.05;

    @Override
    @NotNull
    public MasteryScore score(@NotNull EvidenceBundle evidence) {
        return new MasteryScore(
                computeCorrectness(evidence),
                computeFreshness(evidence),
                computeApplicability(evidence),
                computeSafety(evidence),
                computeTransferability(evidence),
                computeEvidenceStrength(evidence),
                computeRegressionStability(evidence)
        );
    }

    @Override
    @NotNull
    public MasteryScore update(@NotNull MasteryScore currentScore, @NotNull EvidenceBundle evidence) {
        // Weighted average of current score and new evidence
        double evidenceWeight = evidence.totalWeight();
        double currentWeight = 1.0 - Math.min(evidenceWeight, 0.5);

        return new MasteryScore(
                weightedAverage(currentScore.correctness(), computeCorrectness(evidence), currentWeight, evidenceWeight),
                weightedAverage(currentScore.freshness(), computeFreshness(evidence), currentWeight, evidenceWeight),
                weightedAverage(currentScore.applicability(), computeApplicability(evidence), currentWeight, evidenceWeight),
                weightedAverage(currentScore.safety(), computeSafety(evidence), currentWeight, evidenceWeight),
                weightedAverage(currentScore.transferability(), computeTransferability(evidence), currentWeight, evidenceWeight),
                weightedAverage(currentScore.evidenceStrength(), computeEvidenceStrength(evidence), currentWeight, evidenceWeight),
                weightedAverage(currentScore.regressionStability(), computeRegressionStability(evidence), currentWeight, evidenceWeight)
        );
    }

    private double computeCorrectness(@NotNull EvidenceBundle evidence) {
        double weight = evidence.weights().getOrDefault("correctness", DEFAULT_CORRECTNESS_WEIGHT);
        double value = evidence.items().stream()
                .filter(item -> item.type().equals("correctness"))
                .mapToDouble(EvidenceBundle.EvidenceItem::value)
                .findFirst()
                .orElse(0.5);
        return Math.min(1.0, Math.max(0.0, value * weight));
    }

    private double computeFreshness(@NotNull EvidenceBundle evidence) {
        double weight = evidence.weights().getOrDefault("freshness", DEFAULT_FRESHNESS_WEIGHT);
        double value = evidence.items().stream()
                .filter(item -> item.type().equals("freshness"))
                .mapToDouble(EvidenceBundle.EvidenceItem::value)
                .findFirst()
                .orElse(0.5);
        return Math.min(1.0, Math.max(0.0, value * weight));
    }

    private double computeApplicability(@NotNull EvidenceBundle evidence) {
        double weight = evidence.weights().getOrDefault("applicability", DEFAULT_APPLICABILITY_WEIGHT);
        double value = evidence.items().stream()
                .filter(item -> item.type().equals("applicability"))
                .mapToDouble(EvidenceBundle.EvidenceItem::value)
                .findFirst()
                .orElse(0.5);
        return Math.min(1.0, Math.max(0.0, value * weight));
    }

    private double computeSafety(@NotNull EvidenceBundle evidence) {
        double weight = evidence.weights().getOrDefault("safety", DEFAULT_SAFETY_WEIGHT);
        double value = evidence.items().stream()
                .filter(item -> item.type().equals("safety"))
                .mapToDouble(EvidenceBundle.EvidenceItem::value)
                .findFirst()
                .orElse(0.5);
        return Math.min(1.0, Math.max(0.0, value * weight));
    }

    private double computeTransferability(@NotNull EvidenceBundle evidence) {
        double weight = evidence.weights().getOrDefault("transferability", DEFAULT_TRANSFERABILITY_WEIGHT);
        double value = evidence.items().stream()
                .filter(item -> item.type().equals("transferability"))
                .mapToDouble(EvidenceBundle.EvidenceItem::value)
                .findFirst()
                .orElse(0.5);
        return Math.min(1.0, Math.max(0.0, value * weight));
    }

    private double computeEvidenceStrength(@NotNull EvidenceBundle evidence) {
        double weight = evidence.weights().getOrDefault("evidence_strength", DEFAULT_EVIDENCE_WEIGHT);
        double value = Math.min(1.0, evidence.totalWeight() / 10.0); // Normalize by 10 for scale
        return Math.min(1.0, Math.max(0.0, value * weight));
    }

    private double computeRegressionStability(@NotNull EvidenceBundle evidence) {
        double weight = evidence.weights().getOrDefault("regression_stability", DEFAULT_REGRESSION_WEIGHT);
        double value = evidence.items().stream()
                .filter(item -> item.type().equals("regression"))
                .mapToDouble(EvidenceBundle.EvidenceItem::value)
                .findFirst()
                .orElse(0.5);
        return Math.min(1.0, Math.max(0.0, value * weight));
    }

    private double weightedAverage(double current, double newValue, double currentWeight, double newWeight) {
        return (current * currentWeight + newValue * newWeight) / (currentWeight + newWeight);
    }
}
