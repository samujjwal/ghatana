package com.ghatana.tutorputor.explorer.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

/**
 * @doc.type test
 * @doc.purpose Unit tests for LearningClaim model with 100% coverage
 * @doc.layer test
 */
class LearningClaimTest {

    @Test
    @DisplayName("Should create LearningClaim using builder with all fields")
    void shouldCreateLearningClaimWithBuilder() {
        // Given
        String id = "claim-123";
        String text = "Students understand Newton's laws";
        String domain = "PHYSICS";
        String gradeLevel = "HIGH_SCHOOL";
        List<String> prerequisites = List.of("Basic algebra", "Vectors");

        // When
        LearningClaim claim = LearningClaim.builder()
            .id(id)
            .text(text)
            .domain(domain)
            .gradeLevel(gradeLevel)
            .prerequisites(prerequisites)
            .build();

        // Then
        assertThat(claim.getId()).isEqualTo(id);
        assertThat(claim.getText()).isEqualTo(text);
        assertThat(claim.getDomain()).isEqualTo(domain);
        assertThat(claim.getGradeLevel()).isEqualTo(gradeLevel);
        assertThat(claim.getPrerequisites()).isEqualTo(prerequisites);
    }

    @Test
    @DisplayName("Should create LearningClaim with null prerequisites")
    void shouldCreateLearningClaimWithNullPrerequisites() {
        // When
        LearningClaim claim = LearningClaim.builder()
            .id("claim-456")
            .text("Basic concept")
            .domain("MATH")
            .gradeLevel("ELEMENTARY")
            .prerequisites(null)
            .build();

        // Then
        assertThat(claim.getPrerequisites()).isNull();
    }

    @Test
    @DisplayName("Should create LearningClaim with empty prerequisites")
    void shouldCreateLearningClaimWithEmptyPrerequisites() {
        // When
        LearningClaim claim = LearningClaim.builder()
            .id("claim-789")
            .text("Simple concept")
            .domain("BIOLOGY")
            .gradeLevel("MIDDLE_SCHOOL")
            .prerequisites(List.of())
            .build();

        // Then
        assertThat(claim.getPrerequisites()).isEmpty();
    }

    @Test
    @DisplayName("Should handle long text in LearningClaim")
    void shouldHandleLongText() {
        // Given
        String longText = "A".repeat(1000);

        // When
        LearningClaim claim = LearningClaim.builder()
            .id("claim-long")
            .text(longText)
            .domain("CHEMISTRY")
            .gradeLevel("COLLEGE")
            .prerequisites(List.of())
            .build();

        // Then
        assertThat(claim.getText()).hasSize(1000);
    }
}
