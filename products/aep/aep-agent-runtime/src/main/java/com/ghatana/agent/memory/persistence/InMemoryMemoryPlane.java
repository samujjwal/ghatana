package com.ghatana.agent.memory.persistence;

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
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of MemoryPlane for testing.
 * Stores all items in ConcurrentHashMaps with basic search support.
 *
 * @doc.type class
 * @doc.purpose In-memory testing implementation
 * @doc.layer agent-memory
 */
public class InMemoryMemoryPlane implements MemoryPlane {

    private static final Logger log = LoggerFactory.getLogger(InMemoryMemoryPlane.class);

    private final ConcurrentHashMap<String, EnhancedEpisode> episodes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, EnhancedFact> facts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, EnhancedProcedure> procedures = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TypedArtifact> artifacts = new ConcurrentHashMap<>();
    private final WorkingMemory workingMemory;
    private final TaskStateStore taskStateStore;

    public InMemoryMemoryPlane(@NotNull TaskStateStore taskStateStore) {
        this.workingMemory = new BoundedWorkingMemory(WorkingMemoryConfig.builder().build());
        this.taskStateStore = Objects.requireNonNull(taskStateStore, "taskStateStore");
    }

    @Override
    @NotNull
    public Promise<EnhancedEpisode> storeEpisode(@NotNull EnhancedEpisode episode) {
        episodes.put(episode.getId(), episode);
        log.debug("Stored episode: {}", episode.getId());
        return Promise.of(episode);
    }

    @Override
    @NotNull
    public Promise<List<EnhancedEpisode>> queryEpisodes(@NotNull MemoryQuery query) {
        List<EnhancedEpisode> result = episodes.values().stream()
                .filter(e -> matchesQuery(e, query))
                .sorted(Comparator.comparing(EnhancedEpisode::getCreatedAt).reversed())
                .limit(query.getLimit())
                .collect(Collectors.toList());
        return Promise.of(result);
    }

    @Override
    @NotNull
    public Promise<EnhancedFact> storeFact(@NotNull EnhancedFact fact) {
        facts.put(fact.getId(), fact);
        log.debug("Stored fact: {}", fact.getId());
        return Promise.of(fact);
    }

    @Override
    @NotNull
    public Promise<List<EnhancedFact>> queryFacts(@NotNull MemoryQuery query) {
        List<EnhancedFact> result = facts.values().stream()
                .filter(f -> matchesQuery(f, query))
                .sorted(Comparator.comparing(EnhancedFact::getCreatedAt).reversed())
                .limit(query.getLimit())
                .collect(Collectors.toList());
        return Promise.of(result);
    }

    @Override
    @NotNull
    public Promise<EnhancedProcedure> storeProcedure(@NotNull EnhancedProcedure procedure) {
        procedures.put(procedure.getId(), procedure);
        log.debug("Stored procedure: {}", procedure.getId());
        return Promise.of(procedure);
    }

    @Override
    @NotNull
    public Promise<List<EnhancedProcedure>> queryProcedures(@NotNull MemoryQuery query) {
        List<EnhancedProcedure> result = procedures.values().stream()
                .filter(p -> matchesQuery(p, query))
                .sorted(Comparator.comparing(EnhancedProcedure::getCreatedAt).reversed())
                .limit(query.getLimit())
                .collect(Collectors.toList());
        return Promise.of(result);
    }

    @Override
    @NotNull
    public Promise<@Nullable EnhancedProcedure> getProcedure(@NotNull String procedureId) {
        return Promise.of(procedures.get(procedureId));
    }

    @Override
    @NotNull
    public Promise<TypedArtifact> writeArtifact(@NotNull TypedArtifact artifact) {
        artifacts.put(artifact.getId(), artifact);
        log.debug("Wrote artifact: {} (type={})", artifact.getId(), artifact.getArtifactType());
        return Promise.of(artifact);
    }

    @Override
    @NotNull
    public Promise<MemoryItem> store(@NotNull MemoryItem item) {
        return switch (item.getType()) {
            case EPISODE -> storeEpisode((EnhancedEpisode) item).map(e -> e);
            case FACT -> storeFact((EnhancedFact) item).map(f -> f);
            case PROCEDURE -> storeProcedure((EnhancedProcedure) item).map(p -> p);
            case ARTIFACT -> writeArtifact((TypedArtifact) item).map(a -> a);
            default -> {
                log.debug("Generic store for type {}: {}", item.getType(), item.getId());
                yield Promise.of(item);
            }
        };
    }

    @Override
    @NotNull
    public Promise<List<MemoryItem>> query(@NotNull MemoryQuery query) {
        return readItems(query);
    }

    @Override
    @NotNull
    public Promise<List<MemoryItem>> readItems(@NotNull MemoryQuery query) {
        List<MemoryItem> all = new ArrayList<>();
        episodes.values().stream().filter(e -> matchesQuery(e, query)).forEach(all::add);
        facts.values().stream().filter(f -> matchesQuery(f, query)).forEach(all::add);
        procedures.values().stream().filter(p -> matchesQuery(p, query)).forEach(all::add);
        artifacts.values().stream().filter(a -> matchesQuery(a, query)).forEach(all::add);

        List<MemoryItem> result = all.stream()
                .sorted(Comparator.comparing(MemoryItem::getCreatedAt).reversed())
                .limit(query.getLimit())
                .collect(Collectors.toList());
        return Promise.of(result);
    }

    @Override
    @NotNull
    public Promise<List<ScoredMemoryItem>> searchSemantic(
            @NotNull String query,
            @Nullable List<MemoryItemType> itemTypes,
            int k,
            @Nullable Instant startTime,
            @Nullable Instant endTime) {

        // In-memory: simple string-contains search with uniform scoring
        String lowerQuery = query.toLowerCase();
        List<ScoredMemoryItem> results = new ArrayList<>();

        for (EnhancedEpisode ep : episodes.values()) {
            if (matchesText(ep, lowerQuery) && matchesTimeRange(ep, startTime, endTime)) {
                results.add(new ScoredMemoryItem(ep, 0.8, Map.of("strategy", "text-match")));
            }
        }
        for (EnhancedFact fact : facts.values()) {
            if (matchesText(fact, lowerQuery) && matchesTimeRange(fact, startTime, endTime)) {
                results.add(new ScoredMemoryItem(fact, 0.7, Map.of("strategy", "text-match")));
            }
        }

        List<ScoredMemoryItem> topK = results.stream()
                .sorted(Comparator.comparingDouble(ScoredMemoryItem::getScore).reversed())
                .limit(k)
                .collect(Collectors.toList());

        return Promise.of(topK);
    }

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
        String checkpointId = UUID.randomUUID().toString();
        log.debug("Created checkpoint {} for task {}", checkpointId, taskId);
        return Promise.of(checkpointId);
    }

    @Override
    @NotNull
    public Promise<MemoryPlaneStats> getStats() {
        return Promise.of(MemoryPlaneStats.builder()
                .episodeCount(episodes.size())
                .factCount(facts.size())
                .procedureCount(procedures.size())
                .artifactCount(artifacts.size())
                .build());
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private boolean matchesQuery(MemoryItem item, MemoryQuery query) {
        if (query.getTenantId() != null && !query.getTenantId().equals(item.getTenantId())) return false;
        if (query.getItemTypes() != null && !query.getItemTypes().contains(item.getType())) return false;
        if (query.getStartTime() != null && item.getCreatedAt().isBefore(query.getStartTime())) return false;
        if (query.getEndTime() != null && item.getCreatedAt().isAfter(query.getEndTime())) return false;
        if (query.getMinConfidence() > 0 && item.getValidity().getConfidence() < query.getMinConfidence()) return false;
        return true;
    }

    private boolean matchesTimeRange(MemoryItem item, Instant start, Instant end) {
        if (start != null && item.getCreatedAt().isBefore(start)) return false;
        if (end != null && item.getCreatedAt().isAfter(end)) return false;
        return true;
    }

    private boolean matchesText(EnhancedEpisode ep, String query) {
        return ep.getInput().toLowerCase().contains(query)
                || ep.getOutput().toLowerCase().contains(query);
    }

    private boolean matchesText(EnhancedFact fact, String query) {
        return fact.getSubject().toLowerCase().contains(query)
                || fact.getPredicate().toLowerCase().contains(query)
                || fact.getObject().toLowerCase().contains(query);
    }
}
