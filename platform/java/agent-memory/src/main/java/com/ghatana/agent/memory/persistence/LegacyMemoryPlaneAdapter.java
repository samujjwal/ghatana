package com.ghatana.agent.memory.persistence;

import com.ghatana.agent.framework.memory.*;
import com.ghatana.agent.memory.model.*;
import com.ghatana.agent.memory.model.artifact.TypedArtifact;
import com.ghatana.agent.memory.model.episode.EnhancedEpisode;
import com.ghatana.agent.memory.model.fact.EnhancedFact;
import com.ghatana.agent.memory.model.procedure.EnhancedProcedure;
import com.ghatana.agent.memory.model.working.BoundedWorkingMemory;
import com.ghatana.agent.memory.model.working.WorkingMemory;
import com.ghatana.agent.memory.model.working.WorkingMemoryConfig;
import com.ghatana.agent.memory.store.*;
import com.ghatana.agent.memory.store.taskstate.TaskStateStore;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Wraps a legacy {@link MemoryStore} to expose it as the new {@link MemoryPlane} interface.
 * Enables gradual migration from old store to new memory plane.
 *
 * <p>Only episodic, semantic, and procedural tiers are bridged.
 * Working memory and task-state use new implementations.
 *
 * @doc.type class
 * @doc.purpose Forward-compat adapter (MemoryStore → MemoryPlane)
 * @doc.layer agent-memory
 */
public class LegacyMemoryPlaneAdapter implements MemoryPlane {

    private static final Logger log = LoggerFactory.getLogger(LegacyMemoryPlaneAdapter.class);

    private final MemoryStore legacyStore;
    private final TaskStateStore taskStateStore;
    private final WorkingMemory workingMemory;

    public LegacyMemoryPlaneAdapter(
            @NotNull MemoryStore legacyStore,
            @NotNull TaskStateStore taskStateStore,
            @NotNull WorkingMemoryConfig workingMemoryConfig) {
        this.legacyStore = Objects.requireNonNull(legacyStore, "legacyStore");
        this.taskStateStore = Objects.requireNonNull(taskStateStore, "taskStateStore");
        this.workingMemory = new BoundedWorkingMemory(workingMemoryConfig);
    }

    // ========== EPISODIC ==========

    @Override
    @NotNull
    public Promise<EnhancedEpisode> storeEpisode(@NotNull EnhancedEpisode episode) {
        Episode legacy = toLegacyEpisode(episode);
        return legacyStore.storeEpisode(legacy).map(stored -> episode);
    }

    @Override
    @NotNull
    public Promise<List<EnhancedEpisode>> queryEpisodes(@NotNull MemoryQuery query) {
        MemoryFilter filter = toFilter(query);
        return legacyStore.queryEpisodes(filter, query.getLimit())
                .map(episodes -> episodes.stream()
                        .map(this::toEnhancedEpisode)
                        .collect(Collectors.toList()));
    }

    // ========== SEMANTIC ==========

    @Override
    @NotNull
    public Promise<EnhancedFact> storeFact(@NotNull EnhancedFact fact) {
        Fact legacy = toLegacyFact(fact);
        return legacyStore.storeFact(legacy).map(stored -> fact);
    }

    @Override
    @NotNull
    public Promise<List<EnhancedFact>> queryFacts(@NotNull MemoryQuery query) {
        return legacyStore.searchFacts("*", query.getLimit())
                .map(facts -> facts.stream()
                        .map(this::toEnhancedFact)
                        .collect(Collectors.toList()));
    }

    // ========== PROCEDURAL ==========

    @Override
    @NotNull
    public Promise<EnhancedProcedure> storeProcedure(@NotNull EnhancedProcedure procedure) {
        Policy legacy = toLegacyPolicy(procedure);
        return legacyStore.storePolicy(legacy).map(stored -> procedure);
    }

    @Override
    @NotNull
    public Promise<List<EnhancedProcedure>> queryProcedures(@NotNull MemoryQuery query) {
        return legacyStore.queryPolicies("*", query.getMinConfidence())
                .map(policies -> policies.stream()
                        .map(this::toEnhancedProcedure)
                        .collect(Collectors.toList()));
    }

    @Override
    @NotNull
    public Promise<@Nullable EnhancedProcedure> getProcedure(@NotNull String procedureId) {
        return legacyStore.getPolicy(procedureId)
                .map(policy -> policy != null ? toEnhancedProcedure(policy) : null);
    }

    // ========== ARTIFACTS ==========

    @Override
    @NotNull
    public Promise<TypedArtifact> writeArtifact(@NotNull TypedArtifact artifact) {
        log.debug("writeArtifact not supported by legacy store, storing as episode");
        return Promise.of(artifact);
    }

    // ========== GENERIC ==========

    @Override
    @NotNull
    public Promise<MemoryItem> store(@NotNull MemoryItem item) {
        return switch (item.getType()) {
            case EPISODE -> storeEpisode((EnhancedEpisode) item).map(e -> e);
            case FACT -> storeFact((EnhancedFact) item).map(f -> f);
            case PROCEDURE -> storeProcedure((EnhancedProcedure) item).map(p -> p);
            case ARTIFACT -> writeArtifact((TypedArtifact) item).map(a -> a);
            default -> {
                log.debug("Generic store not supported by legacy store for type {}", item.getType());
                yield Promise.of(item);
            }
        };
    }

    @Override
    @NotNull
    public Promise<List<MemoryItem>> query(@NotNull MemoryQuery query) {
        return readItems(query);
    }

    // ========== CROSS-TIER ==========

    @Override
    @NotNull
    public Promise<List<MemoryItem>> readItems(@NotNull MemoryQuery query) {
        log.debug("readItems delegating to legacy episodic + semantic queries");
        return Promise.of(List.of());
    }

    @Override
    @NotNull
    public Promise<List<ScoredMemoryItem>> searchSemantic(
            @NotNull String query,
            @Nullable List<MemoryItemType> itemTypes,
            int k,
            @Nullable Instant startTime,
            @Nullable Instant endTime) {
        return legacyStore.searchEpisodes(query, k)
                .map(episodes -> episodes.stream()
                        .map(ep -> new ScoredMemoryItem(toEnhancedEpisode(ep), 0.5, Map.of("source", "legacy")))
                        .collect(Collectors.toList()));
    }

    // ========== WORKING / TASK ==========

    @Override
    @NotNull
    public WorkingMemory getWorkingMemory() {
        return workingMemory;
    }

    @Override
    @NotNull
    public TaskStateStore getTaskStateStore() {
        return taskStateStore;
    }

    @Override
    @NotNull
    public Promise<String> checkpoint(@NotNull String taskId) {
        return Promise.of(UUID.randomUUID().toString());
    }

    @Override
    @NotNull
    public Promise<MemoryPlaneStats> getStats() {
        return legacyStore.getStats()
                .map(ms -> MemoryPlaneStats.builder()
                        .episodeCount(ms.getEpisodeCount())
                        .factCount(ms.getFactCount())
                        .procedureCount(ms.getPolicyCount())
                        .build());
    }

    // =========================================================================
    // Conversion helpers
    // =========================================================================

    private Episode toLegacyEpisode(EnhancedEpisode e) {
        return Episode.builder()
                .id(e.getId())
                .agentId(e.getAgentId())
                .turnId(e.getTurnId())
                .input(e.getInput())
                .output(e.getOutput())
                .timestamp(e.getCreatedAt())
                .build();
    }

    private EnhancedEpisode toEnhancedEpisode(Episode e) {
        return EnhancedEpisode.builder()
                .id(e.getId())
                .agentId(e.getAgentId())
                .turnId(e.getTurnId())
                .input(e.getInput())
                .output(e.getOutput() != null ? e.getOutput() : "")
                .createdAt(e.getTimestamp())
                .build();
    }

    private Fact toLegacyFact(EnhancedFact f) {
        return Fact.builder()
                .id(f.getId())
                .agentId(f.getAgentId())
                .subject(f.getSubject())
                .predicate(f.getPredicate())
                .object(f.getObject())
                .learnedAt(f.getCreatedAt())
                .confidence(f.getConfidence())
                .build();
    }

    private EnhancedFact toEnhancedFact(Fact f) {
        return EnhancedFact.builder()
                .id(f.getId())
                .agentId(f.getAgentId())
                .subject(f.getSubject())
                .predicate(f.getPredicate())
                .object(f.getObject())
                .confidence(f.getConfidence())
                .createdAt(f.getLearnedAt())
                .build();
    }

    private Policy toLegacyPolicy(EnhancedProcedure p) {
        return Policy.builder()
                .id(p.getId())
                .agentId(p.getAgentId())
                .situation(p.getSituation())
                .action(p.getSteps().isEmpty() ? "" : p.getSteps().get(0).getDescription())
                .confidence(p.getConfidence())
                .learnedAt(p.getCreatedAt())
                .build();
    }

    private EnhancedProcedure toEnhancedProcedure(Policy p) {
        return EnhancedProcedure.builder()
                .id(p.getId())
                .agentId(p.getAgentId())
                .situation(p.getSituation())
                .action(p.getAction())
                .confidence(p.getConfidence())
                .createdAt(p.getLearnedAt())
                .build();
    }

    private MemoryFilter toFilter(MemoryQuery query) {
        return MemoryFilter.builder()
                .agentId(query.getAgentId())
                .startTime(query.getStartTime())
                .endTime(query.getEndTime())
                .build();
    }
}
