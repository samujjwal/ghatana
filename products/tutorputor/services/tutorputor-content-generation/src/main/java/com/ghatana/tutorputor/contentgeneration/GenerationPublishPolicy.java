package com.ghatana.tutorputor.contentgeneration;

import io.activej.promise.Promise;
import java.time.Instant;
import java.util.*;

/**
 * Policy engine for determining whether generated content can be auto-published.
 *
 * <p><b>Purpose</b><br>
 * Enforces production-grade safety gates before content can be automatically published.
 * Prevents unsafe or incomplete content from reaching production without human review.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * GenerationPublishPolicy policy = new GenerationPublishPolicy(featureFlagService);
 *
 * GenerationPublishPolicy.PolicyInput input = new GenerationPublishPolicy.PolicyInput(
 *     "tenant-123",
 *     0.85,  // validation score
 *     0.90,  // semantic evidence score
 *     SimulationValidationStatus.VALID,
 *     AssessmentValidationStatus.VALID,
 *     AccessibilityStatus.COMPLIANT,
 *     true,  // source completeness
 *     false, // SME review required
 *     0.15,  // AI risk score
 *     "mathematics",  // content domain
 *     "12-18"  // age band
 * );
 *
 * Promise<GenerationPublishPolicy.PublishDecision> decision = policy.executeEvaluation(input);
 *
 * if (decision.getResult().getDecisionType() == DecisionType.PUBLISHABLE) {
 *     // Auto-publish is safe
 * } else {
 *     // Require human review
 * }
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * Domain service enforcing content safety and quality gates before publication.
 * Serves as the single source of truth for publish eligibility decisions.
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe if feature flag service is thread-safe.
 *
 * @doc.type class
 * @doc.purpose Policy engine for content auto-publish safety gates
 * @doc.layer domain
 * @doc.pattern Service
 */
public final class GenerationPublishPolicy {

    private final FeatureFlagService featureFlagService;

    /**
     * Minimum validation score threshold (0-1).
     * Content below this score cannot be auto-published.
     */
    private static final double MIN_VALIDATION_SCORE = 0.80;

    /**
     * Minimum semantic evidence score threshold (0-1).
     * Content below this score lacks sufficient grounding.
     */
    private static final double MIN_SEMANTIC_EVIDENCE_SCORE = 0.85;

    /**
     * Maximum AI risk score threshold (0-1).
     * Content above this score requires human review.
     */
    private static final double MAX_AI_RISK_SCORE = 0.30;

    /**
     * Creates GenerationPublishPolicy.
     *
     * @param featureFlagService feature flag service (non-null)
     * @throws NullPointerException if featureFlagService is null
     */
    public GenerationPublishPolicy(FeatureFlagService featureFlagService) {
        this.featureFlagService = Objects.requireNonNull(
                featureFlagService, "featureFlagService cannot be null"
        );
    }

    /**
     * Evaluates whether content can be auto-published.
     *
     * <p>GIVEN: Valid policy input with all validation scores
     * WHEN: executeEvaluation() called
     * THEN: Returns publish decision based on policy gates and feature flag
     *
     * @param input policy evaluation input (non-null)
     * @return Promise of publish decision
     * @throws NullPointerException if input is null
     */
    public Promise<PublishDecision> executeEvaluation(PolicyInput input) {
        Objects.requireNonNull(input, "input cannot be null");

        // Check feature flag first
        boolean autoPublishEnabled = featureFlagService.isEnabled(
                "autonomous_content_auto_publish",
                input.tenantId()
        );

        if (!autoPublishEnabled) {
            return Promise.of(PublishDecision.reviewRequired(
                    "Auto-publish feature flag is disabled",
                    input
            ));
        }

        // Evaluate all policy gates
        List<String> blockedReasons = new ArrayList<>();
        List<String> reviewReasons = new ArrayList<>();

        // Gate 1: Validation score
        if (input.validationScore() < MIN_VALIDATION_SCORE) {
            blockedReasons.add(String.format(
                    "Validation score %.2f below threshold %.2f",
                    input.validationScore(), MIN_VALIDATION_SCORE
            ));
        }

        // Gate 2: Semantic evidence score
        if (input.semanticEvidenceScore() < MIN_SEMANTIC_EVIDENCE_SCORE) {
            reviewReasons.add(String.format(
                    "Semantic evidence score %.2f below threshold %.2f",
                    input.semanticEvidenceScore(), MIN_SEMANTIC_EVIDENCE_SCORE
            ));
        }

        // Gate 3: Simulation validation status
        if (input.simulationValidationStatus() == SimulationValidationStatus.INVALID) {
            blockedReasons.add("Simulation validation failed");
        } else if (input.simulationValidationStatus() == SimulationValidationStatus.REQUIRED_BUT_MISSING) {
            reviewReasons.add("Simulation validation required but missing");
        }

        // Gate 4: Assessment validation status
        if (input.assessmentValidationStatus() == AssessmentValidationStatus.INVALID) {
            blockedReasons.add("Assessment validation failed");
        } else if (input.assessmentValidationStatus() == AssessmentValidationStatus.REQUIRED_BUT_MISSING) {
            reviewReasons.add("Assessment validation required but missing");
        }

        // Gate 5: Accessibility status
        if (input.accessibilityStatus() == AccessibilityStatus.NON_COMPLIANT) {
            blockedReasons.add("Accessibility validation failed");
        } else if (input.accessibilityStatus() == AccessibilityStatus.NOT_EVALUATED) {
            reviewReasons.add("Accessibility not evaluated");
        }

        // Gate 6: Source/citation completeness
        if (!input.sourceCitationComplete()) {
            reviewReasons.add("Source citations incomplete");
        }

        // Gate 7: SME/human-review requirement
        if (input.smeReviewRequired()) {
            reviewReasons.add("SME review required by policy");
        }

        // Gate 8: AI risk score
        if (input.aiRiskScore() > MAX_AI_RISK_SCORE) {
            reviewReasons.add(String.format(
                    "AI risk score %.2f exceeds threshold %.2f",
                    input.aiRiskScore(), MAX_AI_RISK_SCORE
            ));
        }

        // Gate 9: High-risk content domains
        if (isHighRiskDomain(input.contentDomain())) {
            reviewReasons.add("High-risk content domain: " + input.contentDomain());
        }

        // Gate 10: Age-band policy
        if (isRestrictedAgeBand(input.ageBand())) {
            reviewReasons.add("Restricted age band: " + input.ageBand());
        }

        // Determine decision
        if (!blockedReasons.isEmpty()) {
            return Promise.of(PublishDecision.blocked(
                    String.join("; ", blockedReasons),
                    input
            ));
        }

        if (!reviewReasons.isEmpty()) {
            return Promise.of(PublishDecision.reviewRequired(
                    String.join("; ", reviewReasons),
                    input
            ));
        }

        // All gates passed - content is publishable
        return Promise.of(PublishDecision.publishable(input));
    }

    /**
     * Checks if content domain is high-risk.
     *
     * @param domain content domain
     * @return true if high-risk, false otherwise
     */
    private boolean isHighRiskDomain(String domain) {
        Set<String> highRiskDomains = Set.of(
                "medicine",
                "clinical",
                "health",
                "finance",
                "legal",
                "safety-critical"
        );
        return highRiskDomains.contains(domain.toLowerCase());
    }

    /**
     * Checks if age band is restricted.
     *
     * @param ageBand age band identifier
     * @return true if restricted, false otherwise
     */
    private boolean isRestrictedAgeBand(String ageBand) {
        // Age bands requiring additional review
        Set<String> restrictedAgeBands = Set.of(
                "0-5",
                "6-11",
                "under-13"
        );
        return restrictedAgeBands.contains(ageBand.toLowerCase());
    }

    /**
     * Policy input for publish evaluation.
     *
     * @param tenantId tenant identifier
     * @param validationScore content validation score (0-1)
     * @param semanticEvidenceScore semantic evidence grounding score (0-1)
     * @param simulationValidationStatus simulation validation status
     * @param assessmentValidationStatus assessment validation status
     * @param accessibilityStatus accessibility compliance status
     * @param sourceCitationComplete whether sources/citations are complete
     * @param smeReviewRequired whether SME review is required by policy
     * @param aiRiskScore AI generation risk score (0-1)
     * @param contentDomain content domain (e.g., mathematics, medicine)
     * @param ageBand target age band (e.g., 12-18, under-13)
     */
    public record PolicyInput(
            String tenantId,
            double validationScore,
            double semanticEvidenceScore,
            SimulationValidationStatus simulationValidationStatus,
            AssessmentValidationStatus assessmentValidationStatus,
            AccessibilityStatus accessibilityStatus,
            boolean sourceCitationComplete,
            boolean smeReviewRequired,
            double aiRiskScore,
            String contentDomain,
            String ageBand
    ) {
        public PolicyInput {
            Objects.requireNonNull(tenantId, "tenantId cannot be null");
            Objects.requireNonNull(simulationValidationStatus, "simulationValidationStatus cannot be null");
            Objects.requireNonNull(assessmentValidationStatus, "assessmentValidationStatus cannot be null");
            Objects.requireNonNull(accessibilityStatus, "accessibilityStatus cannot be null");
            Objects.requireNonNull(contentDomain, "contentDomain cannot be null");
            Objects.requireNonNull(ageBand, "ageBand cannot be null");

            if (validationScore < 0.0 || validationScore > 1.0) {
                throw new IllegalArgumentException("validationScore must be between 0.0 and 1.0");
            }
            if (semanticEvidenceScore < 0.0 || semanticEvidenceScore > 1.0) {
                throw new IllegalArgumentException("semanticEvidenceScore must be between 0.0 and 1.0");
            }
            if (aiRiskScore < 0.0 || aiRiskScore > 1.0) {
                throw new IllegalArgumentException("aiRiskScore must be between 0.0 and 1.0");
            }
        }
    }

    /**
     * Publish decision result.
     */
    public static final class PublishDecision {
        private final DecisionType decisionType;
        private final String reason;
        private final PolicyInput policyInput;
        private final Instant evaluatedAt;
        private final PublishMetadata metadata;

        private PublishDecision(
                DecisionType decisionType,
                String reason,
                PolicyInput policyInput,
                PublishMetadata metadata) {
            this.decisionType = Objects.requireNonNull(decisionType, "decisionType cannot be null");
            this.reason = reason;
            this.policyInput = Objects.requireNonNull(policyInput, "policyInput cannot be null");
            this.evaluatedAt = Instant.now();
            this.metadata = metadata != null ? metadata : new PublishMetadata();
        }

        /**
         * Creates a publishable decision.
         */
        public static PublishDecision publishable(PolicyInput input) {
            return new PublishDecision(
                    DecisionType.PUBLISHABLE,
                    "All policy gates passed",
                    input,
                    new PublishMetadata()
            );
        }

        /**
         * Creates a review-required decision.
         */
        public static PublishDecision reviewRequired(String reason, PolicyInput input) {
            return new PublishDecision(
                    DecisionType.REVIEW_REQUIRED,
                    reason,
                    input,
                    new PublishMetadata()
            );
        }

        /**
         * Creates a blocked decision.
         */
        public static PublishDecision blocked(String reason, PolicyInput input) {
            return new PublishDecision(
                    DecisionType.BLOCKED,
                    reason,
                    input,
                    new PublishMetadata()
            );
        }

        /**
         * Creates an invalid decision.
         */
        public static PublishDecision invalid(String reason, PolicyInput input) {
            return new PublishDecision(
                    DecisionType.INVALID,
                    reason,
                    input,
                    new PublishMetadata()
            );
        }

        public DecisionType getDecisionType() {
            return decisionType;
        }

        public String getReason() {
            return reason;
        }

        public PolicyInput getPolicyInput() {
            return policyInput;
        }

        public Instant getEvaluatedAt() {
            return evaluatedAt;
        }

        public PublishMetadata getMetadata() {
            return metadata;
        }

        public boolean isPublishable() {
            return decisionType == DecisionType.PUBLISHABLE;
        }

        public boolean requiresReview() {
            return decisionType == DecisionType.REVIEW_REQUIRED;
        }

        public boolean isBlocked() {
            return decisionType == DecisionType.BLOCKED;
        }

        public boolean isInvalid() {
            return decisionType == DecisionType.INVALID;
        }
    }

    /**
     * Publish decision type.
     */
    public enum DecisionType {
        /**
         * Content can be auto-published.
         */
        PUBLISHABLE,

        /**
         * Content requires human review before publication.
         */
        REVIEW_REQUIRED,

        /**
         * Content is blocked from publication.
         */
        BLOCKED,

        /**
         * Content input is invalid.
         */
        INVALID
    }

    /**
     * Metadata for publish decision.
     */
    public static final class PublishMetadata {
        private String modelVersion;
        private String promptVersion;
        private String evaluatorVersion;
        private String validationVersion;
        private String reviewerOverrideUserId;
        private String reviewerOverrideReason;
        private Instant reviewerOverrideAt;

        public PublishMetadata() {
            this.modelVersion = "unknown";
            this.promptVersion = "unknown";
            this.evaluatorVersion = "1.0.0";
            this.validationVersion = "1.0.0";
        }

        public String getModelVersion() {
            return modelVersion;
        }

        public void setModelVersion(String modelVersion) {
            this.modelVersion = modelVersion;
        }

        public String getPromptVersion() {
            return promptVersion;
        }

        public void setPromptVersion(String promptVersion) {
            this.promptVersion = promptVersion;
        }

        public String getEvaluatorVersion() {
            return evaluatorVersion;
        }

        public void setEvaluatorVersion(String evaluatorVersion) {
            this.evaluatorVersion = evaluatorVersion;
        }

        public String getValidationVersion() {
            return validationVersion;
        }

        public void setValidationVersion(String validationVersion) {
            this.validationVersion = validationVersion;
        }

        public String getReviewerOverrideUserId() {
            return reviewerOverrideUserId;
        }

        public void setReviewerOverrideUserId(String reviewerOverrideUserId) {
            this.reviewerOverrideUserId = reviewerOverrideUserId;
        }

        public String getReviewerOverrideReason() {
            return reviewerOverrideReason;
        }

        public void setReviewerOverrideReason(String reviewerOverrideReason) {
            this.reviewerOverrideReason = reviewerOverrideReason;
        }

        public Instant getReviewerOverrideAt() {
            return reviewerOverrideAt;
        }

        public void setReviewerOverrideAt(Instant reviewerOverrideAt) {
            this.reviewerOverrideAt = reviewerOverrideAt;
        }

        public boolean hasReviewerOverride() {
            return reviewerOverrideUserId != null;
        }
    }

    /**
     * Simulation validation status.
     */
    public enum SimulationValidationStatus {
        /**
         * Simulation is valid and compliant.
         */
        VALID,

        /**
         * Simulation validation failed.
         */
        INVALID,

        /**
         * Simulation validation was required but not performed.
         */
        REQUIRED_BUT_MISSING,

        /**
         * Simulation validation not applicable for this content type.
         */
        NOT_APPLICABLE
    }

    /**
     * Assessment validation status.
     */
    public enum AssessmentValidationStatus {
        /**
         * Assessment is valid and compliant.
         */
        VALID,

        /**
         * Assessment validation failed.
         */
        INVALID,

        /**
         * Assessment validation was required but not performed.
         */
        REQUIRED_BUT_MISSING,

        /**
         * Assessment validation not applicable for this content type.
         */
        NOT_APPLICABLE
    }

    /**
     * Accessibility compliance status.
     */
    public enum AccessibilityStatus {
        /**
         * Content meets accessibility standards.
         */
        COMPLIANT,

        /**
         * Content does not meet accessibility standards.
         */
        NON_COMPLIANT,

        /**
         * Accessibility not yet evaluated.
         */
        NOT_EVALUATED
    }

    /**
     * Simple feature flag service interface for Java service.
     * In production, this should integrate with the platform TypeScript feature flag service.
     */
    public interface FeatureFlagService {
        /**
         * Checks if a feature flag is enabled for a tenant.
         *
         * @param flagKey feature flag key
         * @param tenantId tenant identifier
         * @return true if enabled, false otherwise
         */
        boolean isEnabled(String flagKey, String tenantId);
    }
}
