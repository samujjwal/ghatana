/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.memory.retrieval;

import com.ghatana.agent.context.version.VersionContext;
import com.ghatana.agent.framework.memory.MemoryPlane;
import com.ghatana.agent.memory.model.MemoryItem;
import com.ghatana.agent.memory.model.MemoryItemType;
import com.ghatana.agent.memory.model.MemoryQuery;
import com.ghatana.agent.mastery.MasteryItem;
import com.ghatana.agent.mastery.MasteryQuery;
import com.ghatana.agent.mastery.MasteryRegistry;
import com.ghatana.agent.mastery.MasteryState;
import com.ghatana.agent.mastery.VersionApplicability;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Mastery-aware memory retriever that filters memory based on mastery state and version applicability.
 *
 * @doc.type class
 * @doc.purpose Mastery-aware memory retrieval
 * @doc.layer agent-core
 * @doc.pattern Service
 */
public final class MasteryAwareMemoryRetriever {

    private final MasteryRegistry masteryRegistry;
    @Nullable
    private final MemoryPlane memoryPlane;

    /**
     * Creates a mastery-aware memory retriever.
     *
     * @param masteryRegistry mastery registry
     */
    public MasteryAwareMemoryRetriever(@NotNull MasteryRegistry masteryRegistry) {
        this.masteryRegistry = masteryRegistry;
        this.memoryPlane = null;
    }

    /**
     * Creates a mastery-aware memory retriever composed with MemoryPlane.
     *
     * @param masteryRegistry mastery registry
     * @param memoryPlane memory plane for memory operations
     */
    public MasteryAwareMemoryRetriever(
            @NotNull MasteryRegistry masteryRegistry,
            @Nullable MemoryPlane memoryPlane) {
        this.masteryRegistry = masteryRegistry;
        this.memoryPlane = memoryPlane;
    }

    /**
     * Filters memory query results based on mastery state and orders them by retrieval priority.
     * Retrieval priority order:
     * 1. Negative knowledge (highest priority - what to avoid)
     * 2. Active mastered skills (highest confidence)
     * 3. Semantic facts (stable knowledge)
     * 4. Episodes (contextual experiences)
     * 5. Maintenance-only skills (legacy support)
     *
     * Hard exclusion: obsolete, retired, and quarantined items are always excluded.
     *
     * @param query memory query
     * @param versionContext version context
     * @return promise of filtered and ordered memory items
     */
    @NotNull
    public Promise<List<MemoryItem>> filterByMastery(
            @NotNull MemoryQuery query,
            @NotNull VersionContext versionContext) {
        // Build mastery query from memory query parameters
        // Use skillTags as skillId if available, otherwise use agentId as fallback
        String skillId = extractSkillId(query);
        
        MasteryQuery masteryQuery = MasteryQuery.bySkill(skillId)
                .withAgentId(query.agentId())
                .withTenantId(query.tenantId());

        return masteryRegistry.query(masteryQuery)
                .then(masteryItems -> {
                    // Build skill ID to mastery item mapping (full mastery items for version context classification)
                    Map<String, MasteryItem> skillMasteryItems = masteryItems.stream()
                            .collect(Collectors.toMap(MasteryItem::skillId, item -> item));

                    // Filter and order memory items based on mastery state and version applicability
                    return Promise.of(orderMemoryItemsByMastery(query, skillMasteryItems, versionContext));
                });
    }

    /**
     * Extracts skill ID from memory query.
     * Uses skillTags if available (single tag), otherwise uses agentId as fallback.
     *
     * @param query memory query
     * @return skill identifier
     */
    @NotNull
    private String extractSkillId(@NotNull MemoryQuery query) {
        if (query.skillTags() != null && !query.skillTags().isEmpty()) {
            // Use the first skill tag as the skill ID
            return query.skillTags().iterator().next();
        }
        // Fallback to agentId if no skill tags
        return query.agentId();
    }

    /**
     * Orders memory items by mastery-based retrieval priority.
     *
     * @param query memory query
     * @param skillMasteryItems mapping of skill IDs to mastery items
     * @param versionContext version context
     * @return ordered list of memory items
     */
    @NotNull
    private List<MemoryItem> orderMemoryItemsByMastery(
            @NotNull MemoryQuery query,
            @NotNull Map<String, MasteryItem> skillMasteryItems,
            @NotNull VersionContext versionContext) {
        @SuppressWarnings("unchecked")
        List<MemoryItem> items = query.items() != null ? new ArrayList<>((List<MemoryItem>) query.items()) : new ArrayList<>();
        Instant now = Instant.now();

        // Filter out items with hard-excluded mastery states and non-applicable versions
        List<MemoryItem> filteredItems = items.stream()
                .filter(item -> {
                    MasteryItem masteryItem = skillMasteryItems.getOrDefault(item.getSkillId(), null);
                    if (masteryItem == null) {
                        // No mastery item found, use UNKNOWN state
                        return isAllowedForRetrieval(MasteryState.UNKNOWN, versionContext, now);
                    }
                    // Check version applicability
                    VersionApplicability applicability = masteryItem.versionScope().classify(versionContext);
                    if (applicability == VersionApplicability.OBSOLETE) {
                        // Obsolete versions are always excluded
                        return false;
                    }
                    return isAllowedForRetrieval(masteryItem.state(), versionContext, now);
                })
                .toList();

        // Sort by retrieval priority
        return filteredItems.stream()
                .sorted(createMasteryPriorityComparator(skillMasteryItems, versionContext))
                .collect(Collectors.toList());
    }

    /**
     * Checks if a mastery state is allowed for retrieval.
     * Hard exclusion: obsolete, retired, and quarantined items are always excluded.
     *
     * @param state mastery state
     * @param versionContext version context
     * @param now current time
     * @return true if allowed for retrieval
     */
    private boolean isAllowedForRetrieval(
            @NotNull MasteryState state,
            @NotNull VersionContext versionContext,
            @NotNull Instant now) {
        // Hard exclusion: these states are never allowed for retrieval
        return switch (state) {
            case OBSOLETE, RETIRED, QUARANTINED -> false;
            case MAINTENANCE_ONLY -> {
                // Maintenance-only items are only allowed if version context matches legacy scope
                // For now, allow but with lower priority
                yield true;
            }
            case UNKNOWN, OBSERVED, PRACTICED, COMPETENT, MASTERED -> true;
        };
    }

    /**
     * Creates a comparator for ordering memory items by mastery-based retrieval priority.
     * Priority order:
     * 1. Negative knowledge (highest priority - what to avoid)
     * 2. Active mastered skills (highest confidence)
     * 3. Semantic facts (stable knowledge)
     * 4. Episodes (contextual experiences)
     * 5. Maintenance-only skills (legacy support)
     *
     * @param skillMasteryItems mapping of skill IDs to mastery items
     * @param versionContext version context
     * @return comparator for ordering
     */
    @NotNull
    private Comparator<MemoryItem> createMasteryPriorityComparator(
            @NotNull Map<String, MasteryItem> skillMasteryItems,
            @NotNull VersionContext versionContext) {
        return (item1, item2) -> {
            int priority1 = getRetrievalPriority(item1, skillMasteryItems, versionContext);
            int priority2 = getRetrievalPriority(item2, skillMasteryItems, versionContext);

            // Higher priority items come first
            return Integer.compare(priority2, priority1);
        };
    }

    /**
     * Returns the retrieval priority for a memory item.
     * Higher values indicate higher priority.
     *
     * @param item memory item
     * @param skillMasteryItems mapping of skill IDs to mastery items
     * @param versionContext version context
     * @return retrieval priority (100-0)
     */
    private int getRetrievalPriority(
            @NotNull MemoryItem item,
            @NotNull Map<String, MasteryItem> skillMasteryItems,
            @NotNull VersionContext versionContext) {
        MasteryItem masteryItem = skillMasteryItems.getOrDefault(item.getSkillId(), null);
        MasteryState state = masteryItem != null ? masteryItem.state() : MasteryState.UNKNOWN;

        // Base priority from mastery state
        int statePriority = switch (state) {
            case MASTERED -> 90;
            case COMPETENT -> 80;
            case PRACTICED -> 60;
            case OBSERVED -> 40;
            case UNKNOWN -> 20;
            case MAINTENANCE_ONLY -> 10;
            case OBSOLETE, RETIRED, QUARANTINED -> 0; // Should be filtered out
        };

        // Adjust priority based on version applicability
        int versionAdjustment = 0;
        if (masteryItem != null) {
            VersionApplicability applicability = masteryItem.versionScope().classify(versionContext);
            versionAdjustment = switch (applicability) {
                case ACTIVE -> 5;
                case MAINTENANCE -> 0;
                case OBSOLETE -> -20; // Should be filtered out
                case UNKNOWN -> -10;
            };
        }

        // Adjust priority based on memory type
        int typeAdjustment = switch (item.getType()) {
            case PROCEDURE -> 5; // Procedural knowledge
            case FACT -> 10; // Stable knowledge
            case EPISODE -> 0; // Contextual experiences
            case NEGATIVE_KNOWLEDGE -> 5; // What does not work — valuable
            case TASK_STATE -> -5; // Workflow patterns
            case WORKING -> -15; // Ephemeral state
            case PREFERENCE -> -10; // User preferences
            case ARTIFACT -> -20; // Generic artifacts
            case CUSTOM -> -10; // Custom types
        };

        // Combine state, version, and type priorities
        return Math.max(0, statePriority + versionAdjustment + typeAdjustment);
    }

    /**
     * Returns true if memory should be retrieved based on mastery state.
     * Hard exclusion: obsolete, retired, and quarantined items are never allowed.
     *
     * @param skillId skill identifier
     * @param tenantId tenant identifier (required for tenant-scoped queries)
     * @param versionContext version context
     * @return promise of retrieval decision
     */
    @NotNull
    public Promise<Boolean> shouldRetrieve(
            @NotNull String skillId,
            @NotNull String tenantId,
            @NotNull VersionContext versionContext) {
        MasteryQuery query = MasteryQuery.bySkill(skillId)
                .withTenantId(tenantId);

        return masteryRegistry.query(query)
                .then(items -> {
                    if (items.isEmpty()) {
                        return Promise.of(false);
                    }

                    // Check if any item is allowed for retrieval
                    return Promise.of(items.stream()
                            .anyMatch(item -> {
                                // Check version applicability
                                VersionApplicability applicability = item.versionScope().classify(versionContext);
                                if (applicability == VersionApplicability.OBSOLETE) {
                                    return false;
                                }
                                return isAllowedForRetrieval(item.state(), versionContext, Instant.now());
                            }));
                });
    }

    /**
     * Returns true if memory should be retrieved based on mastery state.
     * Hard exclusion: obsolete, retired, and quarantined items are never allowed.
     * This overload is deprecated in favor of the tenant-scoped version.
     *
     * @param skillId skill identifier
     * @param versionContext version context
     * @return promise of retrieval decision
     * @deprecated Use {@link #shouldRetrieve(String, String, VersionContext)} for tenant-scoped queries
     */
    @Deprecated
    @NotNull
    public Promise<Boolean> shouldRetrieve(
            @NotNull String skillId,
            @NotNull VersionContext versionContext) {
        // Use default tenant ID for backward compatibility
        return shouldRetrieve(skillId, "default", versionContext);
    }
}
