package com.ghatana.yappc.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ComplianceAssessment} domain model.
 *
 * @doc.type class
 * @doc.purpose Validates ComplianceAssessment entity behavior, score calculation, and lifecycle
 * @doc.layer product
 * @doc.pattern UnitTest
 */
@DisplayName("ComplianceAssessment Domain Model Tests")
class ComplianceAssessmentTest {

    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_COMPLETED = "COMPLETED";

    @Nested
    @DisplayName("Factory Method Tests")
    class FactoryMethodTests {

        @Test
        @DisplayName("of() creates assessment with required fields and defaults")
        void ofCreatesWithRequiredFieldsAndDefaults() { // GH-90000
            // GIVEN
            UUID workspaceId = UUID.randomUUID(); // GH-90000
            UUID frameworkId = UUID.randomUUID(); // GH-90000

            // WHEN
            ComplianceAssessment assessment = ComplianceAssessment.of(workspaceId, frameworkId); // GH-90000

            // THEN
            assertThat(assessment.getWorkspaceId()).isEqualTo(workspaceId); // GH-90000
            assertThat(assessment.getFrameworkId()).isEqualTo(frameworkId); // GH-90000
            assertThat(assessment.getStatus()).isEqualTo(STATUS_IN_PROGRESS); // GH-90000
            assertThat(assessment.getTotalControls()).isZero(); // GH-90000
            assertThat(assessment.getPassedControls()).isZero(); // GH-90000
            assertThat(assessment.getFailedControls()).isZero(); // GH-90000
            assertThat(assessment.getNaControls()).isZero(); // GH-90000
            assertThat(assessment.getScore()).isZero(); // GH-90000
            assertThat(assessment.getStartedAt()).isNotNull(); // GH-90000
            assertThat(assessment.getCreatedAt()).isNotNull(); // GH-90000
            assertThat(assessment.getUpdatedAt()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("of() throws NullPointerException when workspaceId is null")
        void ofThrowsWhenWorkspaceIdNull() { // GH-90000
            UUID frameworkId = UUID.randomUUID(); // GH-90000

            assertThatThrownBy(() -> ComplianceAssessment.of(null, frameworkId)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("workspaceId must not be null");
        }

        @Test
        @DisplayName("of() throws NullPointerException when frameworkId is null")
        void ofThrowsWhenFrameworkIdNull() { // GH-90000
            UUID workspaceId = UUID.randomUUID(); // GH-90000

            assertThatThrownBy(() -> ComplianceAssessment.of(workspaceId, null)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("frameworkId must not be null");
        }
    }

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("builder creates assessment with all fields")
        void builderCreatesWithAllFields() { // GH-90000
            // GIVEN
            UUID id = UUID.randomUUID(); // GH-90000
            UUID workspaceId = UUID.randomUUID(); // GH-90000
            UUID frameworkId = UUID.randomUUID(); // GH-90000
            Instant now = Instant.now(); // GH-90000

            // WHEN
            ComplianceAssessment assessment = ComplianceAssessment.builder() // GH-90000
                    .id(id) // GH-90000
                    .workspaceId(workspaceId) // GH-90000
                    .frameworkId(frameworkId) // GH-90000
                    .status(STATUS_COMPLETED) // GH-90000
                    .totalControls(100) // GH-90000
                    .passedControls(85) // GH-90000
                    .failedControls(10) // GH-90000
                    .naControls(5) // GH-90000
                    .score(89) // 85/(100-5)*100 = 89.47 rounded // GH-90000
                    .startedAt(now.minusSeconds(3600)) // GH-90000
                    .assessedAt(now) // GH-90000
                    .createdAt(now.minusSeconds(7200)) // GH-90000
                    .updatedAt(now) // GH-90000
                    .version(3) // GH-90000
                    .build(); // GH-90000

            // THEN
            assertThat(assessment.getId()).isEqualTo(id); // GH-90000
            assertThat(assessment.getWorkspaceId()).isEqualTo(workspaceId); // GH-90000
            assertThat(assessment.getFrameworkId()).isEqualTo(frameworkId); // GH-90000
            assertThat(assessment.getStatus()).isEqualTo(STATUS_COMPLETED); // GH-90000
            assertThat(assessment.getTotalControls()).isEqualTo(100); // GH-90000
            assertThat(assessment.getPassedControls()).isEqualTo(85); // GH-90000
            assertThat(assessment.getFailedControls()).isEqualTo(10); // GH-90000
            assertThat(assessment.getNaControls()).isEqualTo(5); // GH-90000
            assertThat(assessment.getScore()).isEqualTo(89); // GH-90000
            assertThat(assessment.getVersion()).isEqualTo(3); // GH-90000
        }

        @Test
        @DisplayName("builder defaults status to IN_PROGRESS")
        void builderDefaultsStatusToInProgress() { // GH-90000
            ComplianceAssessment assessment = ComplianceAssessment.builder() // GH-90000
                    .workspaceId(UUID.randomUUID()) // GH-90000
                    .frameworkId(UUID.randomUUID()) // GH-90000
                    .build(); // GH-90000

            assertThat(assessment.getStatus()).isEqualTo(STATUS_IN_PROGRESS); // GH-90000
        }
    }

    @Nested
    @DisplayName("Score Calculation Tests")
    class ScoreCalculationTests {

        @Test
        @DisplayName("calculateScore() computes correct percentage")
        void calculateScoreComputesCorrectPercentage() { // GH-90000
            // GIVEN: 80 passed out of 100 total (0 N/A) = 80% // GH-90000
            ComplianceAssessment assessment = ComplianceAssessment.builder() // GH-90000
                    .workspaceId(UUID.randomUUID()) // GH-90000
                    .frameworkId(UUID.randomUUID()) // GH-90000
                    .totalControls(100) // GH-90000
                    .passedControls(80) // GH-90000
                    .failedControls(20) // GH-90000
                    .naControls(0) // GH-90000
                    .build(); // GH-90000

            // WHEN
            assessment.calculateScore(); // GH-90000

            // THEN
            assertThat(assessment.getScore()).isEqualTo(80); // GH-90000
        }

        @Test
        @DisplayName("calculateScore() excludes N/A controls from denominator")
        void calculateScoreExcludesNotApplicable() { // GH-90000
            // GIVEN: 80 passed, 10 failed, 10 N/A = 80/(100-10)*100 = 88.89 -> 89 // GH-90000
            ComplianceAssessment assessment = ComplianceAssessment.builder() // GH-90000
                    .workspaceId(UUID.randomUUID()) // GH-90000
                    .frameworkId(UUID.randomUUID()) // GH-90000
                    .totalControls(100) // GH-90000
                    .passedControls(80) // GH-90000
                    .failedControls(10) // GH-90000
                    .naControls(10) // GH-90000
                    .build(); // GH-90000

            // WHEN
            assessment.calculateScore(); // GH-90000

            // THEN
            // 80 / (100 - 10) * 100 = 88.89 // GH-90000
            assertThat(assessment.getScore()).isGreaterThanOrEqualTo(88); // GH-90000
            assertThat(assessment.getScore()).isLessThanOrEqualTo(89); // GH-90000
        }

        @Test
        @DisplayName("calculateScore() returns 0 when all controls are N/A")
        void calculateScoreReturnsZeroWhenAllNotApplicable() { // GH-90000
            // GIVEN: All controls are N/A, avoid division by zero
            ComplianceAssessment assessment = ComplianceAssessment.builder() // GH-90000
                    .workspaceId(UUID.randomUUID()) // GH-90000
                    .frameworkId(UUID.randomUUID()) // GH-90000
                    .totalControls(50) // GH-90000
                    .passedControls(0) // GH-90000
                    .failedControls(0) // GH-90000
                    .naControls(50) // GH-90000
                    .build(); // GH-90000

            // WHEN
            assessment.calculateScore(); // GH-90000

            // THEN
            assertThat(assessment.getScore()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("calculateScore() returns 100 for perfect score")
        void calculateScoreReturns100ForPerfectScore() { // GH-90000
            // GIVEN: All applicable controls passed
            ComplianceAssessment assessment = ComplianceAssessment.builder() // GH-90000
                    .workspaceId(UUID.randomUUID()) // GH-90000
                    .frameworkId(UUID.randomUUID()) // GH-90000
                    .totalControls(100) // GH-90000
                    .passedControls(90) // GH-90000
                    .failedControls(0) // GH-90000
                    .naControls(10) // GH-90000
                    .build(); // GH-90000

            // WHEN
            assessment.calculateScore(); // GH-90000

            // THEN
            assertThat(assessment.getScore()).isEqualTo(100); // GH-90000
        }

        @Test
        @DisplayName("calculateScore() returns 0 when no controls passed")
        void calculateScoreReturns0WhenNoPassed() { // GH-90000
            ComplianceAssessment assessment = ComplianceAssessment.builder() // GH-90000
                    .workspaceId(UUID.randomUUID()) // GH-90000
                    .frameworkId(UUID.randomUUID()) // GH-90000
                    .totalControls(50) // GH-90000
                    .passedControls(0) // GH-90000
                    .failedControls(50) // GH-90000
                    .naControls(0) // GH-90000
                    .build(); // GH-90000

            // WHEN
            assessment.calculateScore(); // GH-90000

            // THEN
            assertThat(assessment.getScore()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("calculateScore() returns this for fluent chaining")
        void calculateScoreReturnsSelfForChaining() { // GH-90000
            ComplianceAssessment assessment = ComplianceAssessment.builder() // GH-90000
                    .workspaceId(UUID.randomUUID()) // GH-90000
                    .frameworkId(UUID.randomUUID()) // GH-90000
                    .totalControls(100) // GH-90000
                    .passedControls(80) // GH-90000
                    .failedControls(20) // GH-90000
                    .build(); // GH-90000

            ComplianceAssessment result = assessment.calculateScore(); // GH-90000

            assertThat(result).isSameAs(assessment); // GH-90000
        }
    }

    @Nested
    @DisplayName("Complete Method Tests")
    class CompleteMethodTests {

        @Test
        @DisplayName("complete() sets status to COMPLETED and assessedAt")
        void completeSetsStatusAndTimestamp() { // GH-90000
            // GIVEN
            ComplianceAssessment assessment = ComplianceAssessment.of( // GH-90000
                    UUID.randomUUID(), // GH-90000
                    UUID.randomUUID() // GH-90000
            );
            assertThat(assessment.getStatus()).isEqualTo(STATUS_IN_PROGRESS); // GH-90000
            assertThat(assessment.getAssessedAt()).isNull(); // GH-90000

            // WHEN
            assessment.complete(); // GH-90000

            // THEN
            assertThat(assessment.getStatus()).isEqualTo(STATUS_COMPLETED); // GH-90000
            assertThat(assessment.getAssessedAt()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("complete() also calculates the score")
        void completeAlsoCalculatesScore() { // GH-90000
            // GIVEN
            ComplianceAssessment assessment = ComplianceAssessment.builder() // GH-90000
                    .workspaceId(UUID.randomUUID()) // GH-90000
                    .frameworkId(UUID.randomUUID()) // GH-90000
                    .totalControls(100) // GH-90000
                    .passedControls(75) // GH-90000
                    .failedControls(25) // GH-90000
                    .naControls(0) // GH-90000
                    .build(); // GH-90000

            // WHEN
            assessment.complete(); // GH-90000

            // THEN
            assertThat(assessment.getScore()).isEqualTo(75); // GH-90000
            assertThat(assessment.getStatus()).isEqualTo(STATUS_COMPLETED); // GH-90000
        }

        @Test
        @DisplayName("complete() returns this for fluent chaining")
        void completeReturnsSelfForChaining() { // GH-90000
            ComplianceAssessment assessment = ComplianceAssessment.of( // GH-90000
                    UUID.randomUUID(), // GH-90000
                    UUID.randomUUID() // GH-90000
            );

            ComplianceAssessment result = assessment.complete(); // GH-90000

            assertThat(result).isSameAs(assessment); // GH-90000
        }
    }

    @Nested
    @DisplayName("IsPassing Method Tests")
    class IsPassingMethodTests {

        @Test
        @DisplayName("isPassing() returns true when score >= 80")
        void isPassingReturnsTrueWhenScoreAbove80() { // GH-90000
            ComplianceAssessment assessment = ComplianceAssessment.builder() // GH-90000
                    .workspaceId(UUID.randomUUID()) // GH-90000
                    .frameworkId(UUID.randomUUID()) // GH-90000
                    .score(85) // GH-90000
                    .build(); // GH-90000

            assertThat(assessment.isPassing()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("isPassing() returns true when score == 80")
        void isPassingReturnsTrueWhenScoreEquals80() { // GH-90000
            ComplianceAssessment assessment = ComplianceAssessment.builder() // GH-90000
                    .workspaceId(UUID.randomUUID()) // GH-90000
                    .frameworkId(UUID.randomUUID()) // GH-90000
                    .score(80) // GH-90000
                    .build(); // GH-90000

            assertThat(assessment.isPassing()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("isPassing() returns false when score < 80")
        void isPassingReturnsFalseWhenScoreBelow80() { // GH-90000
            ComplianceAssessment assessment = ComplianceAssessment.builder() // GH-90000
                    .workspaceId(UUID.randomUUID()) // GH-90000
                    .frameworkId(UUID.randomUUID()) // GH-90000
                    .score(79) // GH-90000
                    .build(); // GH-90000

            assertThat(assessment.isPassing()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("isPassing() returns false for score of 0")
        void isPassingReturnsFalseForZeroScore() { // GH-90000
            ComplianceAssessment assessment = ComplianceAssessment.of( // GH-90000
                    UUID.randomUUID(), // GH-90000
                    UUID.randomUUID() // GH-90000
            );

            assertThat(assessment.isPassing()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("isPassing() returns true for perfect score")
        void isPassingReturnsTrueForPerfectScore() { // GH-90000
            ComplianceAssessment assessment = ComplianceAssessment.builder() // GH-90000
                    .workspaceId(UUID.randomUUID()) // GH-90000
                    .frameworkId(UUID.randomUUID()) // GH-90000
                    .score(100) // GH-90000
                    .build(); // GH-90000

            assertThat(assessment.isPassing()).isTrue(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Equality Tests")
    class EqualityTests {

        @Test
        @DisplayName("equals returns true for same id")
        void equalsReturnsTrueForSameId() { // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            ComplianceAssessment assessment1 = ComplianceAssessment.builder().id(id).workspaceId(UUID.randomUUID()).build(); // GH-90000
            ComplianceAssessment assessment2 = ComplianceAssessment.builder().id(id).workspaceId(UUID.randomUUID()).build(); // GH-90000

            assertThat(assessment1).isEqualTo(assessment2); // GH-90000
            assertThat(assessment1.hashCode()).isEqualTo(assessment2.hashCode()); // GH-90000
        }

        @Test
        @DisplayName("equals returns false for different ids")
        void equalsReturnsFalseForDifferentIds() { // GH-90000
            ComplianceAssessment assessment1 = ComplianceAssessment.builder().id(UUID.randomUUID()).build(); // GH-90000
            ComplianceAssessment assessment2 = ComplianceAssessment.builder().id(UUID.randomUUID()).build(); // GH-90000

            assertThat(assessment1).isNotEqualTo(assessment2); // GH-90000
        }
    }

    @Nested
    @DisplayName("Full Lifecycle Tests")
    class LifecycleTests {

        @Test
        @DisplayName("full lifecycle: create -> set results -> complete")
        void fullLifecycle() { // GH-90000
            // GIVEN: Create assessment
            ComplianceAssessment assessment = ComplianceAssessment.of( // GH-90000
                    UUID.randomUUID(), // GH-90000
                    UUID.randomUUID() // GH-90000
            );
            assertThat(assessment.getStatus()).isEqualTo(STATUS_IN_PROGRESS); // GH-90000
            assertThat(assessment.getStartedAt()).isNotNull(); // GH-90000

            // WHEN: Set control results
            assessment.setTotalControls(50); // GH-90000
            assessment.setPassedControls(45); // GH-90000
            assessment.setFailedControls(5); // GH-90000

            // WHEN: Complete
            assessment.complete(); // GH-90000

            // THEN
            assertThat(assessment.getStatus()).isEqualTo(STATUS_COMPLETED); // GH-90000
            assertThat(assessment.getScore()).isEqualTo(90); // GH-90000
            assertThat(assessment.isPassing()).isTrue(); // GH-90000
            assertThat(assessment.getAssessedAt()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("can store JSON details")
        void canStoreJsonDetails() { // GH-90000
            ComplianceAssessment assessment = ComplianceAssessment.builder() // GH-90000
                    .workspaceId(UUID.randomUUID()) // GH-90000
                    .frameworkId(UUID.randomUUID()) // GH-90000
                    .details("{\"controls\": [{\"id\": \"1\", \"status\": \"PASSED\"}]}") // GH-90000
                    .build(); // GH-90000

            assertThat(assessment.getDetails()).contains("controls");
            assertThat(assessment.getDetails()).contains("PASSED");
        }
    }

    @Nested
    @DisplayName("Control Count Tests")
    class ControlCountTests {

        @Test
        @DisplayName("control counts sum correctly")
        void controlCountsSumCorrectly() { // GH-90000
            ComplianceAssessment assessment = ComplianceAssessment.builder() // GH-90000
                    .workspaceId(UUID.randomUUID()) // GH-90000
                    .frameworkId(UUID.randomUUID()) // GH-90000
                    .totalControls(100) // GH-90000
                    .passedControls(70) // GH-90000
                    .failedControls(20) // GH-90000
                    .naControls(10) // GH-90000
                    .build(); // GH-90000

            int sumOfParts = assessment.getPassedControls() + // GH-90000
                            assessment.getFailedControls() + // GH-90000
                            assessment.getNaControls(); // GH-90000

            assertThat(sumOfParts).isEqualTo(assessment.getTotalControls()); // GH-90000
        }
    }
}
