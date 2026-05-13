/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Set;

/**
 * Query parameters for searching mastery items.
 *
 * <p>Allows filtering by skill, agent, version context, tenant, state, domain, and freshness.
 *
 * @doc.type record
 * @doc.purpose Query parameters for mastery item search
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record MasteryQuery(
        @Nullable String skillId,
        @Nullable String agentId,
        @Nullable String agentReleaseId,
        @Nullable String tenantId,
        @Nullable String domain,
        @Nullable Set<MasteryState> states,
        @Nullable Boolean includeObsolete,
        @Nullable Boolean includeRetired,
        @Nullable Boolean includeMaintenanceOnly,
        @Nullable Boolean requireFreshness,
        @Nullable Instant currentTime,
        @Nullable Integer limit,
        @Nullable Integer offset
) {
    public MasteryQuery {
        // No null checks - all fields are optional for flexible querying
    }

    /**
     * Creates a query by skill ID.
     *
     * @param skillId skill identifier
     * @return mastery query
     */
    @NotNull
    public static MasteryQuery bySkill(@NotNull String skillId) {
        return new MasteryQuery(skillId, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    /**
     * Creates a query by agent ID.
     *
     * @param agentId agent identifier
     * @return mastery query
     */
    @NotNull
    public static MasteryQuery byAgent(@NotNull String agentId) {
        return new MasteryQuery(null, agentId, null, null, null, null, null, null, null, null, null, null, null);
    }

    /**
     * Creates a query by tenant ID.
     *
     * @param tenantId tenant identifier
     * @return mastery query
     */
    @NotNull
    public static MasteryQuery byTenant(@NotNull String tenantId) {
        return new MasteryQuery(null, null, null, tenantId, null, null, null, null, null, null, null, null, null);
    }

    /**
     * Creates a query for active mastery items only.
     *
     * @return mastery query for active items
     */
    @NotNull
    public static MasteryQuery activeOnly() {
        return new MasteryQuery(
                null,
                null,
                null,
                null,
                null,
                Set.of(MasteryState.OBSERVED, MasteryState.PRACTICED, MasteryState.COMPETENT, MasteryState.MASTERED),
                false,
                false,
                false,
                null,
                null,
                null,
                null
        );
    }

    /**
     * Creates a query for fresh mastery items only.
     *
     * @param currentTime current time to check freshness against
     * @return mastery query for fresh items
     */
    @NotNull
    public static MasteryQuery freshOnly(@NotNull Instant currentTime) {
        return new MasteryQuery(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true,
                currentTime,
                null,
                null
        );
    }

    /**
     * Returns a new query with the limit set.
     *
     * @param limit maximum number of results
     * @return new query with limit
     */
    @NotNull
    public MasteryQuery withLimit(int limit) {
        return new MasteryQuery(skillId, agentId, agentReleaseId, tenantId, domain, states, includeObsolete, includeRetired, includeMaintenanceOnly, requireFreshness, currentTime, limit, offset);
    }

    /**
     * Returns a new query with the offset set.
     *
     * @param offset number of results to skip
     * @return new query with offset
     */
    @NotNull
    public MasteryQuery withOffset(int offset) {
        return new MasteryQuery(skillId, agentId, agentReleaseId, tenantId, domain, states, includeObsolete, includeRetired, includeMaintenanceOnly, requireFreshness, currentTime, limit, offset);
    }

    /**
     * Returns a new query with the agent ID set.
     *
     * @param agentId agent identifier
     * @return new query with agent ID
     */
    @NotNull
    public MasteryQuery withAgentId(@NotNull String agentId) {
        return new MasteryQuery(skillId, agentId, agentReleaseId, tenantId, domain, states, includeObsolete, includeRetired, includeMaintenanceOnly, requireFreshness, currentTime, limit, offset);
    }

    /**
     * Returns a new query with the agent release ID set.
     *
     * @param agentReleaseId agent release identifier
     * @return new query with agent release ID
     */
    @NotNull
    public MasteryQuery withAgentReleaseId(@NotNull String agentReleaseId) {
        return new MasteryQuery(skillId, agentId, agentReleaseId, tenantId, domain, states, includeObsolete, includeRetired, includeMaintenanceOnly, requireFreshness, currentTime, limit, offset);
    }

    /**
     * Returns a new query with the tenant ID set.
     *
     * @param tenantId tenant identifier
     * @return new query with tenant ID
     */
    @NotNull
    public MasteryQuery withTenantId(@NotNull String tenantId) {
        return new MasteryQuery(skillId, agentId, agentReleaseId, tenantId, domain, states, includeObsolete, includeRetired, includeMaintenanceOnly, requireFreshness, currentTime, limit, offset);
    }

    /**
     * Returns a new query with the states set.
     *
     * @param states set of mastery states
     * @return new query with states
     */
    @NotNull
    public MasteryQuery withStates(@NotNull Set<MasteryState> states) {
        return new MasteryQuery(skillId, agentId, agentReleaseId, tenantId, domain, states, includeObsolete, includeRetired, includeMaintenanceOnly, requireFreshness, currentTime, limit, offset);
    }
}
