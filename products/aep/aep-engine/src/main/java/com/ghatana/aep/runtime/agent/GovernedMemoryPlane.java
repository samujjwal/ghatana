/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.memory.governance;

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
 * Privacy gate decorator for {@link MemoryPlane}.
 *
 * <p>Enforces {@link DataAccessBroker#checkAccess} before every read operation
 * (query, searchSemantic, getProcedure). Write operations (store*) are allowed
 * unconditionally because they are governed separately at the ingestion boundary.
 *
 * <p>This satisfies Track X TX-1: memory retrieval and context hydration must
 * pass {@code DataAccessBroker} checks before access.
 *
 * @doc.type class
 * @doc.purpose Privacy gate decorator for MemoryPlane
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

    /**
     * Creates a governed memory plane.
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
        if (delegate == null) throw new IllegalArgumentException("delegate must not be null");
        if (accessBroker == null) throw new IllegalArgumentException("accessBroker must not be null");
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
        if (subjectId == null || subjectId.isBlank()) throw new IllegalArgumentException("subjectId must not be blank");

        this.delegate = delegate;
        this.accessBroker = accessBroker;
        this.tenantId = tenantId;
        this.subjectId = subjectId;
    }

    // =========================================================================
    // Read operations — require DataAccessBroker check first
    // =========================================================================

    @NotNull
    @Override
    public Promise<List<EnhancedEpisode>> queryEpisodes(@NotNull MemoryQuery query) {
        return accessBroker.checkAccess(tenantId, subjectId, MEMORY_DATA_ID, MEMORY_PURPOSE)
                .then(() -> delegate.queryEpisodes(query));
    }

    @NotNull
    @Override
    public Promise<List<EnhancedFact>> queryFacts(@NotNull MemoryQuery query) {
        return accessBroker.checkAccess(tenantId, subjectId, MEMORY_DATA_ID, MEMORY_PURPOSE)
                .then(() -> delegate.queryFacts(query));
    }

    @NotNull
    @Override
    public Promise<List<EnhancedProcedure>> queryProcedures(@NotNull MemoryQuery query) {
        return accessBroker.checkAccess(tenantId, subjectId, MEMORY_DATA_ID, MEMORY_PURPOSE)
                .then(() -> delegate.queryProcedures(query));
    }

    @NotNull
    @Override
    public Promise<@Nullable EnhancedProcedure> getProcedure(@NotNull String procedureId) {
        return accessBroker.checkAccess(tenantId, subjectId, MEMORY_DATA_ID, MEMORY_PURPOSE)
                .then(() -> delegate.getProcedure(procedureId));
    }

    @NotNull
    @Override
    public Promise<List<MemoryItem>> query(@NotNull MemoryQuery query) {
        return accessBroker.checkAccess(tenantId, subjectId, MEMORY_DATA_ID, MEMORY_PURPOSE)
                .then(() -> delegate.query(query));
    }

    @NotNull
    @Override
    public Promise<List<MemoryItem>> readItems(@NotNull MemoryQuery query) {
        return accessBroker.checkAccess(tenantId, subjectId, MEMORY_DATA_ID, MEMORY_PURPOSE)
                .then(() -> delegate.readItems(query));
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
                .then(() -> delegate.searchSemantic(query, itemTypes, k, startTime, endTime));
    }

    // =========================================================================
    // Write operations — pass through without access check
    // =========================================================================

    @NotNull
    @Override
    public Promise<EnhancedEpisode> storeEpisode(@NotNull EnhancedEpisode episode) {
        return delegate.storeEpisode(episode);
    }

    @NotNull
    @Override
    public Promise<EnhancedFact> storeFact(@NotNull EnhancedFact fact) {
        return delegate.storeFact(fact);
    }

    @NotNull
    @Override
    public Promise<EnhancedProcedure> storeProcedure(@NotNull EnhancedProcedure procedure) {
        return delegate.storeProcedure(procedure);
    }

    @NotNull
    @Override
    public Promise<TypedArtifact> writeArtifact(@NotNull TypedArtifact artifact) {
        return delegate.writeArtifact(artifact);
    }

    @NotNull
    @Override
    public Promise<MemoryItem> store(@NotNull MemoryItem item) {
        return delegate.store(item);
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
