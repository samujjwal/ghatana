/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.registry;

import com.ghatana.agent.lifecycle.AgentLifecyclePhase;
import com.ghatana.agent.lifecycle.AgentPhaseTrace;
import com.ghatana.agent.lifecycle.AgentTurnTrace;
import com.ghatana.agent.lifecycle.AgentTurnTraceRepository;
import com.ghatana.datacloud.client.DataCloudClient;
import com.ghatana.datacloud.entity.storage.QuerySpecInterface;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Data-Cloud-backed persistence for turn and phase traces.
 */
public final class DataCloudAgentTurnTraceRepository implements AgentTurnTraceRepository {

    public static final String TURN_COLLECTION = "agent-turn-trace";
    public static final String PHASE_COLLECTION = "agent-phase-trace";

    private static final String F_TRACE_ID = "traceId";
    private static final String F_TURN_ID = "turnId";
    private static final String F_AGENT_ID = "agentId";
    private static final String F_STARTED_AT = "startedAt";
    private static final String F_ENDED_AT = "endedAt";
    private static final String F_STATUS = "status";
    private static final String F_METRICS = "metrics";
    private static final String F_PHASE_TRACE_ID = "phaseTraceId";
    private static final String F_PHASE = "phase";
    private static final String F_ERROR = "error";

    private final DataCloudClient dataCloud;
    private final String tenantId;

    public DataCloudAgentTurnTraceRepository(@NotNull DataCloudClient dataCloud, @NotNull String tenantId) {
        this.dataCloud = Objects.requireNonNull(dataCloud, "dataCloud");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
    }

    @Override
    public @NotNull Promise<AgentTurnTrace> save(@NotNull AgentTurnTrace trace) {
        return dataCloud.createEntity(tenantId, TURN_COLLECTION, turnData(trace))
                .then(entity -> savePhases(trace.traceId(), trace.phases(), 0))
                .map(ignored -> trace);
    }

    @Override
    public @NotNull Promise<Optional<AgentTurnTrace>> findByTraceId(@NotNull String traceId) {
        return dataCloud.queryEntities(tenantId, TURN_COLLECTION, fieldEq(F_TRACE_ID, traceId))
                .then(turns -> {
                    if (turns.isEmpty()) {
                        return Promise.of(Optional.empty());
                    }
                    Map<String, Object> turn = turns.getFirst().getData();
                    return dataCloud.queryEntities(tenantId, PHASE_COLLECTION, fieldEq(F_TRACE_ID, traceId))
                            .map(phases -> Optional.of(fromDataMap(turn,
                                    phases.stream().map(e -> phaseFromData(e.getData())).toList())));
                });
    }

    @Override
    public @NotNull Promise<List<AgentTurnTrace>> findByAgent(@NotNull String agentId) {
        return dataCloud.queryEntities(tenantId, TURN_COLLECTION, fieldEq(F_AGENT_ID, agentId))
                .then(turns -> {
                    List<Map<String, Object>> turnData = turns.stream().map(e -> e.getData()).toList();
                    if (turnData.isEmpty()) {
                        return Promise.of(List.of());
                    }
                    return collectTurns(turnData, 0, new java.util.ArrayList<>());
                });
    }

    private Promise<Void> savePhases(String traceId, List<AgentPhaseTrace> phases, int index) {
        if (index >= phases.size()) {
            return Promise.complete();
        }
        return dataCloud.createEntity(tenantId, PHASE_COLLECTION, phaseData(traceId, phases.get(index)))
                .then(entity -> savePhases(traceId, phases, index + 1));
    }

    private Promise<List<AgentTurnTrace>> collectTurns(
            List<Map<String, Object>> turns,
            int index,
            List<AgentTurnTrace> collected) {
        if (index >= turns.size()) {
            return Promise.of(collected);
        }
        Map<String, Object> turn = turns.get(index);
        String traceId = str(turn, F_TRACE_ID);
        return dataCloud.queryEntities(tenantId, PHASE_COLLECTION, fieldEq(F_TRACE_ID, traceId))
                .then(phases -> {
                    collected.add(fromDataMap(turn,
                            phases.stream().map(e -> phaseFromData(e.getData())).toList()));
                    return collectTurns(turns, index + 1, collected);
                });
    }

    private static Map<String, Object> turnData(AgentTurnTrace trace) {
        Map<String, Object> data = new HashMap<>();
        data.put(F_TRACE_ID, trace.traceId());
        data.put(F_TURN_ID, trace.turnId());
        data.put(F_AGENT_ID, trace.agentId());
        data.put(F_STARTED_AT, trace.startedAt().toString());
        data.put(F_ENDED_AT, trace.endedAt().toString());
        data.put(F_STATUS, trace.status());
        data.put(F_METRICS, trace.metrics());
        return data;
    }

    private static Map<String, Object> phaseData(String traceId, AgentPhaseTrace phase) {
        Map<String, Object> data = new HashMap<>();
        data.put(F_PHASE_TRACE_ID, phase.phaseTraceId());
        data.put(F_TRACE_ID, traceId);
        data.put(F_PHASE, phase.phase().name());
        data.put(F_STARTED_AT, phase.startedAt().toString());
        data.put(F_ENDED_AT, phase.endedAt().toString());
        data.put(F_STATUS, phase.status());
        data.put(F_ERROR, phase.error());
        data.put(F_METRICS, phase.metrics());
        return data;
    }

    @SuppressWarnings("unchecked")
    private static AgentTurnTrace fromDataMap(Map<String, Object> data, List<AgentPhaseTrace> phases) {
        return new AgentTurnTrace(
                str(data, F_TRACE_ID),
                str(data, F_TURN_ID),
                str(data, F_AGENT_ID),
                Instant.parse(str(data, F_STARTED_AT)),
                Instant.parse(str(data, F_ENDED_AT)),
                str(data, F_STATUS),
                phases,
                (Map<String, Object>) data.getOrDefault(F_METRICS, Map.of()));
    }

    @SuppressWarnings("unchecked")
    private static AgentPhaseTrace phaseFromData(Map<String, Object> data) {
        return new AgentPhaseTrace(
                str(data, F_PHASE_TRACE_ID),
                AgentLifecyclePhase.valueOf(str(data, F_PHASE)),
                Instant.parse(str(data, F_STARTED_AT)),
                Instant.parse(str(data, F_ENDED_AT)),
                str(data, F_STATUS),
                str(data, F_ERROR),
                (Map<String, Object>) data.getOrDefault(F_METRICS, Map.of()));
    }

    private static QuerySpecInterface fieldEq(String field, String value) {
        return new QuerySpecInterface() {
            private String f = field + " = '" + value + "'";
            private Integer limit = 1000;
            private Integer offset = 0;
            @Override public String getFilter() { return f; }
            @Override public void setFilter(String filter) { this.f = filter; }
            @Override public Integer getLimit() { return limit; }
            @Override public void setLimit(Integer limit) { this.limit = limit; }
            @Override public Integer getOffset() { return offset; }
            @Override public void setOffset(Integer offset) { this.offset = offset; }
            @Override public String getQueryType() { return "filter"; }
            @Override public void setQueryType(String queryType) { /* no-op */ }
        };
    }

    private static String str(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
