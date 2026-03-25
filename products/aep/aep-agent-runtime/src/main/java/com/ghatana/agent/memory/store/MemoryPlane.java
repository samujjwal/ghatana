package com.ghatana.agent.memory.store;

import com.ghatana.agent.memory.model.*;
import com.ghatana.agent.memory.model.artifact.TypedArtifact;
import com.ghatana.agent.memory.model.episode.EnhancedEpisode;
import com.ghatana.agent.memory.model.fact.EnhancedFact;
import com.ghatana.agent.memory.model.procedure.EnhancedProcedure;
import com.ghatana.agent.memory.model.working.WorkingMemory;
import com.ghatana.agent.memory.store.taskstate.TaskStateStore;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * The Memory Plane is the evolution of {@code MemoryStore} — a richer SPI
 * that spans all memory tiers: episodic, semantic, procedural, task-state,
 * working, preference, and typed artifacts.
 *
 * <p>Every operation is async ({@link Promise}) and emits OpenTelemetry spans.
 *
 * <p><b>Backward compatibility:</b> The existing {@code MemoryStore} interface
 * is preserved as a thin facade. Use {@link com.ghatana.agent.memory.store.MemoryStoreAdapter}
 * or {@link com.ghatana.agent.memory.store.LegacyMemoryPlaneAdapter} for interop.
 *
 * @doc.type interface
 * @doc.purpose Multi-tier memory plane SPI
 * @doc.layer agent-memory
 * @doc.pattern Strategy / SPI
 */
public interface MemoryPlane {

    // =========================================================================
    // Episodic Memory
    // =========================================================================

    @NotNull Promise<EnhancedEpisode> storeEpisode(@NotNull EnhancedEpisode episode);

    @NotNull Promise<List<EnhancedEpisode>> queryEpisodes(@NotNull MemoryQuery query);

    // =========================================================================
    // Semantic Memory (Facts)
    // =========================================================================

    @NotNull Promise<EnhancedFact> storeFact(@NotNull EnhancedFact fact);

    @NotNull Promise<List<EnhancedFact>> queryFacts(@NotNull MemoryQuery query);

    // =========================================================================
    // Procedural Memory
    // =========================================================================

    @NotNull Promise<EnhancedProcedure> storeProcedure(@NotNull EnhancedProcedure procedure);

    @NotNull Promise<List<EnhancedProcedure>> queryProcedures(@NotNull MemoryQuery query);

    @NotNull Promise<@Nullable EnhancedProcedure> getProcedure(@NotNull String procedureId);

    // =========================================================================
    // Typed Artifacts
    // =========================================================================

    @NotNull Promise<TypedArtifact> writeArtifact(@NotNull TypedArtifact artifact);

    // =========================================================================
    // Cross-tier Query + Semantic Search
    // =========================================================================

    /**
     * Generic store: persists any {@link MemoryItem} by dispatching to
     * the appropriate tier-specific method based on {@link MemoryItem#getType()}.
     *
     * <p>This is the recommended entry point when the caller works with
     * polymorphic items. Tier-specific methods ({@link #storeEpisode},
     * {@link #storeFact}, etc.) remain as typed convenience overloads.
     *
     * @param item the memory item to persist (must implement one of the tier-specific types)
     * @return the persisted item (same reference or refreshed)
     */
    @NotNull Promise<MemoryItem> store(@NotNull MemoryItem item);

    /**
     * Generic query: reads memory items across all tiers matching the given query.
     *
     * <p>Equivalent to {@link #readItems(MemoryQuery)} — provided as a symmetric
     * counterpart to {@link #store(MemoryItem)} for a clean generic API surface.
     *
     * @param query Query filters
     * @return List of matching items across tiers
     */
    @NotNull Promise<List<MemoryItem>> query(@NotNull MemoryQuery query);

    /**
     * Read memory items across all tiers matching the given query.
     *
     * @param query Query filters
     * @return List of matching items
     */
    @NotNull Promise<List<MemoryItem>> readItems(@NotNull MemoryQuery query);

    /**
     * Semantic vector search across memory tiers.
     *
     * @param query      Natural language query
     * @param itemTypes  Optional tier filter (null = all tiers)
     * @param k          Maximum results to return
     * @param startTime  Optional time range start
     * @param endTime    Optional time range end
     * @return Scored results ranked by relevance
     */
    @NotNull Promise<List<ScoredMemoryItem>> searchSemantic(
            @NotNull String query,
            @Nullable List<MemoryItemType> itemTypes,
            int k,
            @Nullable Instant startTime,
            @Nullable Instant endTime);

    // =========================================================================
    // Working Memory
    // =========================================================================

    /** Returns the bounded working memory for this execution context. */
    @NotNull WorkingMemory getWorkingMemory();

    // =========================================================================
    // Task-State Memory
    // =========================================================================

    /** Returns the task-state store for multi-session workflows. */
    @NotNull TaskStateStore getTaskStateStore();

    // =========================================================================
    // Lifecycle
    // =========================================================================

    /**
     * Creates a checkpoint for a task.
     *
     * @param taskId Task identifier
     * @return Promise of checkpoint ID
     */
    @NotNull Promise<String> checkpoint(@NotNull String taskId);

    /**
     * Gets memory plane statistics.
     *
     * @return Statistics across all tiers
     */
    @NotNull Promise<MemoryPlaneStats> getStats();
}
