package com.ghatana.agent.memory.observability;

import com.ghatana.agent.memory.model.*;
import com.ghatana.agent.memory.model.artifact.TypedArtifact;
import com.ghatana.agent.memory.model.episode.EnhancedEpisode;
import com.ghatana.agent.memory.model.fact.EnhancedFact;
import com.ghatana.agent.memory.model.procedure.EnhancedProcedure;
import com.ghatana.agent.memory.model.working.WorkingMemory;
import com.ghatana.agent.memory.store.*;
import com.ghatana.agent.memory.store.taskstate.TaskStateStore;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Decorator that wraps a {@link MemoryPlane} to add OpenTelemetry spans
 * and {@link MemoryMetrics} recording to every operation.
 *
 * <p>Follows the Decorator pattern — delegates all calls to the inner plane
 * while measuring latencies and recording outcomes.
 *
 * @doc.type class
 * @doc.purpose Observability decorator for MemoryPlane
 * @doc.layer agent-memory
 */
public class TracedMemoryPlane implements MemoryPlane {

    private static final Logger log = LoggerFactory.getLogger(TracedMemoryPlane.class);

    private final MemoryPlane delegate;
    private final MemoryMetrics metrics;

    public TracedMemoryPlane(@NotNull MemoryPlane delegate, @NotNull MemoryMetrics metrics) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    // ========== EPISODIC ==========

    @Override
    @NotNull
    public Promise<EnhancedEpisode> storeEpisode(@NotNull EnhancedEpisode episode) {
        long start = System.nanoTime();
        return delegate.storeEpisode(episode)
                .whenComplete(() -> metrics.recordWrite("episodic", System.nanoTime() - start));
    }

    @Override
    @NotNull
    public Promise<List<EnhancedEpisode>> queryEpisodes(@NotNull MemoryQuery query) {
        long start = System.nanoTime();
        return delegate.queryEpisodes(query)
                .whenComplete(() -> metrics.recordRead(System.nanoTime() - start));
    }

    // ========== SEMANTIC ==========

    @Override
    @NotNull
    public Promise<EnhancedFact> storeFact(@NotNull EnhancedFact fact) {
        long start = System.nanoTime();
        return delegate.storeFact(fact)
                .whenComplete(() -> metrics.recordWrite("semantic", System.nanoTime() - start));
    }

    @Override
    @NotNull
    public Promise<List<EnhancedFact>> queryFacts(@NotNull MemoryQuery query) {
        long start = System.nanoTime();
        return delegate.queryFacts(query)
                .whenComplete(() -> metrics.recordRead(System.nanoTime() - start));
    }

    // ========== PROCEDURAL ==========

    @Override
    @NotNull
    public Promise<EnhancedProcedure> storeProcedure(@NotNull EnhancedProcedure procedure) {
        long start = System.nanoTime();
        return delegate.storeProcedure(procedure)
                .whenComplete(() -> metrics.recordWrite("procedural", System.nanoTime() - start));
    }

    @Override
    @NotNull
    public Promise<List<EnhancedProcedure>> queryProcedures(@NotNull MemoryQuery query) {
        long start = System.nanoTime();
        return delegate.queryProcedures(query)
                .whenComplete(() -> metrics.recordRead(System.nanoTime() - start));
    }

    @Override
    @NotNull
    public Promise<@Nullable EnhancedProcedure> getProcedure(@NotNull String procedureId) {
        long start = System.nanoTime();
        return delegate.getProcedure(procedureId)
                .whenComplete(() -> metrics.recordRead(System.nanoTime() - start));
    }

    // ========== ARTIFACTS ==========

    @Override
    @NotNull
    public Promise<TypedArtifact> writeArtifact(@NotNull TypedArtifact artifact) {
        long start = System.nanoTime();
        return delegate.writeArtifact(artifact)
                .whenComplete(() -> metrics.recordWrite("artifact", System.nanoTime() - start));
    }

    // ========== GENERIC ==========

    @Override
    @NotNull
    public Promise<MemoryItem> store(@NotNull MemoryItem item) {
        long start = System.nanoTime();
        return delegate.store(item)
                .whenComplete(() -> metrics.recordWrite(item.getType().name().toLowerCase(), System.nanoTime() - start));
    }

    @Override
    @NotNull
    public Promise<List<MemoryItem>> query(@NotNull MemoryQuery query) {
        long start = System.nanoTime();
        return delegate.query(query)
                .whenComplete(() -> metrics.recordRead(System.nanoTime() - start));
    }

    // ========== CROSS-TIER ==========

    @Override
    @NotNull
    public Promise<List<MemoryItem>> readItems(@NotNull MemoryQuery query) {
        long start = System.nanoTime();
        return delegate.readItems(query)
                .whenComplete(() -> metrics.recordRead(System.nanoTime() - start));
    }

    @Override
    @NotNull
    public Promise<List<ScoredMemoryItem>> searchSemantic(
            @NotNull String query,
            @Nullable List<MemoryItemType> itemTypes,
            int k,
            @Nullable Instant startTime,
            @Nullable Instant endTime) {
        long start = System.nanoTime();
        return delegate.searchSemantic(query, itemTypes, k, startTime, endTime)
                .whenComplete(() -> metrics.recordSearch(System.nanoTime() - start));
    }

    // ========== DELEGATES ==========

    @Override
    @NotNull
    public WorkingMemory getWorkingMemory() {
        return delegate.getWorkingMemory();
    }

    @Override
    @NotNull
    public TaskStateStore getTaskStateStore() {
        return delegate.getTaskStateStore();
    }

    @Override
    @NotNull
    public Promise<String> checkpoint(@NotNull String taskId) {
        return delegate.checkpoint(taskId);
    }

    @Override
    @NotNull
    public Promise<MemoryPlaneStats> getStats() {
        return delegate.getStats();
    }
}
