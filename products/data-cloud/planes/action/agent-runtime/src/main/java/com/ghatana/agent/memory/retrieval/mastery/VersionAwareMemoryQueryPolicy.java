/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.memory.retrieval.mastery;

import com.ghatana.agent.context.version.VersionContext;
import com.ghatana.agent.memory.store.MemoryQuery;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Policy for applying version-aware filtering to memory queries.
 *
 * <p>Filters memory items based on version compatibility with the
 * current execution environment.
 *
 * @doc.type class
 * @doc.purpose Policy for version-aware memory query filtering
 * @doc.layer agent-runtime
 * @doc.pattern Policy
 */
public class VersionAwareMemoryQueryPolicy {

    /**
     * Applies version-aware filtering to a memory query.
     *
     * @param baseQuery the base memory query
     * @param versionContext the current version context
     * @return filtered memory query with version constraints
     */
    @NotNull
    public MemoryQuery applyVersionFilter(
            @NotNull MemoryQuery baseQuery,
            @Nullable VersionContext versionContext) {
        if (versionContext == null) {
            return baseQuery;
        }

        // Add version context reference to the query
        MemoryQuery modifiedQuery = MemoryQuery.builder()
                .tenantId(baseQuery.getTenantId())
                .itemTypes(baseQuery.getItemTypes())
                .startTime(baseQuery.getStartTime())
                .endTime(baseQuery.getEndTime())
                .limit(baseQuery.getLimit())
                .minConfidence(baseQuery.getMinConfidence())
                .versionContextRef(versionContext.toString())
                .build();

        // Filter out obsolete items by default unless explicitly requested
        if (!modifiedQuery.isIncludeObsolete()) {
            // This would be implemented by the underlying store to filter
            // based on version compatibility metadata
        }

        return modifiedQuery;
    }

    /**
     * Checks if a memory item is compatible with the given version context.
     *
     * @param itemVersion the version of the memory item
     * @param currentContext the current version context
     * @return true if compatible
     */
    public boolean isVersionCompatible(
            @Nullable String itemVersion,
            @NotNull VersionContext currentContext) {
        if (itemVersion == null) {
            return true; // No version constraint
        }

        // Version compatibility logic would go here
        // For now, return true as a placeholder
        return true;
    }
}
