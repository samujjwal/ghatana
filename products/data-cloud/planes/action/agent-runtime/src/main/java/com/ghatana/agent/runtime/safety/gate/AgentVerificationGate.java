/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.safety.gate;

import com.ghatana.agent.approval.VerificationProof;

import java.util.Map;
import java.util.Objects;

/**
 * Gate that checks if verification is required and valid.
 *
 * <p>This gate rejects dispatch if verification is required but the proof is missing or invalid.
 *
 * @doc.type class
 * @doc.purpose Validates verification proof before dispatch
 * @doc.layer product
 * @doc.pattern Gate
 */
public final class AgentVerificationGate implements AgentDispatchGate {

    private final VerificationChecker verificationChecker;

    public AgentVerificationGate(VerificationChecker verificationChecker) {
        if (verificationChecker == null) {
            throw new IllegalArgumentException("verificationChecker must not be null");
        }
        this.verificationChecker = verificationChecker;
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

        if (!verificationChecker.isVerificationRequired(context.agentId(), tenantId)) {
            return GateResult.success();
        }

        VerificationProof verificationProof = (VerificationProof) context.metadata().get("verificationProof");
        if (verificationProof == null) {
            return GateResult.failure(
                "Verification is required for agent [" + context.agentId() + "] but verification proof is absent");
        }

        if (!verificationChecker.isVerificationValid(context.agentId(), tenantId, verificationProof)) {
            return GateResult.failure(
                "verification proof [" + verificationProof.proofId() + "] is invalid for agent [" + context.agentId() + "]");
        }

        return GateResult.success();
    }

    /**
     * Interface for checking verification requirements and validity.
     */
    public interface VerificationChecker {
        /**
         * Checks if verification is required for the agent.
         *
         * @param agentId the agent ID
         * @param tenantId the tenant ID
         * @return true if verification is required
         */
        boolean isVerificationRequired(String agentId, String tenantId);

        /**
         * Checks if the verification proof is valid.
         *
         * @param agentId the agent ID
         * @param tenantId the tenant ID
         * @param verificationProof the verification proof
         * @return true if the verification proof is valid
         */
        boolean isVerificationValid(String agentId, String tenantId, VerificationProof verificationProof);
    }
}
