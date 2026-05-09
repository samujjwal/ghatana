package com.ghatana.tutorputor.contentgeneration;

import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for GenerationPublishPolicy.
 *
 * <p>Tests every policy branch, edge case, and integration scenario.
 *
 * @doc.type test
 * @doc.purpose Unit tests for content auto-publish policy
 * @doc.layer test
 */
@DisplayName("GenerationPublishPolicy Tests")
class GenerationPublishPolicyTest {

    private static final String TENANT_ID = "tenant-123";

    @Nested
    @DisplayName("Feature Flag Gate")
    class FeatureFlagGateTests {

        @Test
        @DisplayName("Feature flag off should return review_required even with perfect scores")
        void featureFlagOffReturnsReviewRequired() {
            // Arrange
            GenerationPublishPolicy.FeatureFlagService flagService = new PlatformFeatureFlagService(
                    Map.of("autonomous_content_auto_publish", false)
            );
            GenerationPublishPolicy policy = new GenerationPublishPolicy(flagService);

            GenerationPublishPolicy.PolicyInput input = new GenerationPublishPolicy.PolicyInput(
                    TENANT_ID,
                    1.0,  // perfect validation score
                    1.0,  // perfect semantic evidence score
                    GenerationPublishPolicy.SimulationValidationStatus.VALID,
                    GenerationPublishPolicy.AssessmentValidationStatus.VALID,
                    GenerationPublishPolicy.AccessibilityStatus.COMPLIANT,
                    true,  // source citation complete
                    false, // no SME review required
                    0.0,   // zero AI risk
                    "mathematics",
                    "12-18"
            );

            // Act
            Promise<GenerationPublishPolicy.PublishDecision> decisionPromise = policy.executeEvaluation(input);
            GenerationPublishPolicy.PublishDecision decision = decisionPromise.getResult();

            // Assert
            assertThat(decision.getDecisionType())
                    .isEqualTo(GenerationPublishPolicy.DecisionType.REVIEW_REQUIRED);
            assertThat(decision.getReason())
                    .contains("Auto-publish feature flag is disabled");
        }

        @Test
        @DisplayName("Feature flag on should evaluate policy gates")
        void featureFlagOnEvaluatesPolicyGates() {
            // Arrange
            GenerationPublishPolicy.FeatureFlagService flagService = new PlatformFeatureFlagService(
                    Map.of("autonomous_content_auto_publish", true)
            );
            GenerationPublishPolicy policy = new GenerationPublishPolicy(flagService);

            GenerationPublishPolicy.PolicyInput input = new GenerationPublishPolicy.PolicyInput(
                    TENANT_ID,
                    0.90,
                    0.90,
                    GenerationPublishPolicy.SimulationValidationStatus.VALID,
                    GenerationPublishPolicy.AssessmentValidationStatus.VALID,
                    GenerationPublishPolicy.AccessibilityStatus.COMPLIANT,
                    true,
                    false,
                    0.10,
                    "mathematics",
                    "12-18"
            );

            // Act
            Promise<GenerationPublishPolicy.PublishDecision> decisionPromise = policy.executeEvaluation(input);
            GenerationPublishPolicy.PublishDecision decision = decisionPromise.getResult();

            // Assert
            assertThat(decision.getDecisionType())
                    .isEqualTo(GenerationPublishPolicy.DecisionType.PUBLISHABLE);
        }
    }

    @Nested
    @DisplayName("Validation Score Gate")
    class ValidationScoreGateTests {

        @Test
        @DisplayName("Validation score below threshold should block publication")
        void lowValidationScoreBlocksPublication() {
            // Arrange
            GenerationPublishPolicy policy = createPolicyWithFlagEnabled();
            GenerationPublishPolicy.PolicyInput input = new GenerationPublishPolicy.PolicyInput(
                    TENANT_ID,
                    0.75,  // below 0.80 threshold
                    0.90,
                    GenerationPublishPolicy.SimulationValidationStatus.VALID,
                    GenerationPublishPolicy.AssessmentValidationStatus.VALID,
                    GenerationPublishPolicy.AccessibilityStatus.COMPLIANT,
                    true,
                    false,
                    0.10,
                    "mathematics",
                    "12-18"
            );

            // Act
            Promise<GenerationPublishPolicy.PublishDecision> decisionPromise = policy.executeEvaluation(input);
            GenerationPublishPolicy.PublishDecision decision = decisionPromise.getResult();

            // Assert
            assertThat(decision.getDecisionType())
                    .isEqualTo(GenerationPublishPolicy.DecisionType.BLOCKED);
            assertThat(decision.getReason())
                    .contains("Validation score")
                    .contains("below threshold");
        }

        @Test
        @DisplayName("Validation score at threshold should pass")
        void validationScoreAtThresholdPasses() {
            // Arrange
            GenerationPublishPolicy policy = createPolicyWithFlagEnabled();
            GenerationPublishPolicy.PolicyInput input = new GenerationPublishPolicy.PolicyInput(
                    TENANT_ID,
                    0.80,  // exactly at threshold
                    0.90,
                    GenerationPublishPolicy.SimulationValidationStatus.VALID,
                    GenerationPublishPolicy.AssessmentValidationStatus.VALID,
                    GenerationPublishPolicy.AccessibilityStatus.COMPLIANT,
                    true,
                    false,
                    0.10,
                    "mathematics",
                    "12-18"
            );

            // Act
            Promise<GenerationPublishPolicy.PublishDecision> decisionPromise = policy.executeEvaluation(input);
            GenerationPublishPolicy.PublishDecision decision = decisionPromise.getResult();

            // Assert
            assertThat(decision.getDecisionType())
                    .isNotEqualTo(GenerationPublishPolicy.DecisionType.BLOCKED);
        }

        @Test
        @DisplayName("Regression test: very low validation score cannot publish")
        void regressionVeryLowScoreCannotPublish() {
            // Arrange
            GenerationPublishPolicy policy = createPolicyWithFlagEnabled();
            GenerationPublishPolicy.PolicyInput input = new GenerationPublishPolicy.PolicyInput(
                    TENANT_ID,
                    0.30,  // very low score
                    0.90,
                    GenerationPublishPolicy.SimulationValidationStatus.VALID,
                    GenerationPublishPolicy.AssessmentValidationStatus.VALID,
                    GenerationPublishPolicy.AccessibilityStatus.COMPLIANT,
                    true,
                    false,
                    0.10,
                    "mathematics",
                    "12-18"
            );

            // Act
            Promise<GenerationPublishPolicy.PublishDecision> decisionPromise = policy.executeEvaluation(input);
            GenerationPublishPolicy.PublishDecision decision = decisionPromise.getResult();

            // Assert
            assertThat(decision.getDecisionType())
                    .isEqualTo(GenerationPublishPolicy.DecisionType.BLOCKED);
            assertThat(decision.isPublishable()).isFalse();
        }
    }

    @Nested
    @DisplayName("Semantic Evidence Score Gate")
    class SemanticEvidenceScoreGateTests {

        @Test
        @DisplayName("Semantic evidence score below threshold requires review")
        void lowSemanticEvidenceScoreRequiresReview() {
            // Arrange
            GenerationPublishPolicy policy = createPolicyWithFlagEnabled();
            GenerationPublishPolicy.PolicyInput input = new GenerationPublishPolicy.PolicyInput(
                    TENANT_ID,
                    0.90,
                    0.80,  // below 0.85 threshold
                    GenerationPublishPolicy.SimulationValidationStatus.VALID,
                    GenerationPublishPolicy.AssessmentValidationStatus.VALID,
                    GenerationPublishPolicy.AccessibilityStatus.COMPLIANT,
                    true,
                    false,
                    0.10,
                    "mathematics",
                    "12-18"
            );

            // Act
            Promise<GenerationPublishPolicy.PublishDecision> decisionPromise = policy.executeEvaluation(input);
            GenerationPublishPolicy.PublishDecision decision = decisionPromise.getResult();

            // Assert
            assertThat(decision.getDecisionType())
                    .isEqualTo(GenerationPublishPolicy.DecisionType.REVIEW_REQUIRED);
            assertThat(decision.getReason())
                    .contains("Semantic evidence score")
                    .contains("below threshold");
        }

        @Test
        @DisplayName("Semantic evidence score at threshold should pass")
        void semanticEvidenceScoreAtThresholdPasses() {
            // Arrange
            GenerationPublishPolicy policy = createPolicyWithFlagEnabled();
            GenerationPublishPolicy.PolicyInput input = new GenerationPublishPolicy.PolicyInput(
                    TENANT_ID,
                    0.90,
                    0.85,  // exactly at threshold
                    GenerationPublishPolicy.SimulationValidationStatus.VALID,
                    GenerationPublishPolicy.AssessmentValidationStatus.VALID,
                    GenerationPublishPolicy.AccessibilityStatus.COMPLIANT,
                    true,
                    false,
                    0.10,
                    "mathematics",
                    "12-18"
            );

            // Act
            Promise<GenerationPublishPolicy.PublishDecision> decisionPromise = policy.executeEvaluation(input);
            GenerationPublishPolicy.PublishDecision decision = decisionPromise.getResult();

            // Assert
            assertThat(decision.requiresReview()).isFalse();
        }
    }

    @Nested
    @DisplayName("Simulation Validation Gate")
    class SimulationValidationGateTests {

        @Test
        @DisplayName("Invalid simulation validation should block publication")
        void invalidSimulationValidationBlocks() {
            // Arrange
            GenerationPublishPolicy policy = createPolicyWithFlagEnabled();
            GenerationPublishPolicy.PolicyInput input = new GenerationPublishPolicy.PolicyInput(
                    TENANT_ID,
                    0.90,
                    0.90,
                    GenerationPublishPolicy.SimulationValidationStatus.INVALID,
                    GenerationPublishPolicy.AssessmentValidationStatus.VALID,
                    GenerationPublishPolicy.AccessibilityStatus.COMPLIANT,
                    true,
                    false,
                    0.10,
                    "mathematics",
                    "12-18"
            );

            // Act
            Promise<GenerationPublishPolicy.PublishDecision> decisionPromise = policy.executeEvaluation(input);
            GenerationPublishPolicy.PublishDecision decision = decisionPromise.getResult();

            // Assert
            assertThat(decision.getDecisionType())
                    .isEqualTo(GenerationPublishPolicy.DecisionType.BLOCKED);
            assertThat(decision.getReason())
                    .contains("Simulation validation failed");
        }

        @Test
        @DisplayName("Required but missing simulation validation requires review")
        void missingRequiredSimulationValidationRequiresReview() {
            // Arrange
            GenerationPublishPolicy policy = createPolicyWithFlagEnabled();
            GenerationPublishPolicy.PolicyInput input = new GenerationPublishPolicy.PolicyInput(
                    TENANT_ID,
                    0.90,
                    0.90,
                    GenerationPublishPolicy.SimulationValidationStatus.REQUIRED_BUT_MISSING,
                    GenerationPublishPolicy.AssessmentValidationStatus.VALID,
                    GenerationPublishPolicy.AccessibilityStatus.COMPLIANT,
                    true,
                    false,
                    0.10,
                    "mathematics",
                    "12-18"
            );

            // Act
            Promise<GenerationPublishPolicy.PublishDecision> decisionPromise = policy.executeEvaluation(input);
            GenerationPublishPolicy.PublishDecision decision = decisionPromise.getResult();

            // Assert
            assertThat(decision.getDecisionType())
                    .isEqualTo(GenerationPublishPolicy.DecisionType.REVIEW_REQUIRED);
        }

        @Test
        @DisplayName("Not applicable simulation validation should pass")
        void notApplicableSimulationValidationPasses() {
            // Arrange
            GenerationPublishPolicy policy = createPolicyWithFlagEnabled();
            GenerationPublishPolicy.PolicyInput input = new GenerationPublishPolicy.PolicyInput(
                    TENANT_ID,
                    0.90,
                    0.90,
                    GenerationPublishPolicy.SimulationValidationStatus.NOT_APPLICABLE,
                    GenerationPublishPolicy.AssessmentValidationStatus.VALID,
                    GenerationPublishPolicy.AccessibilityStatus.COMPLIANT,
                    true,
                    false,
                    0.10,
                    "mathematics",
                    "12-18"
            );

            // Act
            Promise<GenerationPublishPolicy.PublishDecision> decisionPromise = policy.executeEvaluation(input);
            GenerationPublishPolicy.PublishDecision decision = decisionPromise.getResult();

            // Assert
            assertThat(decision.isBlocked()).isFalse();
        }
    }

    @Nested
    @DisplayName("Assessment Validation Gate")
    class AssessmentValidationGateTests {

        @Test
        @DisplayName("Invalid assessment validation should block publication")
        void invalidAssessmentValidationBlocks() {
            // Arrange
            GenerationPublishPolicy policy = createPolicyWithFlagEnabled();
            GenerationPublishPolicy.PolicyInput input = new GenerationPublishPolicy.PolicyInput(
                    TENANT_ID,
                    0.90,
                    0.90,
                    GenerationPublishPolicy.SimulationValidationStatus.VALID,
                    GenerationPublishPolicy.AssessmentValidationStatus.INVALID,
                    GenerationPublishPolicy.AccessibilityStatus.COMPLIANT,
                    true,
                    false,
                    0.10,
                    "mathematics",
                    "12-18"
            );

            // Act
            Promise<GenerationPublishPolicy.PublishDecision> decisionPromise = policy.executeEvaluation(input);
            GenerationPublishPolicy.PublishDecision decision = decisionPromise.getResult();

            // Assert
            assertThat(decision.getDecisionType())
                    .isEqualTo(GenerationPublishPolicy.DecisionType.BLOCKED);
        }

        @Test
        @DisplayName("Required but missing assessment validation requires review")
        void missingRequiredAssessmentValidationRequiresReview() {
            // Arrange
            GenerationPublishPolicy policy = createPolicyWithFlagEnabled();
            GenerationPublishPolicy.PolicyInput input = new GenerationPublishPolicy.PolicyInput(
                    TENANT_ID,
                    0.90,
                    0.90,
                    GenerationPublishPolicy.SimulationValidationStatus.VALID,
                    GenerationPublishPolicy.AssessmentValidationStatus.REQUIRED_BUT_MISSING,
                    GenerationPublishPolicy.AccessibilityStatus.COMPLIANT,
                    true,
                    false,
                    0.10,
                    "mathematics",
                    "12-18"
            );

            // Act
            Promise<GenerationPublishPolicy.PublishDecision> decisionPromise = policy.executeEvaluation(input);
            GenerationPublishPolicy.PublishDecision decision = decisionPromise.getResult();

            // Assert
            assertThat(decision.getDecisionType())
                    .isEqualTo(GenerationPublishPolicy.DecisionType.REVIEW_REQUIRED);
        }
    }

    @Nested
    @DisplayName("Accessibility Gate")
    class AccessibilityGateTests {

        @Test
        @DisplayName("Non-compliant accessibility should block publication")
        void nonCompliantAccessibilityBlocks() {
            // Arrange
            GenerationPublishPolicy policy = createPolicyWithFlagEnabled();
            GenerationPublishPolicy.PolicyInput input = new GenerationPublishPolicy.PolicyInput(
                    TENANT_ID,
                    0.90,
                    0.90,
                    GenerationPublishPolicy.SimulationValidationStatus.VALID,
                    GenerationPublishPolicy.AssessmentValidationStatus.VALID,
                    GenerationPublishPolicy.AccessibilityStatus.NON_COMPLIANT,
                    true,
                    false,
                    0.10,
                    "mathematics",
                    "12-18"
            );

            // Act
            Promise<GenerationPublishPolicy.PublishDecision> decisionPromise = policy.executeEvaluation(input);
            GenerationPublishPolicy.PublishDecision decision = decisionPromise.getResult();

            // Assert
            assertThat(decision.getDecisionType())
                    .isEqualTo(GenerationPublishPolicy.DecisionType.BLOCKED);
        }

        @Test
        @DisplayName("Not evaluated accessibility requires review")
        void notEvaluatedAccessibilityRequiresReview() {
            // Arrange
            GenerationPublishPolicy policy = createPolicyWithFlagEnabled();
            GenerationPublishPolicy.PolicyInput input = new GenerationPublishPolicy.PolicyInput(
                    TENANT_ID,
                    0.90,
                    0.90,
                    GenerationPublishPolicy.SimulationValidationStatus.VALID,
                    GenerationPublishPolicy.AssessmentValidationStatus.VALID,
                    GenerationPublishPolicy.AccessibilityStatus.NOT_EVALUATED,
                    true,
                    false,
                    0.10,
                    "mathematics",
                    "12-18"
            );

            // Act
            Promise<GenerationPublishPolicy.PublishDecision> decisionPromise = policy.executeEvaluation(input);
            GenerationPublishPolicy.PublishDecision decision = decisionPromise.getResult();

            // Assert
            assertThat(decision.getDecisionType())
                    .isEqualTo(GenerationPublishPolicy.DecisionType.REVIEW_REQUIRED);
        }
    }

    @Nested
    @DisplayName("Source Citation Gate")
    class SourceCitationGateTests {

        @Test
        @DisplayName("Incomplete source citations require review")
        void incompleteSourceCitationsRequireReview() {
            // Arrange
            GenerationPublishPolicy policy = createPolicyWithFlagEnabled();
            GenerationPublishPolicy.PolicyInput input = new GenerationPublishPolicy.PolicyInput(
                    TENANT_ID,
                    0.90,
                    0.90,
                    GenerationPublishPolicy.SimulationValidationStatus.VALID,
                    GenerationPublishPolicy.AssessmentValidationStatus.VALID,
                    GenerationPublishPolicy.AccessibilityStatus.COMPLIANT,
                    false,  // incomplete citations
                    false,
                    0.10,
                    "mathematics",
                    "12-18"
            );

            // Act
            Promise<GenerationPublishPolicy.PublishDecision> decisionPromise = policy.executeEvaluation(input);
            GenerationPublishPolicy.PublishDecision decision = decisionPromise.getResult();

            // Assert
            assertThat(decision.getDecisionType())
                    .isEqualTo(GenerationPublishPolicy.DecisionType.REVIEW_REQUIRED);
            assertThat(decision.getReason())
                    .contains("Source citations incomplete");
        }
    }

    @Nested
    @DisplayName("SME Review Gate")
    class SMEReviewGateTests {

        @Test
        @DisplayName("SME review required by policy should require review")
        void smeReviewRequiredRequiresReview() {
            // Arrange
            GenerationPublishPolicy policy = createPolicyWithFlagEnabled();
            GenerationPublishPolicy.PolicyInput input = new GenerationPublishPolicy.PolicyInput(
                    TENANT_ID,
                    0.90,
                    0.90,
                    GenerationPublishPolicy.SimulationValidationStatus.VALID,
                    GenerationPublishPolicy.AssessmentValidationStatus.VALID,
                    GenerationPublishPolicy.AccessibilityStatus.COMPLIANT,
                    true,
                    true,  // SME review required
                    0.10,
                    "mathematics",
                    "12-18"
            );

            // Act
            Promise<GenerationPublishPolicy.PublishDecision> decisionPromise = policy.executeEvaluation(input);
            GenerationPublishPolicy.PublishDecision decision = decisionPromise.getResult();

            // Assert
            assertThat(decision.getDecisionType())
                    .isEqualTo(GenerationPublishPolicy.DecisionType.REVIEW_REQUIRED);
            assertThat(decision.getReason())
                    .contains("SME review required");
        }
    }

    @Nested
    @DisplayName("AI Risk Score Gate")
    class AIRiskScoreGateTests {

        @Test
        @DisplayName("High AI risk score requires review")
        void highAIRiskScoreRequiresReview() {
            // Arrange
            GenerationPublishPolicy policy = createPolicyWithFlagEnabled();
            GenerationPublishPolicy.PolicyInput input = new GenerationPublishPolicy.PolicyInput(
                    TENANT_ID,
                    0.90,
                    0.90,
                    GenerationPublishPolicy.SimulationValidationStatus.VALID,
                    GenerationPublishPolicy.AssessmentValidationStatus.VALID,
                    GenerationPublishPolicy.AccessibilityStatus.COMPLIANT,
                    true,
                    false,
                    0.50,  // above 0.30 threshold
                    "mathematics",
                    "12-18"
            );

            // Act
            Promise<GenerationPublishPolicy.PublishDecision> decisionPromise = policy.executeEvaluation(input);
            GenerationPublishPolicy.PublishDecision decision = decisionPromise.getResult();

            // Assert
            assertThat(decision.getDecisionType())
                    .isEqualTo(GenerationPublishPolicy.DecisionType.REVIEW_REQUIRED);
            assertThat(decision.getReason())
                    .contains("AI risk score")
                    .contains("exceeds threshold");
        }

        @Test
        @DisplayName("AI risk score at threshold should pass")
        void aiRiskScoreAtThresholdPasses() {
            // Arrange
            GenerationPublishPolicy policy = createPolicyWithFlagEnabled();
            GenerationPublishPolicy.PolicyInput input = new GenerationPublishPolicy.PolicyInput(
                    TENANT_ID,
                    0.90,
                    0.90,
                    GenerationPublishPolicy.SimulationValidationStatus.VALID,
                    GenerationPublishPolicy.AssessmentValidationStatus.VALID,
                    GenerationPublishPolicy.AccessibilityStatus.COMPLIANT,
                    true,
                    false,
                    0.30,  // exactly at threshold
                    "mathematics",
                    "12-18"
            );

            // Act
            Promise<GenerationPublishPolicy.PublishDecision> decisionPromise = policy.executeEvaluation(input);
            GenerationPublishPolicy.PublishDecision decision = decisionPromise.getResult();

            // Assert
            assertThat(decision.requiresReview()).isFalse();
        }
    }

    @Nested
    @DisplayName("Content Domain Gate")
    class ContentDomainGateTests {

        @Test
        @DisplayName("High-risk domains require review")
        void highRiskDomainsRequireReview() {
            // Arrange
            GenerationPublishPolicy policy = createPolicyWithFlagEnabled();

            Set<String> highRiskDomains = Set.of("medicine", "clinical", "health", "finance", "legal", "safety-critical");

            for (String domain : highRiskDomains) {
                GenerationPublishPolicy.PolicyInput input = new GenerationPublishPolicy.PolicyInput(
                        TENANT_ID,
                        0.90,
                        0.90,
                        GenerationPublishPolicy.SimulationValidationStatus.VALID,
                        GenerationPublishPolicy.AssessmentValidationStatus.VALID,
                        GenerationPublishPolicy.AccessibilityStatus.COMPLIANT,
                        true,
                        false,
                        0.10,
                        domain,
                        "12-18"
                );

                // Act
                Promise<GenerationPublishPolicy.PublishDecision> decisionPromise = policy.executeEvaluation(input);
                GenerationPublishPolicy.PublishDecision decision = decisionPromise.getResult();

                // Assert
                assertThat(decision.getDecisionType())
                        .as("Domain %s should require review", domain)
                        .isEqualTo(GenerationPublishPolicy.DecisionType.REVIEW_REQUIRED);
                assertThat(decision.getReason())
                        .contains("High-risk content domain");
            }
        }

        @Test
        @DisplayName("Low-risk domains can publish")
        void lowRiskDomainsCanPublish() {
            // Arrange
            GenerationPublishPolicy policy = createPolicyWithFlagEnabled();
            GenerationPublishPolicy.PolicyInput input = new GenerationPublishPolicy.PolicyInput(
                    TENANT_ID,
                    0.90,
                    0.90,
                    GenerationPublishPolicy.SimulationValidationStatus.VALID,
                    GenerationPublishPolicy.AssessmentValidationStatus.VALID,
                    GenerationPublishPolicy.AccessibilityStatus.COMPLIANT,
                    true,
                    false,
                    0.10,
                    "mathematics",
                    "12-18"
            );

            // Act
            Promise<GenerationPublishPolicy.PublishDecision> decisionPromise = policy.executeEvaluation(input);
            GenerationPublishPolicy.PublishDecision decision = decisionPromise.getResult();

            // Assert
            assertThat(decision.getDecisionType())
                    .isEqualTo(GenerationPublishPolicy.DecisionType.PUBLISHABLE);
        }
    }

    @Nested
    @DisplayName("Age Band Gate")
    class AgeBandGateTests {

        @Test
        @DisplayName("Restricted age bands require review")
        void restrictedAgeBandsRequireReview() {
            // Arrange
            GenerationPublishPolicy policy = createPolicyWithFlagEnabled();

            Set<String> restrictedAgeBands = Set.of("0-5", "6-11", "under-13");

            for (String ageBand : restrictedAgeBands) {
                GenerationPublishPolicy.PolicyInput input = new GenerationPublishPolicy.PolicyInput(
                        TENANT_ID,
                        0.90,
                        0.90,
                        GenerationPublishPolicy.SimulationValidationStatus.VALID,
                        GenerationPublishPolicy.AssessmentValidationStatus.VALID,
                        GenerationPublishPolicy.AccessibilityStatus.COMPLIANT,
                        true,
                        false,
                        0.10,
                        "mathematics",
                        ageBand
                );

                // Act
                Promise<GenerationPublishPolicy.PublishDecision> decisionPromise = policy.executeEvaluation(input);
                GenerationPublishPolicy.PublishDecision decision = decisionPromise.getResult();

                // Assert
                assertThat(decision.getDecisionType())
                        .as("Age band %s should require review", ageBand)
                        .isEqualTo(GenerationPublishPolicy.DecisionType.REVIEW_REQUIRED);
                assertThat(decision.getReason())
                        .contains("Restricted age band");
            }
        }

        @Test
        @DisplayName("Non-restricted age bands can publish")
        void nonRestrictedAgeBandsCanPublish() {
            // Arrange
            GenerationPublishPolicy policy = createPolicyWithFlagEnabled();
            GenerationPublishPolicy.PolicyInput input = new GenerationPublishPolicy.PolicyInput(
                    TENANT_ID,
                    0.90,
                    0.90,
                    GenerationPublishPolicy.SimulationValidationStatus.VALID,
                    GenerationPublishPolicy.AssessmentValidationStatus.VALID,
                    GenerationPublishPolicy.AccessibilityStatus.COMPLIANT,
                    true,
                    false,
                    0.10,
                    "mathematics",
                    "12-18"
            );

            // Act
            Promise<GenerationPublishPolicy.PublishDecision> decisionPromise = policy.executeEvaluation(input);
            GenerationPublishPolicy.PublishDecision decision = decisionPromise.getResult();

            // Assert
            assertThat(decision.getDecisionType())
                    .isEqualTo(GenerationPublishPolicy.DecisionType.PUBLISHABLE);
        }
    }

    @Nested
    @DisplayName("Input Validation")
    class InputValidationTests {

        @Test
        @DisplayName("Null input should throw NullPointerException")
        void nullInputThrowsException() {
            // Arrange
            GenerationPublishPolicy policy = createPolicyWithFlagEnabled();

            // Act & Assert
            org.junit.jupiter.api.Assertions.assertThrows(
                    NullPointerException.class,
                    () -> policy.executeEvaluation(null).getResult()
            );
        }

        @Test
        @DisplayName("Validation score out of range should throw IllegalArgumentException")
        void validationScoreOutOfRangeThrowsException() {
            // Act & Assert
            org.junit.jupiter.api.Assertions.assertThrows(
                    IllegalArgumentException.class,
                    () -> new GenerationPublishPolicy.PolicyInput(
                            TENANT_ID,
                            1.5,  // out of range
                            0.90,
                            GenerationPublishPolicy.SimulationValidationStatus.VALID,
                            GenerationPublishPolicy.AssessmentValidationStatus.VALID,
                            GenerationPublishPolicy.AccessibilityStatus.COMPLIANT,
                            true,
                            false,
                            0.10,
                            "mathematics",
                            "12-18"
                    )
            );
        }

        @Test
        @DisplayName("Semantic evidence score out of range should throw IllegalArgumentException")
        void semanticEvidenceScoreOutOfRangeThrowsException() {
            // Act & Assert
            org.junit.jupiter.api.Assertions.assertThrows(
                    IllegalArgumentException.class,
                    () -> new GenerationPublishPolicy.PolicyInput(
                            TENANT_ID,
                            0.90,
                            -0.1,  // out of range
                            GenerationPublishPolicy.SimulationValidationStatus.VALID,
                            GenerationPublishPolicy.AssessmentValidationStatus.VALID,
                            GenerationPublishPolicy.AccessibilityStatus.COMPLIANT,
                            true,
                            false,
                            0.10,
                            "mathematics",
                            "12-18"
                    )
            );
        }

        @Test
        @DisplayName("AI risk score out of range should throw IllegalArgumentException")
        void aiRiskScoreOutOfRangeThrowsException() {
            // Act & Assert
            org.junit.jupiter.api.Assertions.assertThrows(
                    IllegalArgumentException.class,
                    () -> new GenerationPublishPolicy.PolicyInput(
                            TENANT_ID,
                            0.90,
                            0.90,
                            GenerationPublishPolicy.SimulationValidationStatus.VALID,
                            GenerationPublishPolicy.AssessmentValidationStatus.VALID,
                            GenerationPublishPolicy.AccessibilityStatus.COMPLIANT,
                            true,
                            false,
                            2.0,  // out of range
                            "mathematics",
                            "12-18"
                    )
            );
        }
    }

    @Nested
    @DisplayName("Publish Decision Metadata")
    class PublishDecisionMetadataTests {

        @Test
        @DisplayName("Publish decision should include evaluation timestamp")
        void publishDecisionIncludesTimestamp() {
            // Arrange
            GenerationPublishPolicy policy = createPolicyWithFlagEnabled();
            GenerationPublishPolicy.PolicyInput input = new GenerationPublishPolicy.PolicyInput(
                    TENANT_ID,
                    0.90,
                    0.90,
                    GenerationPublishPolicy.SimulationValidationStatus.VALID,
                    GenerationPublishPolicy.AssessmentValidationStatus.VALID,
                    GenerationPublishPolicy.AccessibilityStatus.COMPLIANT,
                    true,
                    false,
                    0.10,
                    "mathematics",
                    "12-18"
            );

            // Act
            Promise<GenerationPublishPolicy.PublishDecision> decisionPromise = policy.executeEvaluation(input);
            GenerationPublishPolicy.PublishDecision decision = decisionPromise.getResult();

            // Assert
            assertThat(decision.getEvaluatedAt()).isNotNull();
            assertThat(decision.getEvaluatedAt()).isBefore(java.time.Instant.now().plusSeconds(1));
        }

        @Test
        @DisplayName("Publish decision should include metadata")
        void publishDecisionIncludesMetadata() {
            // Arrange
            GenerationPublishPolicy policy = createPolicyWithFlagEnabled();
            GenerationPublishPolicy.PolicyInput input = new GenerationPublishPolicy.PolicyInput(
                    TENANT_ID,
                    0.90,
                    0.90,
                    GenerationPublishPolicy.SimulationValidationStatus.VALID,
                    GenerationPublishPolicy.AssessmentValidationStatus.VALID,
                    GenerationPublishPolicy.AccessibilityStatus.COMPLIANT,
                    true,
                    false,
                    0.10,
                    "mathematics",
                    "12-18"
            );

            // Act
            Promise<GenerationPublishPolicy.PublishDecision> decisionPromise = policy.executeEvaluation(input);
            GenerationPublishPolicy.PublishDecision decision = decisionPromise.getResult();

            // Assert
            assertThat(decision.getMetadata()).isNotNull();
            assertThat(decision.getMetadata().getEvaluatorVersion()).isNotNull();
        }

        @Test
        @DisplayName("Reviewer override can be set on metadata")
        void reviewerOverrideCanBeSet() {
            // Arrange
            GenerationPublishPolicy.PublishDecision decision = GenerationPublishPolicy.PublishDecision.publishable(
                    new GenerationPublishPolicy.PolicyInput(
                            TENANT_ID,
                            0.90,
                            0.90,
                            GenerationPublishPolicy.SimulationValidationStatus.VALID,
                            GenerationPublishPolicy.AssessmentValidationStatus.VALID,
                            GenerationPublishPolicy.AccessibilityStatus.COMPLIANT,
                            true,
                            false,
                            0.10,
                            "mathematics",
                            "12-18"
                    )
            );

            // Act
            decision.getMetadata().setReviewerOverrideUserId("reviewer-456");
            decision.getMetadata().setReviewerOverrideReason("Manual approval after review");
            decision.getMetadata().setReviewerOverrideAt(java.time.Instant.now());

            // Assert
            assertThat(decision.getMetadata().hasReviewerOverride()).isTrue();
            assertThat(decision.getMetadata().getReviewerOverrideUserId()).isEqualTo("reviewer-456");
        }
    }

    /**
     * Helper method to create policy with auto-publish flag enabled.
     */
    private GenerationPublishPolicy createPolicyWithFlagEnabled() {
        GenerationPublishPolicy.FeatureFlagService flagService = new PlatformFeatureFlagService(
                Map.of("autonomous_content_auto_publish", true)
        );
        return new GenerationPublishPolicy(flagService);
    }
}
