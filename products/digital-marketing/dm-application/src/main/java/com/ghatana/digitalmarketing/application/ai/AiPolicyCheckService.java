package com.ghatana.digitalmarketing.application.ai;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.ai.AiProvenance;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;

/**
 * P1-030: AI policy check service for AI output governance.
 *
 * <p>Enforces policy checks on AI-generated content before approval/execution:</p>
 * <ul>
 *   <li>Provenance validation: model/prompt version must be present</li>
 *   <li>Confidence threshold: low-confidence outputs require higher approval</li>
 *   <li>Evidence validation: AI outputs must have supporting evidence</li>
 *   <li>Unsafe claim detection: blocks unsafe or hallucinated claims</li>
 *   <li>Policy compliance: ensures AI outputs comply with brand/governance policies</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose AI policy check service for governance (P1-030)
 * @doc.layer product
 * @doc.pattern ApplicationService, Policy
 */
public interface AiPolicyCheckService {

    /**
     * Checks if an AI output with provenance meets policy requirements.
     *
     * @param ctx the operation context
     * @param provenance the AI provenance record
     * @param outputContent the AI-generated content
     * @param outputType the type of output (strategy, budget, content, recommendation)
     * @return policy check result
     */
    Promise<PolicyCheckResult> checkPolicy(DmOperationContext ctx, AiProvenance provenance, String outputContent, AiOutputType outputType);

    /**
     * Checks if evidence is sufficient for the AI output.
     *
     * @param ctx the operation context
     * @param evidenceLinks list of evidence URLs or references
     * @param outputType the type of output
     * @return evidence validation result
     */
    Promise<EvidenceValidationResult> validateEvidence(DmOperationContext ctx, List<String> evidenceLinks, AiOutputType outputType);

    /**
     * Checks for unsafe or hallucinated claims in AI output.
     *
     * @param ctx the operation context
     * @param outputContent the AI-generated content
     * @return unsafe claim check result
     */
    Promise<UnsafeClaimCheckResult> checkUnsafeClaims(DmOperationContext ctx, String outputContent);

    /**
     * Determines the approval requirement based on policy check results.
     *
     * @param policyCheckResult the policy check result
     * @param evidenceValidationResult the evidence validation result
     * @param unsafeClaimCheckResult the unsafe claim check result
     * @return approval requirement
     */
    ApprovalRequirement determineApprovalRequirement(
        PolicyCheckResult policyCheckResult,
        EvidenceValidationResult evidenceValidationResult,
        UnsafeClaimCheckResult unsafeClaimCheckResult
    );

    /**
     * Policy check result.
     */
    record PolicyCheckResult(
        boolean passed,
        String failureReason,
        List<String> warnings,
        double confidenceScore,
        boolean requiresHigherApproval
    ) {}

    /**
     * Evidence validation result.
     */
    record EvidenceValidationResult(
        boolean sufficient,
        int evidenceCount,
        List<String> missingEvidenceTypes,
        String validationMessage
    ) {}

    /**
     * Unsafe claim check result.
     */
    record UnsafeClaimCheckResult(
        boolean hasUnsafeClaims,
        List<String> unsafeClaims,
        boolean blocked,
        String blockReason
    ) {}

    /**
     * Approval requirement.
     */
    enum ApprovalRequirement {
        AUTO_APPROVE,        // Meets all criteria, can be auto-approved
        STANDARD_APPROVAL,   // Requires standard approval workflow
        ELEVATED_APPROVAL,   // Requires elevated approval (higher role)
        BLOCKED              // Cannot be approved, must be regenerated
    }

    /**
     * AI output type.
     */
    enum AiOutputType {
        STRATEGY,
        BUDGET,
        CONTENT,
        RECOMMENDATION,
        ANALYTICS
    }
}
