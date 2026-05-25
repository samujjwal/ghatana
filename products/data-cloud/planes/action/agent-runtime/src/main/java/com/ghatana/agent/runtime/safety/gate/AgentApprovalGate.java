/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.safety.gate;

import com.ghatana.agent.approval.ApprovalProof;

import java.util.Map;
import java.util.Objects;

/**
 * Gate that checks if human approval is required and valid.
 *
 * <p>This gate rejects dispatch if approval is required but the proof is missing or invalid.
 *
 * @doc.type class
 * @doc.purpose Validates human approval before dispatch
 * @doc.layer product
 * @doc.pattern Gate
 */
public final class AgentApprovalGate implements AgentDispatchGate {

    private final ApprovalChecker approvalChecker;

    public AgentApprovalGate(ApprovalChecker approvalChecker) {
        if (approvalChecker == null) {
            throw new IllegalArgumentException("approvalChecker must not be null");
        }
        this.approvalChecker = approvalChecker;
    }

    @Override
    public GateResult evaluate(DispatchContext context) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }

        String tenantId = (String) context.metadata().get("tenantId");
        if (tenantId == null) {
            return GateResult.failure("tenantId is missing from context metadata");
        }

        if (!approvalChecker.isApprovalRequired(context.agentId(), tenantId)) {
            return GateResult.success();
        }

        ApprovalProof approvalProof = (ApprovalProof) context.metadata().get("approvalProof");
        if (approvalProof == null) {
            return GateResult.failure(
                "Approval is required for agent [" + context.agentId() + "] but approval proof is absent");
        }

        if (!approvalChecker.isApprovalValid(context.agentId(), tenantId, approvalProof)) {
            return GateResult.failure(
                "approval proof [" + approvalProof.proofId() + "] is invalid for agent [" + context.agentId() + "]");
        }

        return GateResult.success();
    }

    /**
     * Interface for checking approval requirements and validity.
     */
    public interface ApprovalChecker {
        /**
         * Checks if approval is required for the agent.
         *
         * @param agentId the agent ID
         * @param tenantId the tenant ID
         * @return true if approval is required
         */
        boolean isApprovalRequired(String agentId, String tenantId);

        /**
         * Checks if the approval proof is valid.
         *
         * @param agentId the agent ID
         * @param tenantId the tenant ID
         * @param approvalProof the approval proof
         * @return true if the approval proof is valid
         */
        boolean isApprovalValid(String agentId, String tenantId, ApprovalProof approvalProof);
    }
}
