/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.memory.governance;

import com.ghatana.agent.framework.memory.MemoryNamespace;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * API for governing memory namespaces: enforcing retention policies, controlling
 * access to namespaces, and auditing governance decisions.
 *
 * <h3>Capabilities</h3>
 * <ul>
 *   <li>Register and update retention policies per namespace</li>
 *   <li>Evaluate whether a memory access is permitted for a given principal</li>
 *   <li>Expire and evict memory entries past their retention window</li>
 *   <li>Audit governance policy enforcement history</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Memory governance: retention policies and namespace access control
 * @doc.layer product
 * @doc.pattern Service Interface
 */
public interface MemoryGovernanceService {

    /**
     * Applies the retention policy for all namespaces of the given agent.
     *
     * <p>Triggers eviction of memory entries whose age exceeds the namespace's
     * {@code retentionDays} setting. Returns a summary of entries evicted.
     *
     * @param agentId  the agent whose namespaces should be trimmed
     * @param tenantId the tenant scope
     * @return a retention enforcement result
     */
    Promise<RetentionEnforcementResult> enforceRetention(String agentId, String tenantId);

    /**
     * Evaluates whether a principal is permitted to read from a specific namespace.
     *
     * @param namespaceId the namespace ID
     * @param principalId the requesting principal (user, agent, service)
     * @param tenantId    the tenant scope
     * @return an access decision
     */
    Promise<AccessDecision> evaluateAccess(String namespaceId, String principalId, String tenantId);

    /**
     * Registers or updates the retention policy for a namespace.
     *
     * @param namespaceId   the namespace to configure
     * @param retentionDays number of days to retain entries (null = indefinite)
     * @param tenantId      the tenant scope
     * @return the updated namespace
     */
    Promise<MemoryNamespace> setRetentionPolicy(String namespaceId,
                                                 @Nullable Integer retentionDays,
                                                 String tenantId);

    /**
     * Returns the governance audit log for a namespace.
     *
     * @param namespaceId the namespace ID
     * @param tenantId    the tenant scope
     * @param since       include only events after this timestamp
     * @return list of governance events (may be empty)
     */
    Promise<List<GovernanceEvent>> auditLog(String namespaceId, String tenantId, Instant since);

    // ─── Value types ──────────────────────────────────────────────────────────

    /**
     * Result of a retention enforcement run.
     *
     * @param agentId         agent whose namespaces were processed
     * @param namespacesChecked count of namespaces checked
     * @param entriesEvicted  count of memory entries evicted
     * @param enforcedAt      timestamp of enforcement
     */
    record RetentionEnforcementResult(
            @NotNull String agentId,
            int namespacesChecked,
            long entriesEvicted,
            @NotNull Instant enforcedAt
    ) {}

    /**
     * Result of an access evaluation.
     *
     * @param permitted   whether the access is permitted
     * @param reason      human-readable explanation of the decision
     * @param principalId the requesting principal
     * @param namespaceId the namespace that was evaluated
     * @param evaluatedAt when the decision was made
     */
    record AccessDecision(
            boolean permitted,
            @NotNull String reason,
            @NotNull String principalId,
            @NotNull String namespaceId,
            @NotNull Instant evaluatedAt
    ) {
        /** Convenience factory for a permitted decision. */
        public static AccessDecision permit(String principalId, String namespaceId) {
            return new AccessDecision(true, "Access permitted", principalId, namespaceId, Instant.now());
        }

        /** Convenience factory for a denied decision with an explicit reason. */
        public static AccessDecision deny(String principalId, String namespaceId, String reason) {
            return new AccessDecision(false, reason, principalId, namespaceId, Instant.now());
        }
    }

    /**
     * A governance event recorded during policy enforcement or access control.
     *
     * @param eventId     unique event ID
     * @param namespaceId namespace the event relates to
     * @param eventType   event classification (e.g., {@code "EVICTION"}, {@code "ACCESS_DENIED"})
     * @param detail      human-readable detail
     * @param occurredAt  when the event was recorded
     */
    record GovernanceEvent(
            @NotNull String eventId,
            @NotNull String namespaceId,
            @NotNull String eventType,
            @NotNull String detail,
            @NotNull Instant occurredAt
    ) {}
}
