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
 */
public final class OperatorCatalogAdmissionPolicy {

    public static final String METADATA_APPROVAL_STATUS = "approvalStatus";
    public static final String APPROVED = "approved";
    public static final String METADATA_TOOL_POLICY_DECLARED = "toolPolicyDeclared";

    private OperatorCatalogAdmissionPolicy() {}

    public static void requireApproved(OperatorCatalogEntry entry) {
        Objects.requireNonNull(entry, "entry");
        String approvalStatus = entry.metadata().getOrDefault(METADATA_APPROVAL_STATUS, "");
        if (!APPROVED.equals(approvalStatus.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("Operator is not approved for runtime use: " + entry.operatorId());
        }
        if (entry.sideEffectProfile().filter(AgentSideEffectProfile.SIDE_EFFECTING::equals).isPresent()
            && !"true".equalsIgnoreCase(entry.metadata().getOrDefault(METADATA_TOOL_POLICY_DECLARED, "false"))) {
            throw new IllegalStateException("Side-effecting operators require tool policy: " + entry.operatorId());
        }
    }
}
