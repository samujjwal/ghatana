package com.ghatana.phr.ai.agents;

import com.ghatana.kernel.observability.ExplainabilityFramework;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Screens a patient's active medications for known high-risk interactions.
 *
 * @doc.type class
 * @doc.purpose Detect medication interactions and generate explainable safety recommendations
 * @doc.layer product
 * @doc.pattern Agent
 */
public class MedicationInteractionAgent {

    private final ExplainabilityFramework explainabilityFramework;
    private final Map<String, InteractionRule> interactions = new HashMap<>();

    public MedicationInteractionAgent(ExplainabilityFramework explainabilityFramework) {
        this.explainabilityFramework = explainabilityFramework;
        registerDefaults();
    }

    public InteractionAssessment assess(String patientId, List<String> activeMedications) {
        Objects.requireNonNull(patientId, "patientId cannot be null");
        Objects.requireNonNull(activeMedications, "activeMedications cannot be null");

        List<InteractionMatch> matches = new ArrayList<>();
        for (int i = 0; i < activeMedications.size(); i++) {
            for (int j = i + 1; j < activeMedications.size(); j++) {
                String left = normalize(activeMedications.get(i));
                String right = normalize(activeMedications.get(j));
                InteractionRule rule = interactions.get(ruleKey(left, right));
                if (rule != null) {
                    matches.add(new InteractionMatch(rule, left, right));
                }
            }
        }

        Severity highest = matches.stream()
            .map(match -> match.rule().severity())
            .max(Severity::compareTo)
            .orElse(Severity.LOW);

        boolean requiresHumanReview = highest == Severity.HIGH || highest == Severity.CRITICAL;
        InteractionAssessment result = new InteractionAssessment(
            "med-interaction-" + patientId + "-" + Instant.now().toEpochMilli(),
            patientId,
            matches,
            highest,
            requiresHumanReview,
            requiresHumanReview ? "Pharmacist/clinician review required before dispense" : "No critical interaction detected"
        );

        if (explainabilityFramework != null) {
            explainabilityFramework.recordDecisionExplanation(
                result.decisionId(),
                ExplainabilityFramework.Explanation.builder()
                    .decisionId(result.decisionId())
                    .summary("Medication interaction analysis completed")
                    .detailedReasoning("Found " + matches.size() + " known interaction pairs")
                    .featureContributions(Map.of(
                        "interaction_pairs", (double) matches.size(),
                        "requires_review", requiresHumanReview ? 1.0 : 0.0
                    ))
                    .confidence(matches.isEmpty() ? 0.9 : 0.85)
                    .modelId("phr-medication-interaction-v1")
                    .metadata(Map.of("patientId", patientId))
                    .build()
            );
        }

        return result;
    }

    private void registerDefaults() {
        register("warfarin", "aspirin", Severity.HIGH,
            "Elevated bleeding risk when anticoagulant and antiplatelet are co-administered");
        register("lisinopril", "spironolactone", Severity.MODERATE,
            "Hyperkalemia risk; monitor potassium closely");
        register("metformin", "contrast_dye", Severity.HIGH,
            "Risk of lactic acidosis around contrast exposure");
        register("clopidogrel", "omeprazole", Severity.MODERATE,
            "Potential reduction in antiplatelet activity");
    }

    private void register(String left, String right, Severity severity, String explanation) {
        interactions.put(ruleKey(left, right), new InteractionRule(severity, explanation));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static String ruleKey(String left, String right) {
        return left.compareTo(right) <= 0 ? left + "|" + right : right + "|" + left;
    }

    public enum Severity { LOW, MODERATE, HIGH, CRITICAL }

    public record InteractionRule(Severity severity, String explanation) {}

    public record InteractionMatch(InteractionRule rule, String medicationA, String medicationB) {}

    public record InteractionAssessment(
        String decisionId,
        String patientId,
        List<InteractionMatch> matches,
        Severity highestSeverity,
        boolean requiresHumanReview,
        String recommendation
    ) {}
}
