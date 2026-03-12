package com.ghatana.tutorputor.explorer.generator;

import com.ghatana.tutorputor.explorer.model.AssessmentItem;
import com.ghatana.tutorputor.explorer.model.ContentGenerationRequest;
import com.ghatana.tutorputor.explorer.model.Domain;
import com.ghatana.tutorputor.explorer.model.LearningClaim;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

/**
 * @doc.type test
 * @doc.purpose Unit tests for AssessmentGenerator
 * @doc.layer test
 */
class AssessmentGeneratorTest {

    private AssessmentGenerator assessmentGenerator;

    @BeforeEach
    void setUp() {
        assessmentGenerator = new AssessmentGenerator();
    }

    @Test
    @DisplayName("Should generate assessments for each claim")
    void shouldGenerateAssessmentsForEachClaim() throws Exception {
        // Given
        List<LearningClaim> claims = List.of(
            LearningClaim.builder().id("c1").text("Claim 1").build(),
            LearningClaim.builder().id("c2").text("Claim 2").build(),
            LearningClaim.builder().id("c3").text("Claim 3").build()
        );
        ContentGenerationRequest request = ContentGenerationRequest.builder()
            .topic("Test Topic")
            .domain(Domain.PHYSICS)
            .tenantId("tenant-1")
            .build();

        // When
        Promise<List<AssessmentItem>> promise = assessmentGenerator.generateAssessments(claims, request);
        List<AssessmentItem> assessments = promise.get();

        // Then
        assertThat(assessments).hasSize(3);
    }

    @Test
    @DisplayName("Should create assessment with correct structure")
    void shouldCreateAssessmentWithCorrectStructure() throws Exception {
        // Given
        List<LearningClaim> claims = List.of(
            LearningClaim.builder().id("c1").text("Newton's First Law").build()
        );
        ContentGenerationRequest request = ContentGenerationRequest.builder()
            .topic("Physics")
            .domain(Domain.PHYSICS)
            .tenantId("tenant-1")
            .build();

        // When
        List<AssessmentItem> assessments = assessmentGenerator.generateAssessments(claims, request).get();

        // Then
        assertThat(assessments).hasSize(1);
        AssessmentItem assessment = assessments.get(0);
        assertThat(assessment.getQuestion()).contains("Newton's First Law");
        assertThat(assessment.getOptions()).hasSize(4);
        assertThat(assessment.getCorrectAnswerIndex()).isZero();
    }
}
