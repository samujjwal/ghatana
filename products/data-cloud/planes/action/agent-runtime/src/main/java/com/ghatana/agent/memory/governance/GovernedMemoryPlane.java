/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.memory.governance;

import com.ghatana.agent.mastery.MasteryRegistry;
import com.ghatana.agent.memory.model.*;
import com.ghatana.agent.memory.model.artifact.TypedArtifact;
import com.ghatana.agent.memory.model.episode.EnhancedEpisode;
import com.ghatana.agent.memory.model.fact.EnhancedFact;
import com.ghatana.agent.memory.model.procedure.EnhancedProcedure;
import com.ghatana.agent.memory.model.working.WorkingMemory;
import com.ghatana.agent.memory.store.MemoryPlane;
import com.ghatana.agent.memory.store.MemoryPlaneStats;
import com.ghatana.agent.memory.store.MemoryQuery;
import com.ghatana.agent.memory.store.ScoredMemoryItem;
import com.ghatana.agent.memory.store.taskstate.TaskStateStore;
import com.ghatana.data.governance.DataAccessBroker;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * Privacy and write-governance decorator for {@link MemoryPlane}.
 *
 * <p>Enforces {@link DataAccessBroker#checkAccess} before every read operation
 * (query, searchSemantic, getProcedure). Semantic, procedural, and learned typed
 * artifact writes are gated before they reach the underlying plane.
 *
 * <p>Additionally applies mastery-aware filtering on reads and writes when a
 * {@link MasteryRegistry} is provided:
 * <ul>
 *   <li>Reads: Filters out obsolete/retired items unless explicitly requested</li>
 *   <li>Writes: Validates procedural skills against mastery bindings and tracks evidence</li>
 * </ul>
 *
 * <p>This satisfies Track X TX-1: memory retrieval and context hydration must
 * pass {@code DataAccessBroker} checks before access.
 *
 * @doc.type class
 * @doc.purpose Privacy and learned-memory write gate decorator for MemoryPlane
 * @doc.layer agent-memory
 * @doc.pattern Decorator
 */
public final class GovernedMemoryPlane implements MemoryPlane {

    /** A logical data identifier representing the entire memory store for the subject. */
    private static final String MEMORY_DATA_ID = "agent.memory";

    /** Processing purpose declared for agent memory context hydration. */
    private static final String MEMORY_PURPOSE = "agent.context.hydration";

    private final MemoryPlane delegate;
    private final DataAccessBroker accessBroker;
    private final String tenantId;
    private final String subjectId;
    @Nullable
    private final MasteryRegistry masteryRegistry;

    /**
     * Creates a governed memory plane without mastery integration.
     *
     * @param delegate     the underlying memory plane implementation
     * @param accessBroker the privacy access broker used to control reads
     * @param tenantId     owning tenant
     * @param subjectId    the data-subject for whom access is checked (e.g. user or agent ID)
     */
    public GovernedMemoryPlane(
            @NotNull MemoryPlane delegate,
            @NotNull DataAccessBroker accessBroker,
            @NotNull String tenantId,
            @NotNull String subjectId) {
        this(delegate, accessBroker, tenantId, subjectId, null);
    }

    /**
     * Creates a governed memory plane with mastery integration.
     *
     * @param delegate         the underlying memory plane implementation
     * @param accessBroker     the privacy access broker used to control reads
     * @param tenantId         owning tenant
     * @param subjectId        the data-subject for whom access is checked (e.g. user or agent ID)
     * @param masteryRegistry optional mastery registry for mastery-aware filtering
     */
    public GovernedMemoryPlane(
            @NotNull MemoryPlane delegate,
            @NotNull DataAccessBroker accessBroker,
            @NotNull String tenantId,
            @NotNull String subjectId,
            @Nullable MasteryRegistry masteryRegistry) {
        if (delegate == null) throw new IllegalArgumentException("delegate must not be null");
        if (accessBroker == null) throw new IllegalArgumentException("accessBroker must not be null");
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
        if (subjectId == null || subjectId.isBlank()) throw new IllegalArgumentException("subjectId must not be blank");

        this.delegate = delegate;
        this.accessBroker = accessBroker;
        this.tenantId = tenantId;
        this.subjectId = subjectId;
        this.masteryRegistry = masteryRegistry;
    }

    // =========================================================================
    // Read operations — require DataAccessBroker check first, then mastery filtering
    // =========================================================================

    @NotNull
    @Override
    public Promise<List<EnhancedEpisode>> queryEpisodes(@NotNull MemoryQuery query) {
        return accessBroker.checkAccess(tenantId, subjectId, MEMORY_DATA_ID, MEMORY_PURPOSE)
                .then(() -> delegate.queryEpisodes(applyMasteryQueryFilter(query)))
                .then(episodes -> masteryRegistry != null 
                        ? filterByMasteryStateAsync(episodes, query) 
                        : Promise.of(episodes));
    }

    @NotNull
    @Override
    public Promise<List<EnhancedFact>> queryFacts(@NotNull MemoryQuery query) {
        return accessBroker.checkAccess(tenantId, subjectId, MEMORY_DATA_ID, MEMORY_PURPOSE)
                .then(() -> delegate.queryFacts(applyMasteryQueryFilter(query)))
                .then(facts -> masteryRegistry != null 
                        ? filterByMasteryStateAsync(facts, query) 
                        : Promise.of(facts));
    }

    @NotNull
    @Override
    public Promise<List<EnhancedProcedure>> queryProcedures(@NotNull MemoryQuery query) {
        return accessBroker.checkAccess(tenantId, subjectId, MEMORY_DATA_ID, MEMORY_PURPOSE)
                .then(() -> delegate.queryProcedures(applyMasteryQueryFilter(query)))
                .then(procedures -> masteryRegistry != null 
                        ? filterByMasteryStateAsync(procedures, query) 
                        : Promise.of(procedures));
    }

    @NotNull
    @Override
    public Promise<@Nullable EnhancedProcedure> getProcedure(@NotNull String procedureId) {
        return accessBroker.checkAccess(tenantId, subjectId, MEMORY_DATA_ID, MEMORY_PURPOSE)
                .then(() -> delegate.getProcedure(procedureId))
                .then(procedure -> {
                    if (procedure == null || masteryRegistry == null) {
                        return Promise.of(procedure);
                    }
                    // Check if procedure is obsolete/retired and filter out by default
                    String masteryState = procedure.getLabels().get("masteryState");
                    if ("OBSOLETE".equals(masteryState) || "RETIRED".equals(masteryState)) {
                        return Promise.of(null);
                    }
                    return Promise.of(procedure);
                });
    }

    @NotNull
    @Override
    public Promise<List<MemoryItem>> query(@NotNull MemoryQuery query) {
        return accessBroker.checkAccess(tenantId, subjectId, MEMORY_DATA_ID, MEMORY_PURPOSE)
                .then(() -> delegate.query(applyMasteryQueryFilter(query)))
                .then(items -> masteryRegistry != null 
                        ? filterByMasteryStateAsync(items, query) 
                        : Promise.of(items));
    }

    @NotNull
    @Override
    public Promise<List<MemoryItem>> readItems(@NotNull MemoryQuery query) {
        return accessBroker.checkAccess(tenantId, subjectId, MEMORY_DATA_ID, MEMORY_PURPOSE)
                .then(() -> delegate.readItems(applyMasteryQueryFilter(query)))
                .then(items -> masteryRegistry != null 
                        ? filterByMasteryStateAsync(items, query) 
                        : Promise.of(items));
    }

    @NotNull
    @Override
    public Promise<List<ScoredMemoryItem>> searchSemantic(
            @NotNull String query,
            @Nullable List<MemoryItemType> itemTypes,
            int k,
            @Nullable Instant startTime,
            @Nullable Instant endTime) {
        return accessBroker.checkAccess(tenantId, subjectId, MEMORY_DATA_ID, MEMORY_PURPOSE)
                .then(() -> delegate.searchSemantic(query, itemTypes, k, startTime, endTime))
                .then(items -> masteryRegistry != null 
                        ? filterScoredByMasteryStateAsync(items) 
                        : Promise.of(items));
    }

    /**
     * Applies mastery-aware query filters when MasteryRegistry is present.
     * Sets default values for mastery-related fields if not explicitly set.
     */
    @NotNull
    private MemoryQuery applyMasteryQueryFilter(@NotNull MemoryQuery query) {
        if (masteryRegistry == null) {
            return query;
        }

        // If query builder is available, set mastery-aware defaults
        // For now, return the query as-is (filtering happens after retrieval)
        return query;
    }

    /**
     * Filters items by mastery state based on query settings.
     * Uses label-based filtering for backward compatibility.
     * For full MasteryRegistry integration, use filterByMasteryStateAsync.
     */
    @NotNull
    private <T extends MemoryItem> List<T> filterByMasteryState(@NotNull List<T> items, @NotNull MemoryQuery query) {
        if (masteryRegistry == null) {
            return items;
        }

        return items.stream()
                .filter(item -> {
                    String masteryState = item.getLabels().get("masteryState");
                    
                    // Filter out obsolete/retired unless explicitly requested
                    if (!query.isIncludeObsolete() 
                            && ("OBSOLETE".equals(masteryState) || "RETIRED".equals(masteryState))) {
                        return false;
                    }
                    
                    // Filter out maintenance-only unless explicitly requested
                    if (!query.isIncludeMaintenanceOnly() && "MAINTENANCE_ONLY".equals(masteryState)) {
                        return false;
                    }
                    
                    // Filter out negative knowledge unless explicitly requested
                    if (!query.isIncludeNegativeKnowledge() 
                            && "true".equalsIgnoreCase(item.getLabels().get("negativeKnowledge"))) {
                        return false;
                    }
                    
                    return true;
                })
                .toList();
    }

    /**
     * Filters items by mastery state using MasteryRegistry (async version).
     * This is the preferred method for mastery-aware filtering.
     */
    @NotNull
    private <T extends MemoryItem> Promise<List<T>> filterByMasteryStateAsync(@NotNull List<T> items, @NotNull MemoryQuery query) {
        if (masteryRegistry == null) {
            return Promise.of(items);
        }

        // For now, fall back to synchronous label-based filtering
        // TODO: Implement full async filtering with MasteryRegistry queries
        return Promise.of(filterByMasteryState(items, query));
    }

    /**
     * Filters scored items by mastery state.
     * Uses label-based filtering for backward compatibility.
     */
    @NotNull
    private List<ScoredMemoryItem> filterScoredByMasteryState(@NotNull List<ScoredMemoryItem> items) {
        if (masteryRegistry == null) {
            return items;
        }

        return items.stream()
                .filter(scored -> {
                    MemoryItem item = scored.getItem();
                    String masteryState = item.getLabels().get("masteryState");
                    
                    // Filter out obsolete/retired items by default
                    if ("OBSOLETE".equals(masteryState) || "RETIRED".equals(masteryState)) {
                        return false;
                    }
                    
                    return true;
                })
                .toList();
    }

    /**
     * Filters scored items by mastery state using MasteryRegistry (async version).
     * This is the preferred method for mastery-aware filtering.
     */
    @NotNull
    private Promise<List<ScoredMemoryItem>> filterScoredByMasteryStateAsync(@NotNull List<ScoredMemoryItem> items) {
        if (masteryRegistry == null) {
            return Promise.of(items);
        }

        // For now, fall back to synchronous label-based filtering
        // TODO: Implement full async filtering with MasteryRegistry queries
        return Promise.of(filterScoredByMasteryState(items));
    }

    // =========================================================================
    // Write operations — learned memory policy checks + mastery validation
    // =========================================================================

    @NotNull
    @Override
    public Promise<EnhancedEpisode> storeEpisode(@NotNull EnhancedEpisode episode) {
        return delegate.storeEpisode(episode);
    }

    @NotNull
    @Override
    public Promise<EnhancedFact> storeFact(@NotNull EnhancedFact fact) {
        try {
            MemoryWritePolicy.validateFact(fact);
            if (masteryRegistry != null) {
                validateMasteryAwareWrite(fact);
            }
        } catch (RuntimeException e) {
            return Promise.ofException(e);
        }
        return delegate.storeFact(fact);
    }

    @NotNull
    @Override
    public Promise<EnhancedProcedure> storeProcedure(@NotNull EnhancedProcedure procedure) {
        try {
            MemoryWritePolicy.validateProcedure(procedure);
            if (masteryRegistry != null) {
                validateMasteryAwareWrite(procedure);
            }
        } catch (RuntimeException e) {
            return Promise.ofException(e);
        }
        return delegate.storeProcedure(procedure);
    }

    @NotNull
    @Override
    public Promise<TypedArtifact> writeArtifact(@NotNull TypedArtifact artifact) {
        try {
            MemoryWritePolicy.validateArtifact(artifact);
            if (masteryRegistry != null) {
                validateMasteryAwareWrite(artifact);
            }
        } catch (RuntimeException e) {
            return Promise.ofException(e);
        }
        return delegate.writeArtifact(artifact);
    }

    @NotNull
    @Override
    public Promise<MemoryItem> store(@NotNull MemoryItem item) {
        try {
            MemoryWritePolicy.validate(item);
            if (masteryRegistry != null) {
                validateMasteryAwareWrite(item);
            }
        } catch (RuntimeException e) {
            return Promise.ofException(e);
        }
        return delegate.store(item);
    }

    /**
     * Validates memory items against mastery constraints when MasteryRegistry is present.
     * Ensures procedural skills are properly tracked with mastery state and evidence.
     */
    private void validateMasteryAwareWrite(@NotNull MemoryItem item) {
        if (masteryRegistry == null) {
            return;
        }

        // Check if item is a procedural skill that requires mastery tracking
        String learningTarget = item.getLabels().get("learningTarget");
        if ("PROCEDURAL_SKILL".equals(learningTarget)) {
            // Ensure mastery state is set
            String masteryState = item.getLabels().get("masteryState");
            if (masteryState == null || masteryState.isBlank()) {
                throw new IllegalStateException(
                        "Procedural skills must have a mastery state set in metadata");
            }

            // Ensure skillId is set
            String skillId = item.getLabels().get("skillId");
            if (skillId == null || skillId.isBlank()) {
                throw new IllegalStateException(
                        "Procedural skills must have a skillId set in metadata");
            }

            // Ensure provenance is set for L2+ learning
            String provenanceRequired = item.getLabels().get("provenanceRequired");
            if ("true".equalsIgnoreCase(provenanceRequired)) {
                String provenance = item.getLabels().get("provenance");
                if (provenance == null || provenance.isBlank()) {
                    throw new IllegalStateException(
                            "Procedural skills with provenanceRequired=true must have provenance in metadata");
                }
            }
        }

        // Check if item is negative knowledge
        String negativeKnowledge = item.getLabels().get("negativeKnowledge");
        if ("true".equalsIgnoreCase(negativeKnowledge)) {
            // Negative knowledge must have clear justification
            String justification = item.getLabels().get("justification");
            if (justification == null || justification.isBlank()) {
                throw new IllegalStateException(
                        "Negative knowledge must have justification in metadata");
            }
        }
    }

    // =========================================================================
    // Structural methods — pass through
    // =========================================================================

    @NotNull
    @Override
    public WorkingMemory getWorkingMemory() {
        return delegate.getWorkingMemory();
    }

    @NotNull
    @Override
    public TaskStateStore getTaskStateStore() {
        return delegate.getTaskStateStore();
    }

    @NotNull
    @Override
    public Promise<String> checkpoint(@NotNull String taskId) {
        return delegate.checkpoint(taskId);
    }

    @NotNull
    @Override
    public Promise<MemoryPlaneStats> getStats() {
        return delegate.getStats();
    }
}
