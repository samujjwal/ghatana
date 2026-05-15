/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.memory.retrieval;

import com.ghatana.agent.context.version.VersionContext;
import com.ghatana.agent.framework.memory.MemoryFilter;
import com.ghatana.agent.framework.memory.MemoryProjectionBridge;
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
import java.util.HashMap;
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
    private final MemoryProjectionBridge memoryPlane;

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
     * Creates a mastery-aware memory retriever composed with MemoryProjectionBridge.
     *
     * @param masteryRegistry mastery registry
     * @param memoryPlane memory projection bridge for memory operations
     */
    public MasteryAwareMemoryRetriever(
            @NotNull MasteryRegistry masteryRegistry,
            @Nullable MemoryProjectionBridge memoryPlane) {
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
                        return isAllowedForRetrieval(MasteryState.UNKNOWN, versionContext, now, null);
                    }
                    // Check version applicability
                    VersionApplicability applicability = masteryItem.versionScope().classify(versionContext);
                    if (applicability == VersionApplicability.OBSOLETE) {
                        // Obsolete versions are always excluded
                        return false;
                    }
                    return isAllowedForRetrieval(masteryItem.state(), versionContext, now, applicability);
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
     * MAINTENANCE_ONLY items are only allowed if version context classifies as MAINTENANCE.
     *
     * @param state mastery state
     * @param versionContext version context
     * @param now current time
     * @param applicability version applicability (null if unknown)
     * @return true if allowed for retrieval
     */
    private boolean isAllowedForRetrieval(
            @NotNull MasteryState state,
            @NotNull VersionContext versionContext,
            @NotNull Instant now,
            @Nullable VersionApplicability applicability) {
        // Hard exclusion: these states are never allowed for retrieval
        return switch (state) {
            case OBSOLETE, RETIRED, QUARANTINED -> false;
            case MAINTENANCE_ONLY -> {
                // Maintenance-only items are only allowed if version context matches legacy scope
                // Strict enforcement: only allow when applicability is MAINTENANCE
                yield applicability == VersionApplicability.MAINTENANCE;
            }
            case UNKNOWN -> {
                // UNKNOWN memory is only allowed in exploration mode (explicit opt-in)
                // Default to blocking unless explicitly requested for fast-learning
                yield false;
            }
            case OBSERVED, PRACTICED, COMPETENT, MASTERED -> true;
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
     * Retrieves memory items from the MemoryPlane, filtered and ordered by mastery state and version applicability.
     *
     * <p>Queries all relevant MemoryPlane tiers (episodes, facts, procedures, policies, task-state)
     * via {@link MemoryPlane#project(String, MemoryFilter, int)}, then joins mastery metadata from the
     * registry to filter out hard-excluded items (OBSOLETE, RETIRED, QUARANTINED) and rank the rest
     * by retrieval priority. Returns a {@link RetrievalBundle} with selected items, rejected items,
     * and per-item {@link RetrievalDecision} for observability.
     *
     * <p>Requires a non-null {@code MemoryPlane} to have been provided at construction time. If none
     * was provided, the method returns an empty bundle with a trace entry explaining why.
     *
     * @param agentId        agent identifier
     * @param tenantId       tenant identifier for isolation
     * @param skillId        skill to retrieve memory for (or agentId if unscoped)
     * @param versionContext version context for applicability classification
     * @param limit          maximum number of items to return
     * @return promise of retrieval bundle
     */
    @NotNull
    public Promise<RetrievalBundle> retrieve(
            @NotNull String agentId,
            @NotNull String tenantId,
            @NotNull String skillId,
            @NotNull VersionContext versionContext,
            int limit) {

        MemoryQuery stub = MemoryQuery.builder()
                .agentId(agentId)
                .tenantId(tenantId)
                .skillTags(java.util.Set.of(skillId))
                .limit(limit)
                .build();

        if (memoryPlane == null) {
            return Promise.of(new RetrievalBundle(
                    List.of(), List.of(),
                    Map.of("reason", "No MemoryPlane configured", "agentId", agentId, "tenantId", tenantId),
                    stub, List.of()));
        }

        MemoryFilter filter = MemoryFilter.builder()
                .agentId(agentId)
                .tenantId(tenantId)
                .skillId(skillId)
                .build();
        int fetchLimit = Math.max(limit * 3, 30); // fetch extra to allow mastery filtering

        return memoryPlane.project(agentId, filter, fetchLimit)
                .then(snapshot -> {
                    // Convert snapshot tiers to MemoryItem list via adapters
                    List<MemoryItem> raw = new ArrayList<>();
                    for (com.ghatana.agent.framework.memory.Episode ep : snapshot.episodes()) {
                        raw.add(new EpisodeMemoryItemAdapter(ep, tenantId, skillId));
                    }
                    for (com.ghatana.agent.framework.memory.Fact fact : snapshot.facts()) {
                        raw.add(new FactMemoryItemAdapter(fact, tenantId, skillId));
                    }
                    for (com.ghatana.agent.framework.memory.Policy policy : snapshot.policies()) {
                        raw.add(new PolicyMemoryItemAdapter(policy, tenantId, skillId));
                    }

                    String resolvedSkill = skillId.isBlank() ? agentId : skillId;
                    MasteryQuery masteryQuery = MasteryQuery.bySkill(resolvedSkill)
                            .withAgentId(agentId)
                            .withTenantId(tenantId);

                    return masteryRegistry.query(masteryQuery)
                            .then(masteryItems -> {
                                Map<String, MasteryItem> skillIndex = masteryItems.stream()
                                        .collect(Collectors.toMap(MasteryItem::skillId, m -> m));

                                Instant now = Instant.now();
                                List<MemoryItem> selected = new ArrayList<>();
                                List<MemoryRetrievalService.RejectedItem> rejected = new ArrayList<>();
                                List<RetrievalDecision> decisions = new ArrayList<>();

                                for (MemoryItem item : raw) {
                                    MasteryItem mi = skillIndex.get(item.getSkillId());
                                    MasteryState state = mi != null ? mi.state() : MasteryState.UNKNOWN;
                                    VersionApplicability applicability = mi != null
                                            ? mi.versionScope().classify(versionContext)
                                            : VersionApplicability.UNKNOWN;

                                    if (applicability == VersionApplicability.OBSOLETE) {
                                        String reason = "Version scope is OBSOLETE";
                                        rejected.add(new MemoryRetrievalService.RejectedItem(item, reason));
                                        decisions.add(RetrievalDecision.excluded(
                                                item.getId(), item.getSkillId(), state, applicability, 0.0, reason));
                                        continue;
                                    }

                                    boolean allowed = isAllowedForRetrieval(state, versionContext, now, applicability);
                                    if (!allowed) {
                                        String reason = "Mastery state " + state + " excluded from retrieval";
                                        rejected.add(new MemoryRetrievalService.RejectedItem(item, reason));
                                        decisions.add(RetrievalDecision.excluded(
                                                item.getId(), item.getSkillId(), state, applicability, 0.0, reason));
                                        continue;
                                    }

                                    int priority = getRetrievalPriority(item, skillIndex, versionContext);
                                    selected.add(item);
                                    decisions.add(RetrievalDecision.included(
                                            item.getId(), item.getSkillId(), state, applicability,
                                            freshness(item, now),
                                            "Mastery state " + state + " approved for retrieval",
                                            priority));
                                }

                                // P0 FIX: Sort selected by priority descending (higher priority first)
                                // Removed .reversed() which was causing lower priority items to come first
                                selected.sort(createMasteryPriorityComparator(skillIndex, versionContext));
                                List<MemoryItem> capped = selected.stream().limit(limit).toList();

                                Map<String, Object> trace = new HashMap<>();
                                trace.put("fetchedCount", raw.size());
                                trace.put("selectedCount", capped.size());
                                trace.put("rejectedCount", rejected.size());
                                trace.put("agentId", agentId);
                                trace.put("tenantId", tenantId);
                                trace.put("skillId", skillId);
                                trace.put("retrievedAt", now.toString());

                                return Promise.of(new RetrievalBundle(capped, rejected, trace, stub, decisions));
                            });
                });
    }

    /** Freshness score between 0.0 and 1.0 based on item age (capped at 30 days old). */
    private double freshness(@NotNull MemoryItem item, @NotNull Instant now) {
        Instant created = item.getCreatedAt();
        if (created == null) return 0.5;
        long ageMs = now.toEpochMilli() - created.toEpochMilli();
        long thirtyDaysMs = 30L * 24 * 60 * 60 * 1000;
        return Math.max(0.0, 1.0 - (double) ageMs / thirtyDaysMs);
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
        // Phase 5 FIX: Make negative knowledge first (higher priority than facts/procedures)
        int typeAdjustment = switch (item.getType()) {
            case NEGATIVE_KNOWLEDGE -> 15; // What does not work — highest priority for safety
            case FACT -> 10; // Stable knowledge
            case PROCEDURE -> 5; // Procedural knowledge
            case EPISODE -> 0; // Contextual experiences
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
                                return isAllowedForRetrieval(item.state(), versionContext, Instant.now(), applicability);
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
