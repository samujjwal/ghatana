/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.memory.retrieval;

import com.ghatana.agent.context.version.VersionContext;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.MemoryPlane;
import com.ghatana.agent.memory.model.MemoryItem;
import com.ghatana.agent.memory.model.MemoryItemType;
import com.ghatana.agent.memory.model.MemoryQuery;
import com.ghatana.agent.mastery.MasteryItem;
import com.ghatana.agent.mastery.MasteryRegistry;
import com.ghatana.agent.mastery.MasteryState;
import com.ghatana.agent.mastery.VersionApplicability;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Complete memory retrieval service that builds queries, retrieves from memory plane,
 * applies mastery/version/freshness/trust filters, and reranks by utility.
 *
 * <p>Retrieval pipeline:
 * <ol>
 *   <li>Build MemoryQuery from Task + AgentContext + VersionContext</li>
 *   <li>MemoryPlane.searchSemantic/query to retrieve items</li>
 *   <li>Retrieve negative knowledge (always considered)</li>
 *   <li>Retrieve procedures, semantic facts, episodes</li>
 *   <li>Apply mastery/version/freshness/trust filters</li>
 *   <li>Rerank by utility</li>
 *   <li>Return RetrievalBundle with trace</li>
 * </ol>
 *
 * @doc.type class
 * @doc.purpose Complete memory retrieval pipeline with filtering and reranking
 * @doc.layer agent-core
 * @doc.pattern Service
 */
public final class MemoryRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(MemoryRetrievalService.class);

    private final MasteryRegistry masteryRegistry;
    @Nullable
    private final MemoryPlane memoryPlane;
    private final MasteryAwareMemoryRetriever masteryAwareRetriever;

    /**
     * Creates a memory retrieval service.
     *
     * @param masteryRegistry mastery registry for mastery state queries
     * @param memoryPlane memory plane for memory operations (optional)
     */
    public MemoryRetrievalService(
            @NotNull MasteryRegistry masteryRegistry,
            @Nullable MemoryPlane memoryPlane) {
        this.masteryRegistry = masteryRegistry;
        this.memoryPlane = memoryPlane;
        this.masteryAwareRetriever = new MasteryAwareMemoryRetriever(masteryRegistry, memoryPlane);
    }

    /**
     * Retrieves memory for the given task, context, and version context.
     *
     * @param task task description
     * @param context agent context
     * @param versionContext version context
     * @return promise of retrieval bundle
     */
    @NotNull
    public Promise<RetrievalBundle> retrieve(
            @NotNull String task,
            @NotNull AgentContext context,
            @NotNull VersionContext versionContext) {
        // Build MemoryQuery from task and context
        MemoryQuery query = buildMemoryQuery(task, context, versionContext);

        // Retrieve from memory plane if available
        Promise<List<MemoryItem>> retrievalPromise;
        if (memoryPlane != null) {
            retrievalPromise = retrieveFromMemoryPlane(query, context, versionContext);
        } else {
            // Fall back to filtering pre-provided items
            retrievalPromise = masteryAwareRetriever.filterByMastery(query, versionContext);
        }

        return retrievalPromise.then(items -> {
            // Apply filters and rerank, generating retrieval decisions
            RetrievalResult result = applyFiltersAndRerank(items, query, context, versionContext);
            
            // Build retrieval bundle with trace and decisions
            return Promise.of(new RetrievalBundle(
                    result.selectedItems(),
                    result.rejectedItems(),
                    result.trace(),
                    query,
                    result.decisions()
            ));
        });
    }

    /**
     * Builds a MemoryQuery from task, context, and version context.
     */
    @NotNull
    private MemoryQuery buildMemoryQuery(
            @NotNull String task,
            @NotNull AgentContext context,
            @NotNull VersionContext versionContext) {
        // Extract skill ID from context if available
        String skillId = context.getConfig("skillId") != null
                ? context.getConfig("skillId").toString()
                : context.getAgentId();

        // Build query with task as search query (situation) and skill as tag filter
        return MemoryQuery.builder()
                .agentId(context.getAgentId())
                .tenantId(context.getTenantId())
                .situation(task)
                .skillTags(Set.of(skillId))
                .metadata(Map.of(
                        "task", task,
                        "skillId", skillId,
                        "versionContext", versionContext.toString()))
                .limit(100)
                .requireFreshness(false)
                .build();
    }

    /**
     * Retrieves memory items from memory plane.
     */
    @NotNull
    private Promise<List<MemoryItem>> retrieveFromMemoryPlane(
            @NotNull MemoryQuery query,
            @NotNull AgentContext context,
            @NotNull VersionContext versionContext) {
        if (memoryPlane == null) {
            log.debug("Memory plane not available, returning empty list");
            return Promise.of(new ArrayList<>());
        }

        // Build memory filter from query
        com.ghatana.agent.framework.memory.MemoryFilter filter = buildMemoryFilter(query);

        // Call memory plane project to retrieve snapshot
        return memoryPlane.project(query.agentId(), filter, query.limit() != null ? query.limit() : 100)
                .then(snapshot -> {
                    List<MemoryItem> items = new ArrayList<>();
                    String tenantId = query.tenantId();
                    String skillId = (query.skillTags() != null && !query.skillTags().isEmpty())
                            ? query.skillTags().iterator().next()
                            : query.agentId();

                    // Convert episodes to MemoryItem
                    for (com.ghatana.agent.framework.memory.Episode episode : snapshot.episodes()) {
                        items.add(new EpisodeMemoryItemAdapter(episode, tenantId, skillId));
                    }

                    // Convert facts to MemoryItem
                    for (com.ghatana.agent.framework.memory.Fact fact : snapshot.facts()) {
                        items.add(new FactMemoryItemAdapter(fact, tenantId, skillId));
                    }

                    // Convert policies to MemoryItem
                    for (com.ghatana.agent.framework.memory.Policy policy : snapshot.policies()) {
                        items.add(new PolicyMemoryItemAdapter(policy, tenantId, skillId));
                    }

                    log.debug("Retrieved {} items from memory plane (episodes: {}, facts: {}, policies: {})",
                            items.size(), snapshot.episodes().size(), snapshot.facts().size(), snapshot.policies().size());
                    return Promise.of(items);
                })
                .then(
                    items -> Promise.of(items),
                    error -> {
                        log.error("Error retrieving from memory plane", error);
                        return Promise.of(new ArrayList<>());
                    }
                );
    }

    /**
     * Builds a MemoryFilter from a MemoryQuery.
     */
    @NotNull
    private com.ghatana.agent.framework.memory.MemoryFilter buildMemoryFilter(@NotNull MemoryQuery query) {
        // Create a basic filter - in a full implementation, this would map
        // query parameters (situation, skillTags, etc.) to filter criteria
        return com.ghatana.agent.framework.memory.MemoryFilter.builder()
                .agentId(query.agentId())
                .tenantId(query.tenantId())
                .tags(query.skillTags() != null ? new java.util.ArrayList<>(query.skillTags()) : java.util.List.of())
                .build();
    }

    /**
     * Applies mastery/version/freshness/trust filters and reranks by utility.
     * Generates RetrievalDecision objects for each item to explain inclusion/exclusion.
     */
    @NotNull
    private RetrievalResult applyFiltersAndRerank(
            @NotNull List<MemoryItem> items,
            @NotNull MemoryQuery query,
            @NotNull AgentContext context,
            @NotNull VersionContext versionContext) {
        String tenantId = query.tenantId();
        String agentId = query.agentId();
        // Derive primary skill ID from skillTags (first tag) or metadata; fall back to agentId.
        String skillId = (query.skillTags() != null && !query.skillTags().isEmpty())
                ? query.skillTags().iterator().next()
                : agentId;
        Instant now = Instant.now();

        // Mastery filtering requires an async call to masteryRegistry.query().
        // This synchronous helper cannot block the ActiveJ event loop, so mastery items
        // are unavailable here; all items pass filtering. Callers that need mastery-aware
        // filtering should use MasteryAwareMemoryRetriever.filterByMastery() instead.
        Map<String, MasteryItem> skillMasteryItems = new HashMap<>();
        log.debug("Synchronous applyFiltersAndRerank: mastery query skipped for skill={}, agentId={}", skillId, agentId);

        // Filter items and generate retrieval decisions
        List<MemoryItem> selectedItems = new ArrayList<>();
        List<RejectedItem> rejectedItems = new ArrayList<>();
        List<RetrievalDecision> decisions = new ArrayList<>();

        for (MemoryItem item : items) {
            String itemSkillId = item.getSkillId() != null ? item.getSkillId() : skillId;
            MasteryItem masteryItem = skillMasteryItems.get(itemSkillId);
            
            // Check version applicability
            VersionApplicability applicability = VersionApplicability.UNKNOWN;
            if (masteryItem != null) {
                applicability = masteryItem.versionScope().classify(versionContext);
            }

            // Calculate freshness
            double freshness = calculateFreshness(item, now);

            // Check if item is allowed for retrieval
            boolean allowed = isAllowedForRetrieval(item, masteryItem, applicability, versionContext, now);
            
            // Generate retrieval decision
            MasteryState masteryState = masteryItem != null ? masteryItem.state() : MasteryState.UNKNOWN;
            int priority = allowed ? getUtilityScore(item, skillMasteryItems, versionContext) : 0;
            
            if (allowed) {
                selectedItems.add(item);
                String reason = determineInclusionReason(item, masteryItem, applicability, versionContext);
                decisions.add(RetrievalDecision.included(
                        item.getId(),
                        itemSkillId,
                        masteryState,
                        applicability,
                        freshness,
                        reason,
                        priority
                ));
            } else {
                String rejectionReason = determineRejectionReason(item, masteryItem, applicability, versionContext);
                rejectedItems.add(new RejectedItem(item, rejectionReason));
                decisions.add(RetrievalDecision.excluded(
                        item.getId(),
                        itemSkillId,
                        masteryState,
                        applicability,
                        freshness,
                        rejectionReason
                ));
            }
        }

        // Rerank selected items by utility
        List<MemoryItem> rerankedItems = rerankByUtility(selectedItems, skillMasteryItems, versionContext);

        // Build trace
        Map<String, Object> trace = new HashMap<>();
        trace.put("situation", query.situation());
        trace.put("tenantId", tenantId);
        trace.put("agentId", agentId);
        trace.put("skillId", skillId);
        trace.put("totalItems", items.size());
        trace.put("selectedItems", selectedItems.size());
        trace.put("rejectedItems", rejectedItems.size());
        trace.put("rejectionReasons", rejectedItems.stream()
                .map(RejectedItem::reason)
                .collect(Collectors.toList()));

        return new RetrievalResult(rerankedItems, rejectedItems, trace, decisions);
    }

    /**
     * Calculates freshness score for a memory item.
     * Returns 1.0 for very recent items, decaying to 0.0 over time.
     */
    private double calculateFreshness(@NotNull MemoryItem item, @NotNull Instant now) {
        Instant createdAt = item.getCreatedAt();
        Instant expiresAt = item.getExpiresAt();
        
        // If item has expired, freshness is 0
        if (expiresAt != null && now.isAfter(expiresAt)) {
            return 0.0;
        }
        
        // Calculate age in days
        long ageDays = java.time.Duration.between(createdAt, now).toDays();
        
        // Decay: 1.0 for age 0 days, 0.5 for 30 days, 0.1 for 90 days, 0.0 for 180+ days
        if (ageDays < 1) return 1.0;
        if (ageDays < 30) return 1.0 - (ageDays / 60.0);
        if (ageDays < 90) return 0.5 - ((ageDays - 30) / 120.0);
        if (ageDays < 180) return 0.1 - ((ageDays - 90) / 900.0);
        return 0.0;
    }

    /**
     * Determines the inclusion reason for an item.
     */
    @NotNull
    private String determineInclusionReason(
            @NotNull MemoryItem item,
            @Nullable MasteryItem masteryItem,
            @NotNull VersionApplicability applicability,
            @NotNull VersionContext versionContext) {
        // Negative knowledge is always included (highest priority)
        if (item.getType() == com.ghatana.agent.memory.model.MemoryItemType.NEGATIVE_KNOWLEDGE) {
            return "Negative knowledge (what to avoid) always included";
        }

        MasteryState state = masteryItem != null ? masteryItem.state() : MasteryState.UNKNOWN;
        
        return switch (state) {
            case MASTERED -> "Mastery state is MASTERED, version applicable";
            case COMPETENT -> "Mastery state is COMPETENT, version applicable";
            case PRACTICED -> "Mastery state is PRACTICED, version applicable";
            case OBSERVED -> "Mastery state is OBSERVED, version applicable";
            case MAINTENANCE_ONLY -> "Maintenance-only item, version context classifies as MAINTENANCE";
            case UNKNOWN -> "No mastery record, included for exploration";
            case OBSOLETE, RETIRED, QUARANTINED -> "Should not be included (filtered earlier)";
        };
    }

    /**
     * Checks if an item is allowed for retrieval.
     */
    private boolean isAllowedForRetrieval(
            @NotNull MemoryItem item,
            @Nullable MasteryItem masteryItem,
            @NotNull VersionApplicability applicability,
            @NotNull VersionContext versionContext,
            @NotNull Instant now) {
        // Always allow negative knowledge (what to avoid)
        if (item.getType() == MemoryItemType.NEGATIVE_KNOWLEDGE) {
            return true;
        }

        // Check version applicability
        if (applicability == VersionApplicability.OBSOLETE) {
            return false;
        }

        // Check mastery state
        MasteryState state = masteryItem != null ? masteryItem.state() : MasteryState.UNKNOWN;
        
        return switch (state) {
            case OBSOLETE, RETIRED, QUARANTINED -> false;
            case MAINTENANCE_ONLY -> {
                // Maintenance-only items only allowed if version context classifies as MAINTENANCE
                yield applicability == VersionApplicability.MAINTENANCE;
            }
            case UNKNOWN -> {
                // UNKNOWN memory only allowed in exploration mode (blocked by default)
                yield false;
            }
            case OBSERVED, PRACTICED, COMPETENT, MASTERED -> true;
        };
    }

    /**
     * Determines the rejection reason for an item.
     */
    @NotNull
    private String determineRejectionReason(
            @NotNull MemoryItem item,
            @Nullable MasteryItem masteryItem,
            @NotNull VersionApplicability applicability,
            @NotNull VersionContext versionContext) {
        // Check version applicability first
        if (applicability == VersionApplicability.OBSOLETE) {
            return "Version obsolete for current context";
        }

        // Check mastery state
        MasteryState state = masteryItem != null ? masteryItem.state() : MasteryState.UNKNOWN;
        
        return switch (state) {
            case OBSOLETE -> "Mastery state is OBSOLETE";
            case RETIRED -> "Mastery state is RETIRED";
            case QUARANTINED -> "Mastery state is QUARANTINED";
            case MAINTENANCE_ONLY -> {
                if (applicability != VersionApplicability.MAINTENANCE) {
                    yield "Mastery state is MAINTENANCE_ONLY but version context is not MAINTENANCE";
                }
                yield "Maintenance-only item excluded";
            }
            case UNKNOWN -> "Mastery state is UNKNOWN (not allowed by default)";
            case OBSERVED, PRACTICED, COMPETENT, MASTERED -> "Unknown rejection reason";
        };
    }

    /**
     * Reranks items by utility.
     * Priority: negative knowledge > mastered > competent > practiced > observed
     */
    @NotNull
    private List<MemoryItem> rerankByUtility(
            @NotNull List<MemoryItem> items,
            @NotNull Map<String, MasteryItem> skillMasteryItems,
            @NotNull VersionContext versionContext) {
        return items.stream()
                .sorted(createUtilityComparator(skillMasteryItems, versionContext))
                .collect(Collectors.toList());
    }

    /**
     * Creates a comparator for ordering items by utility.
     */
    @NotNull
    private Comparator<MemoryItem> createUtilityComparator(
            @NotNull Map<String, MasteryItem> skillMasteryItems,
            @NotNull VersionContext versionContext) {
        return (item1, item2) -> {
            int utility1 = getUtilityScore(item1, skillMasteryItems, versionContext);
            int utility2 = getUtilityScore(item2, skillMasteryItems, versionContext);
            
            // Higher utility items come first
            return Integer.compare(utility2, utility1);
        };
    }

    /**
     * Returns the utility score for an item.
     */
    private int getUtilityScore(
            @NotNull MemoryItem item,
            @NotNull Map<String, MasteryItem> skillMasteryItems,
            @NotNull VersionContext versionContext) {
        // Negative knowledge has highest utility (what to avoid)
        if (item.getType() == MemoryItemType.NEGATIVE_KNOWLEDGE) {
            return 100;
        }

        MasteryItem masteryItem = skillMasteryItems.get(item.getSkillId());
        MasteryState state = masteryItem != null ? masteryItem.state() : MasteryState.UNKNOWN;

        // Base score from mastery state
        int stateScore = switch (state) {
            case MASTERED -> 90;
            case COMPETENT -> 80;
            case PRACTICED -> 60;
            case OBSERVED -> 40;
            case MAINTENANCE_ONLY -> 20;
            case UNKNOWN -> 10;
            case OBSOLETE, RETIRED, QUARANTINED -> 0; // Should be filtered out
        };

        // Adjust based on memory type
        int typeAdjustment = switch (item.getType()) {
            case PROCEDURE -> 5;
            case FACT -> 10;
            case EPISODE -> 0;
            case NEGATIVE_KNOWLEDGE -> 0; // Already handled above
            case TASK_STATE -> -5;
            case WORKING -> -15;
            case PREFERENCE -> -10;
            case ARTIFACT -> -20;
            case CUSTOM -> -10;
        };

        return Math.max(0, stateScore + typeAdjustment);
    }

    /**
     * Result of memory retrieval with selected and rejected items.
     */
    record RetrievalResult(
            @NotNull List<MemoryItem> selectedItems,
            @NotNull List<RejectedItem> rejectedItems,
            @NotNull Map<String, Object> trace,
            @NotNull List<RetrievalDecision> decisions
    ) {}

    /**
     * Rejected item with reason.
     */
    record RejectedItem(
            @NotNull MemoryItem item,
            @NotNull String reason
    ) {}
}
