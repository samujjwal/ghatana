package com.ghatana.core.operator.catalog;

import com.ghatana.core.operator.agent.AgentSideEffectProfile;

import java.util.Locale;
import java.util.Objects;

/**
 * Runtime admission policy for cataloged operators.
 *
 * @doc.type class
 * @doc.purpose Blocks unapproved or unsafe operator use at catalog admission time
 * @doc.layer core
 * @doc.pattern Policy
 * 
 * <p><b>Hardening (AEP-005)</b><br>
 * - Validates production-specific approval requirements
 * - Enforces commit SHA binding for production truth
 * - Checks environment-specific admission constraints
 * - Requires evidence persistence configuration for production
 */
public final class OperatorCatalogAdmissionPolicy {

    public static final String METADATA_APPROVAL_STATUS = "approvalStatus";
    public static final String APPROVED = "approved";
    public static final String METADATA_TOOL_POLICY_DECLARED = "toolPolicyDeclared";
    public static final String METADATA_COMMIT_SHA = "commitSha";
    public static final String METADATA_EVIDENCE_POLICY = "evidencePolicy";
    public static final String METADATA_ENVIRONMENT = "environment";

    private OperatorCatalogAdmissionPolicy() {}

    public static void requireApproved(OperatorCatalogEntry entry) {
        requireApproved(entry, null, null);
    }

    /**
     * Requires operator approval with production-specific constraints.
     *
     * @param entry the operator catalog entry
     * @param commitSha the commit SHA for production truth binding
     * @param environment the target environment
     */
    public static void requireApproved(OperatorCatalogEntry entry, String commitSha, String environment) {
        Objects.requireNonNull(entry, "entry");
        String approvalStatus = entry.metadata().getOrDefault(METADATA_APPROVAL_STATUS, "");
        if (!APPROVED.equals(approvalStatus.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("Operator is not approved for runtime use: " + entry.operatorId());
        }
        if (entry.sideEffectProfile().filter(AgentSideEffectProfile.SIDE_EFFECTING::equals).isPresent()
            && !"true".equalsIgnoreCase(entry.metadata().getOrDefault(METADATA_TOOL_POLICY_DECLARED, "false"))) {
            throw new IllegalStateException("Side-effecting operators require tool policy: " + entry.operatorId());
        }

        // AEP-005: Production-specific validation
        if ("production".equals(environment)) {
            validateProductionConstraints(entry, commitSha);
        }
    }

    /**
     * Validates production-specific constraints for operator admission.
     *
     * @param entry the operator catalog entry
     * @param commitSha the commit SHA
     */
    private static void validateProductionConstraints(OperatorCatalogEntry entry, String commitSha) {
        // Require commit SHA binding
        if (commitSha == null || commitSha.isEmpty()) {
            throw new IllegalStateException("Production requires commit SHA binding for operator: " + entry.operatorId());
        }
        if (!commitSha.matches("^[a-fA-F0-9]{40}$")) {
            throw new IllegalStateException("Invalid commit SHA format for operator: " + entry.operatorId());
        }

        // Require evidence policy
        String evidencePolicy = entry.metadata().getOrDefault(METADATA_EVIDENCE_POLICY, "");
        if (evidencePolicy.isEmpty()) {
            throw new IllegalStateException("Production requires evidence policy for operator: " + entry.operatorId());
        }

        // Validate operator metadata includes commit SHA
        String operatorCommitSha = entry.metadata().getOrDefault(METADATA_COMMIT_SHA, "");
        if (operatorCommitSha.isEmpty()) {
            throw new IllegalStateException("Operator metadata must include commit SHA in production: " + entry.operatorId());
        }
        if (!operatorCommitSha.equals(commitSha)) {
            throw new IllegalStateException("Operator commit SHA does not match deployment commit SHA: " + entry.operatorId());
        }
    }
}
