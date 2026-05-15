package com.ghatana.aep.engine.registry;

import com.ghatana.agent.mastery.MasteryRegistry;
import com.ghatana.agent.memory.model.MemoryItem;
import com.ghatana.agent.memory.model.episode.EnhancedEpisode;
import com.ghatana.agent.memory.model.fact.EnhancedFact;
import com.ghatana.agent.memory.model.procedure.EnhancedProcedure;
import com.ghatana.agent.memory.model.taskstate.TaskBlocker;
import com.ghatana.agent.memory.model.taskstate.TaskCheckpoint;
import com.ghatana.agent.memory.model.taskstate.TaskState;
import com.ghatana.agent.memory.store.MemoryPlane;
import com.ghatana.agent.memory.store.MemoryPlaneStats;
import com.ghatana.agent.memory.store.MemoryQuery;
import com.ghatana.agent.memory.store.ScoredMemoryItem;
import com.ghatana.agent.memory.store.taskstate.ReconcileResult;
import com.ghatana.agent.memory.store.taskstate.TaskStateStore;
import com.ghatana.agent.memory.model.working.BoundedWorkingMemory;
import com.ghatana.agent.memory.model.working.WorkingMemory;
import com.ghatana.agent.memory.model.working.WorkingMemoryConfig;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Thin facade over {@link MemoryPlane} for registry-facing agent memory access.
 *
 * <p>Provides mastery-aware memory retrieval when a {@link MasteryRegistry} is configured.
 *
 * @doc.type class
 * @doc.purpose Adapt the agent memory plane to AgentExecutionService memory responses
 * @doc.layer product
 * @doc.pattern Facade
 */
public class AgentMemoryPlaneClient {

    private final MemoryPlane memoryPlane;
    private final String tenantId;
    @Nullable
    private final MasteryRegistry masteryRegistry;

    public AgentMemoryPlaneClient(@NotNull MemoryPlane memoryPlane) {
        this(memoryPlane, "default", null);
    }

    public AgentMemoryPlaneClient(@NotNull MemoryPlane memoryPlane, @NotNull String tenantId) {
        this(memoryPlane, tenantId, null);
    }

    public AgentMemoryPlaneClient(
            @NotNull MemoryPlane memoryPlane,
            @NotNull String tenantId,
            @Nullable MasteryRegistry masteryRegistry) {
        this.memoryPlane = Objects.requireNonNull(memoryPlane, "memoryPlane");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.masteryRegistry = masteryRegistry;
    }

    public Promise<Void> recordExecution(
            String agentId,
            String executionId,
            Object input,
            Object output,
            long durationMs) {
        EnhancedEpisode episode = EnhancedEpisode.builder()
            .id(executionId)
            .tenantId(tenantId)
            .agentId(agentId)
            .turnId(executionId)
            .timestamp(Instant.now())
            .input(String.valueOf(input))
            .output(String.valueOf(output))
            .latencyMs(durationMs)
            .context(Map.of("executionId", executionId))
            .build();
        return memoryPlane.storeEpisode(episode).map(ignored -> null);
    }

    public Promise<AgentExecutionService.AgentMemory> getMemory(String agentId) {
        MemoryQuery query = MemoryQuery.builder()
            .tenantId(tenantId)
            .agentId(agentId)
            .limit(25)
            .build();

        return memoryPlane.queryEpisodes(query)
            .then(episodes -> memoryPlane.queryFacts(query)
                .then(facts -> memoryPlane.queryProcedures(query)
                    .map(procedures -> toMemoryResponse(episodes, facts, procedures))));
    }

    /**
     * Mastery-aware memory retrieval that filters by mastery state when MasteryRegistry is configured.
     * Excludes obsolete, retired, and maintenance-only items by default unless explicitly requested.
     */
    public Promise<AgentExecutionService.AgentMemory> getMemoryMasteryAware(
            String agentId,
            boolean includeObsolete,
            boolean includeMaintenanceOnly,
            boolean includeNegativeKnowledge) {
        MemoryQuery query = MemoryQuery.builder()
            .tenantId(tenantId)
            .agentId(agentId)
            .limit(25)
            .includeObsolete(includeObsolete)
            .includeMaintenanceOnly(includeMaintenanceOnly)
            .includeNegativeKnowledge(includeNegativeKnowledge)
            .build();

        return memoryPlane.queryEpisodes(query)
            .then(episodes -> memoryPlane.queryFacts(query)
                .then(facts -> memoryPlane.queryProcedures(query)
                    .map(procedures -> {
                        if (masteryRegistry != null) {
                            // Apply mastery-aware filtering
                            procedures = filterByMasteryState(procedures, includeObsolete, includeMaintenanceOnly);
                        }
                        return toMemoryResponse(episodes, facts, procedures);
                    })));
    }

    /**
     * Queries canonical memory items and applies mastery-state filtering.
     *
     * <p>This method is used by governed runtime retrieval so the canonical runtime
     * MemoryPlane can back mastery-aware retrieval without legacy projection bridges.
     */
    public Promise<List<MemoryItem>> queryMemoryItemsMasteryAware(
            @NotNull String agentId,
            @NotNull String skillId,
            int limit,
            boolean includeObsolete,
            boolean includeMaintenanceOnly,
            boolean includeNegativeKnowledge) {
        return queryMemoryItemsMasteryAware(
            this.tenantId,
            agentId,
            skillId,
            limit,
            includeObsolete,
            includeMaintenanceOnly,
            includeNegativeKnowledge);
        }

        /**
         * Queries canonical memory items and applies mastery-state filtering for an explicit tenant.
         *
         * <p>This overload is used by governed dispatch to preserve request-tenant isolation.
         */
        public Promise<List<MemoryItem>> queryMemoryItemsMasteryAware(
            @NotNull String tenantId,
            @NotNull String agentId,
            @NotNull String skillId,
            int limit,
            boolean includeObsolete,
            boolean includeMaintenanceOnly,
            boolean includeNegativeKnowledge) {
        MemoryQuery query = MemoryQuery.builder()
            .tenantId(tenantId)
                .agentId(agentId)
                .skillId(skillId)
                .limit(limit)
                .includeObsolete(includeObsolete)
                .includeMaintenanceOnly(includeMaintenanceOnly)
                .includeNegativeKnowledge(includeNegativeKnowledge)
                .build();

        return memoryPlane.query(query)
                .map(items -> {
                    if (masteryRegistry == null) {
                        return items;
                    }
                    return items.stream()
                            .filter(item -> {
                                String masteryState = item.getLabels().get("masteryState");
                                if (!includeObsolete
                                        && ("OBSOLETE".equals(masteryState) || "RETIRED".equals(masteryState))) {
                                    return false;
                                }
                                if (!includeMaintenanceOnly && "MAINTENANCE_ONLY".equals(masteryState)) {
                                    return false;
                                }
                                return true;
                            })
                            .toList();
                });
    }

    /**
     * Filters procedures by mastery state based on the include flags.
     */
    private List<EnhancedProcedure> filterByMasteryState(
            List<EnhancedProcedure> procedures,
            boolean includeObsolete,
            boolean includeMaintenanceOnly) {
        return procedures.stream()
                .filter(procedure -> {
                    String masteryState = procedure.getLabels().get("masteryState");
                    
                    // Filter out obsolete/retired unless explicitly requested
                    if (!includeObsolete 
                            && ("OBSOLETE".equals(masteryState) || "RETIRED".equals(masteryState))) {
                        return false;
                    }
                    
                    // Filter out maintenance-only unless explicitly requested
                    if (!includeMaintenanceOnly && "MAINTENANCE_ONLY".equals(masteryState)) {
                        return false;
                    }
                    
                    return true;
                })
                .toList();
    }

    private AgentExecutionService.AgentMemory toMemoryResponse(
            List<EnhancedEpisode> episodes,
            List<EnhancedFact> facts,
            List<EnhancedProcedure> procedures) {
        List<Object> episodic = episodes.stream()
            .map(episode -> Map.<String, Object>of(
                "id", episode.getId(),
                "timestamp", episode.getTimestamp().toString(),
                "input", episode.getInput(),
                "output", episode.getOutput(),
                "latencyMs", episode.getLatencyMs()))
            .map(item -> (Object) item)
            .toList();

        Map<String, Object> semantic = new LinkedHashMap<>();
        semantic.put("count", facts.size());
        semantic.put("facts", facts.stream()
            .map(this::summarizeFact)
            .toList());

        Map<String, Object> procedural = new LinkedHashMap<>();
        procedural.put("count", procedures.size());
        procedural.put("procedures", procedures.stream()
            .map(this::summarizeProcedure)
            .toList());

        return new AgentExecutionService.AgentMemory(
            episodic,
            semantic,
            procedural,
            Instant.now().toString());
    }

    private Map<String, Object> summarizeFact(EnhancedFact fact) {
        return Map.of(
            "id", fact.getId(),
            "subject", fact.getSubject(),
            "predicate", fact.getPredicate(),
            "object", fact.getObject(),
            "confidence", fact.getConfidence());
    }

    private Map<String, Object> summarizeProcedure(EnhancedProcedure procedure) {
        return Map.of(
            "id", procedure.getId(),
            "situation", procedure.getSituation(),
            "action", procedure.getAction(),
            "confidence", procedure.getConfidence());
    }

    public static AgentExecutionService.AgentMemory emptyMemory() {
        return new AgentExecutionService.AgentMemory(List.of(), Map.of(), Map.of(), Instant.now().toString());
    }

    public static final class Noop extends AgentMemoryPlaneClient {

        public Noop() {
            super(new NoopMemoryPlane());
        }

        @Override
        public Promise<Void> recordExecution(String agentId, String executionId, Object input, Object output, long durationMs) {
            return Promise.complete();
        }

        @Override
        public Promise<AgentExecutionService.AgentMemory> getMemory(String agentId) {
            return Promise.of(emptyMemory());
        }
    }

    private static final class NoopMemoryPlane implements MemoryPlane {
        @Override public Promise<EnhancedEpisode> storeEpisode(@NotNull EnhancedEpisode episode) { return Promise.of(episode); }
        @Override public Promise<List<EnhancedEpisode>> queryEpisodes(@NotNull MemoryQuery query) { return Promise.of(List.of()); }
        @Override public Promise<EnhancedFact> storeFact(@NotNull EnhancedFact fact) { return Promise.of(fact); }
        @Override public Promise<List<EnhancedFact>> queryFacts(@NotNull MemoryQuery query) { return Promise.of(List.of()); }
        @Override public Promise<EnhancedProcedure> storeProcedure(@NotNull EnhancedProcedure procedure) { return Promise.of(procedure); }
        @Override public Promise<List<EnhancedProcedure>> queryProcedures(@NotNull MemoryQuery query) { return Promise.of(List.of()); }
        @Override public Promise<EnhancedProcedure> getProcedure(@NotNull String procedureId) { return Promise.of(null); }
        @Override public Promise<com.ghatana.agent.memory.model.artifact.TypedArtifact> writeArtifact(@NotNull com.ghatana.agent.memory.model.artifact.TypedArtifact artifact) { return Promise.of(artifact); }
        @Override public Promise<MemoryItem> store(@NotNull MemoryItem item) { return Promise.of(item); }
        @Override public Promise<List<MemoryItem>> query(@NotNull MemoryQuery query) { return Promise.of(List.of()); }
        @Override public Promise<List<MemoryItem>> readItems(@NotNull MemoryQuery query) { return Promise.of(List.of()); }
        @Override public Promise<List<ScoredMemoryItem>> searchSemantic(@NotNull String query, @Nullable List<com.ghatana.agent.memory.model.MemoryItemType> itemTypes, int k, @Nullable Instant startTime, @Nullable Instant endTime) { return Promise.of(List.of()); }
        @Override public WorkingMemory getWorkingMemory() { return new BoundedWorkingMemory(WorkingMemoryConfig.builder().maxEntries(10).build()); }
        @Override public TaskStateStore getTaskStateStore() { return new NoopTaskStateStore(); }
        @Override public Promise<String> checkpoint(@NotNull String taskId) { return Promise.of(taskId); }
        @Override public Promise<MemoryPlaneStats> getStats() { return Promise.of(MemoryPlaneStats.builder().build()); }
    }

    private static final class NoopTaskStateStore implements TaskStateStore {
        @Override public Promise<TaskState> createTask(@NotNull TaskState task) { return Promise.of(task); }
        @Override public Promise<@Nullable TaskState> getTask(@NotNull String taskId) { return Promise.of(null); }
        @Override public Promise<TaskState> updatePhase(@NotNull String taskId, @NotNull String phaseId, @NotNull String status) { return Promise.ofException(new IllegalStateException("No persistent TaskStateStore is configured — wire a DataCloud-backed TaskStateStore implementation via AgentMemoryPlane")); }
        @Override public Promise<TaskCheckpoint> addCheckpoint(@NotNull String taskId, @NotNull TaskCheckpoint checkpoint) { return Promise.of(checkpoint); }
        @Override public Promise<TaskBlocker> reportBlocker(@NotNull String taskId, @NotNull TaskBlocker blocker) { return Promise.of(blocker); }
        @Override public Promise<TaskBlocker> resolveBlocker(@NotNull String taskId, @NotNull String blockerId, @NotNull String resolution) { return Promise.ofException(new IllegalStateException("No persistent TaskStateStore is configured — wire a DataCloud-backed TaskStateStore implementation via AgentMemoryPlane")); }
        @Override public Promise<ReconcileResult> reconcileOnResume(@NotNull String taskId) { return Promise.ofException(new IllegalStateException("No persistent TaskStateStore is configured — wire a DataCloud-backed TaskStateStore implementation via AgentMemoryPlane")); }
        @Override public Promise<Void> archiveTask(@NotNull String taskId) { return Promise.complete(); }
        @Override public Promise<List<TaskState>> listActiveTasks(@NotNull String agentId) { return Promise.of(List.of()); }
        @Override public Promise<Integer> garbageCollect(@NotNull Instant inactiveSince) { return Promise.of(0); }
    }
}
