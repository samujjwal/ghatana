package com.ghatana.products.yappc.domain.model;

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
        void ofCreatesWithRequiredFieldsAndDefaults() {
            // GIVEN
            UUID workspaceId = UUID.randomUUID();
            UUID frameworkId = UUID.randomUUID();

            // WHEN
            ComplianceAssessment assessment = ComplianceAssessment.of(workspaceId, frameworkId);

            // THEN
            assertThat(assessment.getWorkspaceId()).isEqualTo(workspaceId);
            assertThat(assessment.getFrameworkId()).isEqualTo(frameworkId);
            assertThat(assessment.getStatus()).isEqualTo(STATUS_IN_PROGRESS);
            assertThat(assessment.getTotalControls()).isZero();
            assertThat(assessment.getPassedControls()).isZero();
            assertThat(assessment.getFailedControls()).isZero();
            assertThat(assessment.getNaControls()).isZero();
            assertThat(assessment.getScore()).isZero();
            assertThat(assessment.getStartedAt()).isNotNull();
            assertThat(assessment.getCreatedAt()).isNotNull();
            assertThat(assessment.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("of() throws NullPointerException when workspaceId is null")
        void ofThrowsWhenWorkspaceIdNull() {
            UUID frameworkId = UUID.randomUUID();

            assertThatThrownBy(() -> ComplianceAssessment.of(null, frameworkId))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("workspaceId must not be null");
        }

        @Test
        @DisplayName("of() throws NullPointerException when frameworkId is null")
        void ofThrowsWhenFrameworkIdNull() {
            UUID workspaceId = UUID.randomUUID();

            assertThatThrownBy(() -> ComplianceAssessment.of(workspaceId, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("frameworkId must not be null");
        }
    }

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("builder creates assessment with all fields")
        void builderCreatesWithAllFields() {
            // GIVEN
            UUID id = UUID.randomUUID();
            UUID workspaceId = UUID.randomUUID();
            UUID frameworkId = UUID.randomUUID();
            Instant now = Instant.now();

            // WHEN
            ComplianceAssessment assessment = ComplianceAssessment.builder()
                    .id(id)
                    .workspaceId(workspaceId)
                    .frameworkId(frameworkId)
                    .status(STATUS_COMPLETED)
                    .totalControls(100)
                    .passedControls(85)
                    .failedControls(10)
                    .naControls(5)
                    .score(89) // 85/(100-5)*100 = 89.47 rounded
                    .startedAt(now.minusSeconds(3600))
                    .assessedAt(now)
                    .createdAt(now.minusSeconds(7200))
                    .updatedAt(now)
                    .version(3)
                    .build();

            // THEN
            assertThat(assessment.getId()).isEqualTo(id);
            assertThat(assessment.getWorkspaceId()).isEqualTo(workspaceId);
            assertThat(assessment.getFrameworkId()).isEqualTo(frameworkId);
            assertThat(assessment.getStatus()).isEqualTo(STATUS_COMPLETED);
            assertThat(assessment.getTotalControls()).isEqualTo(100);
            assertThat(assessment.getPassedControls()).isEqualTo(85);
            assertThat(assessment.getFailedControls()).isEqualTo(10);
            assertThat(assessment.getNaControls()).isEqualTo(5);
            assertThat(assessment.getScore()).isEqualTo(89);
            assertThat(assessment.getVersion()).isEqualTo(3);
        }

        @Test
        @DisplayName("builder defaults status to IN_PROGRESS")
        void builderDefaultsStatusToInProgress() {
            ComplianceAssessment assessment = ComplianceAssessment.builder()
                    .workspaceId(UUID.randomUUID())
                    .frameworkId(UUID.randomUUID())
                    .build();

            assertThat(assessment.getStatus()).isEqualTo(STATUS_IN_PROGRESS);
        }
    }

    @Nested
    @DisplayName("Score Calculation Tests")
    class ScoreCalculationTests {

        @Test
        @DisplayName("calculateScore() computes correct percentage")
        void calculateScoreComputesCorrectPercentage() {
            // GIVEN: 80 passed out of 100 total (0 N/A) = 80%
            ComplianceAssessment assessment = ComplianceAssessment.builder()
                    .workspaceId(UUID.randomUUID())
                    .frameworkId(UUID.randomUUID())
                    .totalControls(100)
                    .passedControls(80)
                    .failedControls(20)
                    .naControls(0)
                    .build();

            // WHEN
            assessment.calculateScore();

            // THEN
            assertThat(assessment.getScore()).isEqualTo(80);
        }

        @Test
        @DisplayName("calculateScore() excludes N/A controls from denominator")
        void calculateScoreExcludesNotApplicable() {
            // GIVEN: 80 passed, 10 failed, 10 N/A = 80/(100-10)*100 = 88.89 -> 89
            ComplianceAssessment assessment = ComplianceAssessment.builder()
                    .workspaceId(UUID.randomUUID())
                    .frameworkId(UUID.randomUUID())
                    .totalControls(100)
                    .passedControls(80)
                    .failedControls(10)
                    .naControls(10)
                    .build();

            // WHEN
            assessment.calculateScore();

            // THEN
            // 80 / (100 - 10) * 100 = 88.89
            assertThat(assessment.getScore()).isGreaterThanOrEqualTo(88);
            assertThat(assessment.getScore()).isLessThanOrEqualTo(89);
        }

        @Test
        @DisplayName("calculateScore() returns 0 when all controls are N/A")
        void calculateScoreReturnsZeroWhenAllNotApplicable() {
            // GIVEN: All controls are N/A, avoid division by zero
            ComplianceAssessment assessment = ComplianceAssessment.builder()
                    .workspaceId(UUID.randomUUID())
                    .frameworkId(UUID.randomUUID())
                    .totalControls(50)
                    .passedControls(0)
                    .failedControls(0)
                    .naControls(50)
                    .build();

            // WHEN
            assessment.calculateScore();

            // THEN
            assertThat(assessment.getScore()).isZero();
        }

        @Test
        @DisplayName("calculateScore() returns 100 for perfect score")
        void calculateScoreReturns100ForPerfectScore() {
            // GIVEN: All applicable controls passed
            ComplianceAssessment assessment = ComplianceAssessment.builder()
                    .workspaceId(UUID.randomUUID())
                    .frameworkId(UUID.randomUUID())
                    .totalControls(100)
                    .passedControls(90)
                    .failedControls(0)
                    .naControls(10)
                    .build();

            // WHEN
            assessment.calculateScore();

            // THEN
            assertThat(assessment.getScore()).isEqualTo(100);
        }

        @Test
        @DisplayName("calculateScore() returns 0 when no controls passed")
        void calculateScoreReturns0WhenNoPassed() {
            ComplianceAssessment assessment = ComplianceAssessment.builder()
                    .workspaceId(UUID.randomUUID())
                    .frameworkId(UUID.randomUUID())
                    .totalControls(50)
                    .passedControls(0)
                    .failedControls(50)
                    .naControls(0)
                    .build();

            // WHEN
            assessment.calculateScore();

            // THEN
            assertThat(assessment.getScore()).isZero();
        }

        @Test
        @DisplayName("calculateScore() returns this for fluent chaining")
        void calculateScoreReturnsSelfForChaining() {
            ComplianceAssessment assessment = ComplianceAssessment.builder()
                    .workspaceId(UUID.randomUUID())
                    .frameworkId(UUID.randomUUID())
                    .totalControls(100)
                    .passedControls(80)
                    .failedControls(20)
                    .build();

            ComplianceAssessment result = assessment.calculateScore();

            assertThat(result).isSameAs(assessment);
        }
    }

    @Nested
    @DisplayName("Complete Method Tests")
    class CompleteMethodTests {

        @Test
        @DisplayName("complete() sets status to COMPLETED and assessedAt")
        void completeSetsStatusAndTimestamp() {
            // GIVEN
            ComplianceAssessment assessment = ComplianceAssessment.of(
                    UUID.randomUUID(),
                    UUID.randomUUID()
            );
            assertThat(assessment.getStatus()).isEqualTo(STATUS_IN_PROGRESS);
            assertThat(assessment.getAssessedAt()).isNull();

            // WHEN
            assessment.complete();

            // THEN
            assertThat(assessment.getStatus()).isEqualTo(STATUS_COMPLETED);
            assertThat(assessment.getAssessedAt()).isNotNull();
        }

        @Test
        @DisplayName("complete() also calculates the score")
        void completeAlsoCalculatesScore() {
            // GIVEN
            ComplianceAssessment assessment = ComplianceAssessment.builder()
                    .workspaceId(UUID.randomUUID())
                    .frameworkId(UUID.randomUUID())
                    .totalControls(100)
                    .passedControls(75)
                    .failedControls(25)
                    .naControls(0)
                    .build();

            // WHEN
            assessment.complete();

            // THEN
            assertThat(assessment.getScore()).isEqualTo(75);
            assertThat(assessment.getStatus()).isEqualTo(STATUS_COMPLETED);
        }

        @Test
        @DisplayName("complete() returns this for fluent chaining")
        void completeReturnsSelfForChaining() {
            ComplianceAssessment assessment = ComplianceAssessment.of(
                    UUID.randomUUID(),
                    UUID.randomUUID()
            );

            ComplianceAssessment result = assessment.complete();

            assertThat(result).isSameAs(assessment);
        }
    }

    @Nested
    @DisplayName("IsPassing Method Tests")
    class IsPassingMethodTests {

        @Test
        @DisplayName("isPassing() returns true when score >= 80")
        void isPassingReturnsTrueWhenScoreAbove80() {
            ComplianceAssessment assessment = ComplianceAssessment.builder()
                    .workspaceId(UUID.randomUUID())
                    .frameworkId(UUID.randomUUID())
                    .score(85)
                    .build();

            assertThat(assessment.isPassing()).isTrue();
        }

        @Test
        @DisplayName("isPassing() returns true when score == 80")
        void isPassingReturnsTrueWhenScoreEquals80() {
            ComplianceAssessment assessment = ComplianceAssessment.builder()
                    .workspaceId(UUID.randomUUID())
                    .frameworkId(UUID.randomUUID())
                    .score(80)
                    .build();

            assertThat(assessment.isPassing()).isTrue();
        }

        @Test
        @DisplayName("isPassing() returns false when score < 80")
        void isPassingReturnsFalseWhenScoreBelow80() {
            ComplianceAssessment assessment = ComplianceAssessment.builder()
                    .workspaceId(UUID.randomUUID())
                    .frameworkId(UUID.randomUUID())
                    .score(79)
                    .build();

            assertThat(assessment.isPassing()).isFalse();
        }

        @Test
        @DisplayName("isPassing() returns false for score of 0")
        void isPassingReturnsFalseForZeroScore() {
            ComplianceAssessment assessment = ComplianceAssessment.of(
                    UUID.randomUUID(),
                    UUID.randomUUID()
            );

            assertThat(assessment.isPassing()).isFalse();
        }

        @Test
        @DisplayName("isPassing() returns true for perfect score")
        void isPassingReturnsTrueForPerfectScore() {
            ComplianceAssessment assessment = ComplianceAssessment.builder()
                    .workspaceId(UUID.randomUUID())
                    .frameworkId(UUID.randomUUID())
                    .score(100)
                    .build();

            assertThat(assessment.isPassing()).isTrue();
        }
    }

    @Nested
    @DisplayName("Equality Tests")
    class EqualityTests {

        @Test
        @DisplayName("equals returns true for same id")
        void equalsReturnsTrueForSameId() {
            UUID id = UUID.randomUUID();
            ComplianceAssessment assessment1 = ComplianceAssessment.builder().id(id).workspaceId(UUID.randomUUID()).build();
            ComplianceAssessment assessment2 = ComplianceAssessment.builder().id(id).workspaceId(UUID.randomUUID()).build();

            assertThat(assessment1).isEqualTo(assessment2);
            assertThat(assessment1.hashCode()).isEqualTo(assessment2.hashCode());
        }

        @Test
        @DisplayName("equals returns false for different ids")
        void equalsReturnsFalseForDifferentIds() {
            ComplianceAssessment assessment1 = ComplianceAssessment.builder().id(UUID.randomUUID()).build();
            ComplianceAssessment assessment2 = ComplianceAssessment.builder().id(UUID.randomUUID()).build();

            assertThat(assessment1).isNotEqualTo(assessment2);
        }
    }

    @Nested
    @DisplayName("Full Lifecycle Tests")
    class LifecycleTests {

        @Test
        @DisplayName("full lifecycle: create -> set results -> complete")
        void fullLifecycle() {
            // GIVEN: Create assessment
            ComplianceAssessment assessment = ComplianceAssessment.of(
                    UUID.randomUUID(),
                    UUID.randomUUID()
            );
            assertThat(assessment.getStatus()).isEqualTo(STATUS_IN_PROGRESS);
            assertThat(assessment.getStartedAt()).isNotNull();

            // WHEN: Set control results
            assessment.setTotalControls(50);
            assessment.setPassedControls(45);
            assessment.setFailedControls(5);

            // WHEN: Complete
            assessment.complete();

            // THEN
            assertThat(assessment.getStatus()).isEqualTo(STATUS_COMPLETED);
            assertThat(assessment.getScore()).isEqualTo(90);
            assertThat(assessment.isPassing()).isTrue();
            assertThat(assessment.getAssessedAt()).isNotNull();
        }

        @Test
        @DisplayName("can store JSON details")
        void canStoreJsonDetails() {
            ComplianceAssessment assessment = ComplianceAssessment.builder()
                    .workspaceId(UUID.randomUUID())
                    .frameworkId(UUID.randomUUID())
                    .details("{\"controls\": [{\"id\": \"1\", \"status\": \"PASSED\"}]}")
                    .build();

            assertThat(assessment.getDetails()).contains("controls");
            assertThat(assessment.getDetails()).contains("PASSED");
        }
    }

    @Nested
    @DisplayName("Control Count Tests")
    class ControlCountTests {

        @Test
        @DisplayName("control counts sum correctly")
        void controlCountsSumCorrectly() {
            ComplianceAssessment assessment = ComplianceAssessment.builder()
                    .workspaceId(UUID.randomUUID())
                    .frameworkId(UUID.randomUUID())
                    .totalControls(100)
                    .passedControls(70)
                    .failedControls(20)
                    .naControls(10)
                    .build();

            int sumOfParts = assessment.getPassedControls() + 
                            assessment.getFailedControls() + 
                            assessment.getNaControls();
            
            assertThat(sumOfParts).isEqualTo(assessment.getTotalControls());
        }
    }
}
