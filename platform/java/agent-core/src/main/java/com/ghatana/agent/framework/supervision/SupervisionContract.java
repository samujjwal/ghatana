/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.supervision;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * Immutable contract describing the supervision relationship between a
 * supervisor agent and its subordinates.
 *
 * @param supervisorAgentId  ID of the supervising agent
 * @param subordinateAgentIds IDs of the agents being supervised (immutable)
 * @param tenantId           tenant scope for this contract
 * @param strategy           the failure-handling strategy
 * @param maxRestarts        maximum restart count before escalation (-1 = unlimited)
 *
 * @doc.type class
 * @doc.purpose Immutable supervision contract defining agent failure-handling rules
 * @doc.layer platform
 * @doc.pattern Record
 */
public record SupervisionContract(
        @NotNull String supervisorAgentId,
        @NotNull List<String> subordinateAgentIds,
        @NotNull String tenantId,
        @NotNull SupervisionStrategy strategy,
        int maxRestarts
) {
    /** Compact constructor — validates fields and makes collections immutable. */
    public SupervisionContract {
        if (supervisorAgentId == null || supervisorAgentId.isBlank()) {
            throw new IllegalArgumentException("supervisorAgentId must not be blank");
        }
        Objects.requireNonNull(subordinateAgentIds, "subordinateAgentIds");
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        Objects.requireNonNull(strategy, "strategy");
        if (maxRestarts < -1) {
            throw new IllegalArgumentException("maxRestarts must be >= -1; use -1 for unlimited");
        }
        subordinateAgentIds = List.copyOf(subordinateAgentIds);
    }

    /**
     * Returns {@code true} if unlimited restarts are allowed for this contract.
     */
    public boolean isUnlimitedRestarts() {
        return maxRestarts == -1;
    }

    /**
     * Factory: supervision contract with unlimited restarts.
     *
     * @param supervisorId    supervisor agent ID
     * @param subordinateIds  list of subordinate agent IDs
     * @param tenantId        tenant scope
     * @param strategy        failure-handling strategy
     */
    public static SupervisionContract of(
            String supervisorId,
            List<String> subordinateIds,
            String tenantId,
            SupervisionStrategy strategy) {
        return new SupervisionContract(supervisorId, subordinateIds, tenantId, strategy, -1);
    }
}
