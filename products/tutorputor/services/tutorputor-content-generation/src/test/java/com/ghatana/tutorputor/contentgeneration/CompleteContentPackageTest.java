package com.ghatana.tutorputor.explorer.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

/**
 * @doc.type test
 * @doc.purpose Unit tests for CompleteContentPackage model
 * @doc.layer test
 */
class CompleteContentPackageTest {

    @Test
    @DisplayName("Should create CompleteContentPackage with all components")
    void shouldCreateCompleteContentPackage() {
        // Given
        List<LearningClaim> claims = List.of(
            LearningClaim.builder().id("c1").text("Claim 1").domain("PHYSICS").gradeLevel("HS").build()
        );
        List<LearningEvidence> evidence = List.of(
            LearningEvidence.builder().id("e1").claimId("c1").type("EXAMPLE").content("Evidence").build()
        );
        List<ContentExample> examples = List.of(
            ContentExample.builder().id("ex1").claimId("c1").title("Example").description("Desc").build()
        );
        List<SimulationManifest> simulations = List.of(
            SimulationManifest.builder().id("s1").title("Sim").domain("PHYSICS").build()
        );
        List<AnimationConfig> animations = List.of(
            AnimationConfig.builder().id("a1").title("Anim").durationMs(1000).build()
        );
        List<AssessmentItem> assessments = List.of(
            AssessmentItem.builder().id("as1").question("Q1").build()
        );
        QualityReport qualityReport = QualityReport.builder().passed(true).overallScore(0.9).build();

        // When
        CompleteContentPackage pkg = CompleteContentPackage.builder()
            .claims(claims)
            .evidence(evidence)
            .examples(examples)
            .simulations(simulations)
            .animations(animations)
            .assessments(assessments)
            .qualityReport(qualityReport)
            .generationDurationMs(5000)
            .build();

        // Then
        assertThat(pkg.getClaims()).hasSize(1);
        assertThat(pkg.getEvidence()).hasSize(1);
        assertThat(pkg.getExamples()).hasSize(1);
        assertThat(pkg.getSimulations()).hasSize(1);
        assertThat(pkg.getAnimations()).hasSize(1);
        assertThat(pkg.getAssessments()).hasSize(1);
        assertThat(pkg.getQualityReport().isPassed()).isTrue();
        assertThat(pkg.getGenerationDurationMs()).isEqualTo(5000);
    }

    @Test
    @DisplayName("Should create CompleteContentPackage with empty lists")
    void shouldCreatePackageWithEmptyLists() {
        // When
        CompleteContentPackage pkg = CompleteContentPackage.builder()
            .claims(List.of())
            .evidence(List.of())
            .examples(List.of())
            .simulations(List.of())
            .animations(List.of())
            .assessments(List.of())
            .qualityReport(QualityReport.builder().passed(false).overallScore(0.0).build())
            .generationDurationMs(0)
            .build();

        // Then
        assertThat(pkg.getClaims()).isEmpty();
        assertThat(pkg.getEvidence()).isEmpty();
        assertThat(pkg.getExamples()).isEmpty();
        assertThat(pkg.getSimulations()).isEmpty();
        assertThat(pkg.getAnimations()).isEmpty();
        assertThat(pkg.getAssessments()).isEmpty();
        assertThat(pkg.getQualityReport().getOverallScore()).isZero();
    }
}
