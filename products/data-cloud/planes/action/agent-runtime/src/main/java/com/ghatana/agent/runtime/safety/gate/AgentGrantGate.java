/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.safety.gate;

import java.util.Objects;

/**
 * Gate that validates execution grants before agent dispatch.
 *
 * <p>This gate verifies that the execution grant is valid, not expired,
 * and has the required permissions for the requested operation.
 *
 * @doc.type class
 * @doc.purpose Validates execution grants before agent dispatch
 * @doc.layer product
 * @doc.pattern Gate
 */
public final class AgentGrantGate implements AgentDispatchGate {

    private final GrantValidator grantValidator;

    public AgentGrantGate(GrantValidator grantValidator) {
        this.grantValidator = Objects.requireNonNull(grantValidator, "grantValidator must not be null");
    }

    @Override
    public GateResult evaluate(DispatchContext context) {
        Objects.requireNonNull(context, "context must not be null");

        String grant = context.executionGrant();
        if (grant == null || grant.isBlank()) {
            return GateResult.failure("Execution grant is missing or blank");
        }

        boolean isValid = grantValidator.isValidGrant(grant);
        if (!isValid) {
            return GateResult.failure("Execution grant is invalid or expired: " + grant);
        }

        boolean hasPermission = grantValidator.hasRequiredPermission(grant, context.agentId());
        if (!hasPermission) {
            return GateResult.failure(
                "Execution grant does not have permission for agent: " + context.agentId());
        }

        return GateResult.success();
    }

    /**
     * Interface for validating execution grants.
     *
     * <p>This is a placeholder for the actual grant validation logic.
     * A production implementation would query the grant validation service.
     */
    public interface GrantValidator {
        /**
         * Checks if a grant is valid and not expired.
         *
         * @param grant the execution grant
         * @return true if valid
         */
        boolean isValidGrant(String grant);

        /**
         * Checks if a grant has permission for the specified agent.
         *
         * @param grant the execution grant
         * @param agentId the agent ID
         * @return true if permission granted
         */
        boolean hasRequiredPermission(String grant, String agentId);
    }
}
