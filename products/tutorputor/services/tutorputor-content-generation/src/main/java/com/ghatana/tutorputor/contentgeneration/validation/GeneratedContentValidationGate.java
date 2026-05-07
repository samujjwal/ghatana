package com.ghatana.tutorputor.contentgeneration.validation;

import com.ghatana.tutorputor.contentgeneration.domain.AssessmentItem;
import com.ghatana.tutorputor.contentgeneration.domain.ContentExample;
import com.ghatana.tutorputor.contentgeneration.domain.LearningClaim;
import com.ghatana.tutorputor.contentgeneration.domain.LearningEvidence;
import com.ghatana.tutorputor.contentgeneration.domain.QualityReport;
import com.ghatana.tutorputor.contentgeneration.domain.SimulationManifest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic quality gate for AI-generated packages before CMS review intake.
 * @doc.type class
 * @doc.purpose Validates AI-generated content packages before CMS review intake
 * @doc.layer product
 * @doc.pattern ValidationGate
 */
public final class GeneratedContentValidationGate {

    private GeneratedContentValidationGate() {}

    public static QualityReport validate(GeneratedContentPackage contentPackage) {
        List<String> issues = new ArrayList<>();
        List<LearningClaim> claims = contentPackage.claims();
        List<LearningEvidence> evidence = contentPackage.evidence();
        List<ContentExample> examples = contentPackage.examples();
        List<SimulationManifest> simulations = contentPackage.simulations();
        List<AssessmentItem> assessments = contentPackage.assessments();

        requireNonEmpty(claims, "claims", issues);
        requireNonEmpty(evidence, "evidence", issues);
        requireNonEmpty(examples, "examples", issues);
        requireNonEmpty(simulations, "simulations", issues);
        requireNonEmpty(assessments, "assessments", issues);

        Set<String> claimIds = collectClaimIds(claims, issues);
        validateEvidence(evidence, claimIds, issues);
        validateExamples(examples, claimIds, issues);
        validateSimulations(simulations, claimIds, issues);
        validateAssessments(assessments, issues);

        // Weighted scoring — each issue deducts 0.1 from 1.0, floored at 0.0.
        // A package with zero issues scores 1.0; five or more structural issues score 0.0.
        double overallScore = Math.max(0.0, 1.0 - (issues.size() * 0.1));
        boolean passed = issues.isEmpty();
        return QualityReport.builder()
                .passed(passed)
                .overallScore(overallScore)
                .issues(issues)
                .build();
    }

    private static void requireNonEmpty(List<?> items, String label, List<String> issues) {
        if (items == null || items.isEmpty()) {
            issues.add("Generated package must include " + label);
        }
    }

    private static Set<String> collectClaimIds(List<LearningClaim> claims, List<String> issues) {
        Set<String> claimIds = new HashSet<>();
        if (claims == null) {
            return claimIds;
        }

        for (LearningClaim claim : claims) {
            if (isBlank(claim.getId())) {
                issues.add("Claim is missing an id");
                continue;
            }
            if (isBlank(claim.getText())) {
                issues.add("Claim " + claim.getId() + " is missing text");
            }
            if (!claimIds.add(claim.getId())) {
                issues.add("Duplicate claim id: " + claim.getId());
            }
        }
        return claimIds;
    }

    private static void validateEvidence(List<LearningEvidence> evidence, Set<String> claimIds, List<String> issues) {
        if (evidence == null) {
            return;
        }

        Set<String> evidenceIds = new HashSet<>();
        for (LearningEvidence item : evidence) {
            if (isBlank(item.getId()) || !evidenceIds.add(item.getId())) {
                issues.add("Evidence item has missing or duplicate id");
            }
            if (!claimIds.contains(item.getClaimId())) {
                issues.add("Evidence " + item.getId() + " references unknown claim " + item.getClaimId());
            }
            if (isBlank(item.getType()) || isBlank(item.getContent())) {
                issues.add("Evidence " + item.getId() + " must include type and content");
            }
        }
    }

    private static void validateExamples(List<ContentExample> examples, Set<String> claimIds, List<String> issues) {
        if (examples == null) {
            return;
        }

        for (ContentExample example : examples) {
            if (!claimIds.contains(example.getClaimId())) {
                issues.add("Example " + example.getId() + " references unknown claim " + example.getClaimId());
            }
            if (isBlank(example.getTitle()) || isBlank(example.getDescription()) || example.getSteps() == null || example.getSteps().isEmpty()) {
                issues.add("Example " + example.getId() + " must include title, description, and steps");
            }
        }
    }

    private static void validateSimulations(List<SimulationManifest> simulations, Set<String> claimIds, List<String> issues) {
        if (simulations == null) {
            return;
        }

        for (SimulationManifest simulation : simulations) {
            Map<String, Object> configuration = simulation.getConfiguration();
            Object claimId = configuration == null ? null : configuration.get("claimId");
            Object seed = configuration == null ? null : configuration.get("seed");
            Object parameterBounds = configuration == null ? null : configuration.get("parameterBounds");
            Object expectedOutputs = configuration == null ? null : configuration.get("expectedOutputs");

            if (isBlank(simulation.getTitle()) || isBlank(simulation.getDescription())) {
                issues.add("Simulation " + simulation.getId() + " must include title and description");
            }
            if (!(claimId instanceof String claimIdValue) || !claimIds.contains(claimIdValue)) {
                issues.add("Simulation " + simulation.getId() + " must reference a known claimId");
            }
            if (!(seed instanceof Number) || !(parameterBounds instanceof Map<?, ?>) || !(expectedOutputs instanceof Map<?, ?>)) {
                issues.add("Simulation " + simulation.getId() + " must include seed, parameterBounds, and expectedOutputs");
            }
        }
    }

    private static void validateAssessments(List<AssessmentItem> assessments, List<String> issues) {
        if (assessments == null) {
            return;
        }

        for (AssessmentItem assessment : assessments) {
            List<String> options = assessment.getOptions();
            if (isBlank(assessment.getQuestion()) || options == null || options.size() < 2) {
                issues.add("Assessment " + assessment.getId() + " must include a question and at least two options");
                continue;
            }
            if (assessment.getCorrectAnswerIndex() < 0 || assessment.getCorrectAnswerIndex() >= options.size()) {
                issues.add("Assessment " + assessment.getId() + " has an invalid correctAnswerIndex");
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record GeneratedContentPackage(
            List<LearningClaim> claims,
            List<LearningEvidence> evidence,
            List<ContentExample> examples,
            List<SimulationManifest> simulations,
            List<AssessmentItem> assessments
    ) {}
}
