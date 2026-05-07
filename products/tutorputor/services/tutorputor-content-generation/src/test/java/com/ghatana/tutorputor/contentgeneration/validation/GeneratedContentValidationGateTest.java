package com.ghatana.tutorputor.contentgeneration.validation;

import com.ghatana.tutorputor.contentgeneration.domain.AssessmentItem;
import com.ghatana.tutorputor.contentgeneration.domain.ContentExample;
import com.ghatana.tutorputor.contentgeneration.domain.LearningClaim;
import com.ghatana.tutorputor.contentgeneration.domain.LearningEvidence;
import com.ghatana.tutorputor.contentgeneration.domain.QualityReport;
import com.ghatana.tutorputor.contentgeneration.domain.SimulationManifest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Generated Content Validation Gate")
class GeneratedContentValidationGateTest {

    @Test
    @DisplayName("accepts deterministic math, science, and business golden packages")
    void acceptsGoldenPackages() {
        List<GeneratedContentValidationGate.GeneratedContentPackage> goldenPackages = List.of(
                packageFor("math-claim-1", "Explain why equivalent fractions represent the same value", "MATH"),
                packageFor("science-claim-1", "Predict how force changes acceleration for a fixed mass", "SCIENCE"),
                packageFor("business-claim-1", "Calculate contribution margin from price and variable cost", "BUSINESS")
        );

        for (GeneratedContentValidationGate.GeneratedContentPackage contentPackage : goldenPackages) {
            QualityReport report = GeneratedContentValidationGate.validate(contentPackage);

            assertThat(report.isPassed()).isTrue();
            assertThat(report.getOverallScore()).isEqualTo(1.0);
            assertThat(report.getIssues()).isEmpty();
        }
    }

    @Test
    @DisplayName("rejects generated packages before CMS review when evidence, simulation, or assessment metadata is incomplete")
    void rejectsIncompleteGeneratedPackages() {
        GeneratedContentValidationGate.GeneratedContentPackage invalidPackage =
                new GeneratedContentValidationGate.GeneratedContentPackage(
                        List.of(claim("claim-1", "Explain unit rate", "MATH")),
                        List.of(evidence("evidence-1", "missing-claim")),
                        List.of(example("example-1", "claim-1")),
                        List.of(SimulationManifest.builder()
                                .id("simulation-1")
                                .title("Unit rate table")
                                .description("Change numerator and denominator")
                                .domain("MATH")
                                .configuration(Map.of("claimId", "claim-1"))
                                .build()),
                        List.of(AssessmentItem.builder()
                                .id("assessment-1")
                                .question("What is the unit rate?")
                                .options(List.of("3"))
                                .correctAnswerIndex(2)
                                .build())
                );

        QualityReport report = GeneratedContentValidationGate.validate(invalidPackage);

        assertThat(report.isPassed()).isFalse();
        assertThat(report.getIssues()).contains(
                "Evidence evidence-1 references unknown claim missing-claim",
                "Simulation simulation-1 must include seed, parameterBounds, and expectedOutputs",
                "Assessment assessment-1 must include a question and at least two options"
        );
    }

    private static GeneratedContentValidationGate.GeneratedContentPackage packageFor(String claimId, String claimText, String domain) {
        return new GeneratedContentValidationGate.GeneratedContentPackage(
                List.of(claim(claimId, claimText, domain)),
                List.of(evidence("evidence-" + claimId, claimId)),
                List.of(example("example-" + claimId, claimId)),
                List.of(simulation("simulation-" + claimId, claimId, domain)),
                List.of(assessment("assessment-" + claimId, claimText))
        );
    }

    private static LearningClaim claim(String id, String text, String domain) {
        return LearningClaim.builder()
                .id(id)
                .text(text)
                .domain(domain)
                .gradeLevel("GRADE_8")
                .prerequisites(List.of())
                .build();
    }

    private static LearningEvidence evidence(String id, String claimId) {
        return LearningEvidence.builder()
                .id(id)
                .claimId(claimId)
                .type("assessment")
                .content("Learner explains the claim and applies it to a worked problem.")
                .build();
    }

    private static ContentExample example(String id, String claimId) {
        return ContentExample.builder()
                .id(id)
                .claimId(claimId)
                .title("Worked example")
                .description("A deterministic worked example aligned to the claim.")
                .steps(List.of("Read the scenario", "Apply the rule", "Check the result"))
                .visualAidDescription("Labeled diagram or table")
                .gradeLevel("GRADE_8")
                .domain("MATH")
                .createdAt(1_700_000_000_000L)
                .build();
    }

    private static SimulationManifest simulation(String id, String claimId, String domain) {
        return SimulationManifest.builder()
                .id(id)
                .title("Guided simulation")
                .description("Learner changes bounded inputs and observes deterministic outputs.")
                .domain(domain)
                .configuration(Map.of(
                        "claimId", claimId,
                        "seed", 42,
                        "parameterBounds", Map.of("input", List.of(0, 10)),
                        "expectedOutputs", Map.of("baseline", 5)
                ))
                .build();
    }

    private static AssessmentItem assessment(String id, String claimText) {
        return AssessmentItem.builder()
                .id(id)
                .question("Which answer demonstrates: " + claimText + "?")
                .options(List.of("Correct application", "Distractor", "Partial misconception"))
                .correctAnswerIndex(0)
                .build();
    }
}
