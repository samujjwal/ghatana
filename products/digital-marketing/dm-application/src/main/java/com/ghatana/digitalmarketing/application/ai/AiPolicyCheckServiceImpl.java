package com.ghatana.digitalmarketing.application.ai;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.ai.AiProvenance;
import io.activej.promise.Promise;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * P1-030: Implementation of AI policy check service.
 *
 * @doc.type class
 * @doc.purpose AI policy check service implementation (P1-030)
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public final class AiPolicyCheckServiceImpl implements AiPolicyCheckService {

    private static final double MINIMUM_CONFIDENCE_THRESHOLD = 0.7;
    private static final double ELEVATED_APPROVAL_THRESHOLD = 0.5;

    public AiPolicyCheckServiceImpl() {
        // No dependencies for policy checks - uses configuration
    }

    @Override
    public Promise<PolicyCheckResult> checkPolicy(DmOperationContext ctx, AiProvenance provenance, String outputContent, AiOutputType outputType) {
        Objects.requireNonNull(provenance, "provenance must not be null");
        Objects.requireNonNull(outputContent, "outputContent must not be null");

        List<String> warnings = new ArrayList<>();
        boolean passed = true;
        String failureReason = null;
        boolean requiresHigherApproval = false;

        // Check 1: Provenance must have model and prompt versions
        if (provenance.getModelVersion() == null || provenance.getModelVersion().isBlank()) {
            passed = false;
            failureReason = "Model version is missing from provenance";
        }
        if (provenance.getPromptVersion() == null || provenance.getPromptVersion().isBlank()) {
            passed = false;
            failureReason = failureReason != null ? failureReason + "; prompt version missing" : "Prompt version is missing from provenance";
        }

        // Check 2: Confidence score validation
        double confidenceScore = provenance.getConfidenceScore().orElse(0.0);
        if (confidenceScore < ELEVATED_APPROVAL_THRESHOLD) {
            warnings.add("Low confidence score (" + confidenceScore + ") - requires elevated approval");
            requiresHigherApproval = true;
        } else if (confidenceScore < MINIMUM_CONFIDENCE_THRESHOLD) {
            warnings.add("Confidence score below threshold (" + confidenceScore + ") - requires standard approval");
        }

        // Check 3: Output type-specific checks
        if (outputType == AiOutputType.STRATEGY && outputContent.length() < 100) {
            warnings.add("Strategy output is too short - may be incomplete");
        }

        return Promise.of(new PolicyCheckResult(
            passed,
            failureReason,
            warnings,
            confidenceScore,
            requiresHigherApproval
        ));
    }

    @Override
    public Promise<EvidenceValidationResult> validateEvidence(DmOperationContext ctx, List<String> evidenceLinks, AiOutputType outputType) {
        Objects.requireNonNull(evidenceLinks, "evidenceLinks must not be null");

        int evidenceCount = evidenceLinks.size();
        List<String> missingEvidenceTypes = new ArrayList<>();
        boolean sufficient = true;
        String validationMessage = "";

        // Evidence requirements by output type
        switch (outputType) {
            case STRATEGY:
                if (evidenceCount < 2) {
                    sufficient = false;
                    missingEvidenceTypes.add("market research");
                    missingEvidenceTypes.add("competitor analysis");
                    validationMessage = "Strategy requires at least 2 evidence sources (market research, competitor analysis)";
                }
                break;
            case BUDGET:
                if (evidenceCount < 1) {
                    sufficient = false;
                    missingEvidenceTypes.add("financial assumptions");
                    validationMessage = "Budget requires at least 1 evidence source (financial assumptions)";
                }
                break;
            case CONTENT:
                if (evidenceCount < 1) {
                    sufficient = false;
                    missingEvidenceTypes.add("brand guidelines");
                    validationMessage = "Content requires at least 1 evidence source (brand guidelines)";
                }
                break;
            default:
                validationMessage = "No evidence requirements for this output type";
        }

        return Promise.of(new EvidenceValidationResult(
            sufficient,
            evidenceCount,
            missingEvidenceTypes,
            validationMessage
        ));
    }

    @Override
    public Promise<UnsafeClaimCheckResult> checkUnsafeClaims(DmOperationContext ctx, String outputContent) {
        Objects.requireNonNull(outputContent, "outputContent must not be null");

        List<String> unsafeClaims = new ArrayList<>();
        boolean blocked = false;
        String blockReason = null;

        // Check for potentially unsafe patterns
        String lowerContent = outputContent.toLowerCase();
        
        if (lowerContent.contains("guaranteed") || lowerContent.contains("100% success")) {
            unsafeClaims.add("Unrealistic guarantee claim");
        }
        if (lowerContent.contains("illegal") || lowerContent.contains("against the law")) {
            unsafeClaims.add("Potential illegal activity");
            blocked = true;
            blockReason = "AI output suggests illegal activity";
        }
        if (lowerContent.contains("harmful") || lowerContent.contains("dangerous")) {
            unsafeClaims.add("Potentially harmful content");
            blocked = true;
            blockReason = "AI output suggests harmful content";
        }

        return Promise.of(new UnsafeClaimCheckResult(
            !unsafeClaims.isEmpty(),
            unsafeClaims,
            blocked,
            blockReason
        ));
    }

    @Override
    public ApprovalRequirement determineApprovalRequirement(
        PolicyCheckResult policyCheckResult,
        EvidenceValidationResult evidenceValidationResult,
        UnsafeClaimCheckResult unsafeClaimCheckResult
    ) {
        // If blocked by unsafe claims, cannot be approved
        if (unsafeClaimCheckResult.blocked()) {
            return ApprovalRequirement.BLOCKED;
        }

        // If policy check failed, cannot be approved
        if (!policyCheckResult.passed()) {
            return ApprovalRequirement.BLOCKED;
        }

        // If evidence insufficient, cannot be approved
        if (!evidenceValidationResult.sufficient()) {
            return ApprovalRequirement.BLOCKED;
        }

        // If has unsafe claims but not blocked, requires elevated approval
        if (unsafeClaimCheckResult.hasUnsafeClaims()) {
            return ApprovalRequirement.ELEVATED_APPROVAL;
        }

        // If policy check requires higher approval, elevate
        if (policyCheckResult.requiresHigherApproval()) {
            return ApprovalRequirement.ELEVATED_APPROVAL;
        }

        // If confidence is high and evidence is sufficient, can auto-approve
        if (policyCheckResult.confidenceScore() >= MINIMUM_CONFIDENCE_THRESHOLD) {
            return ApprovalRequirement.AUTO_APPROVE;
        }

        // Otherwise, standard approval
        return ApprovalRequirement.STANDARD_APPROVAL;
    }
}
