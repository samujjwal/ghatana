package com.ghatana.orchestrator.ai.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.orchestrator.ai.AIAgentOrchestrationManager.AgentDefinition;
import com.ghatana.orchestrator.ai.AIAgentOrchestrationManager.AgentExecutionStatus;
import com.ghatana.orchestrator.ai.AIAgentOrchestrationManager.ExecutionState;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.platform.types.identity.Offset;
import io.activej.promise.Promise;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages durable event sourcing for orchestration state mutations.
 *
 * <p>All state mutations (agent registration, chain creation, execution lifecycle)
 * are appended as typed JSON events to the {@link EventLogStore}. On startup,
 * {@link #rebuildFromEventLog()} replays the log to restore in-memory state.
 *
 * @doc.type class
 * @doc.purpose Event sourcing manager for orchestration state durability
 * @doc.layer product
 * @doc.pattern EventSourced
 * @doc.gaa.memory episodic
 * @doc.gaa.lifecycle capture
 */
@Slf4j
public class OrchestrationEventSourcingManager {

    // Event-type constants for the event log
    static final String EVT_AGENT_REGISTERED = "ORCHESTRATION_AGENT_REGISTERED";
    static final String EVT_CHAIN_CREATED = "ORCHESTRATION_CHAIN_CREATED";
    static final String EVT_EXECUTION_STARTED = "ORCHESTRATION_EXECUTION_STARTED";
    static final String EVT_EXECUTION_COMPLETED = "ORCHESTRATION_EXECUTION_COMPLETED";
    static final String EVT_EXECUTION_FAILED = "ORCHESTRATION_EXECUTION_FAILED";
    static final String EVT_EXECUTION_CANCELLED = "ORCHESTRATION_EXECUTION_CANCELLED";

    private static final ObjectMapper MAPPER = JsonUtils.getDefaultMapper();

    private final EventLogStore eventLogStore;
    private final TenantContext systemTenant;

    // References to shared in-memory state (owned by AIAgentOrchestrationManagerImpl)
    private final Map<String, AgentDefinition> agentDefinitions;
    private final Map<String, List<String>> agentChains;
    private final Map<String, AgentExecutionStatus> executionStatuses;
    private final AtomicLong executionIdCounter;
    private final StatusUpdater statusUpdater;

    /**
     * Functional interface for updating execution status in the parent manager.
     */
    @FunctionalInterface
    interface StatusUpdater {
        void update(String executionId, ExecutionState state, double progress, String errorMessage);
    }

    OrchestrationEventSourcingManager(
            EventLogStore eventLogStore,
            TenantContext systemTenant,
            Map<String, AgentDefinition> agentDefinitions,
            Map<String, List<String>> agentChains,
            Map<String, AgentExecutionStatus> executionStatuses,
            AtomicLong executionIdCounter,
            StatusUpdater statusUpdater) {
        this.eventLogStore = eventLogStore;
        this.systemTenant = systemTenant;
        this.agentDefinitions = agentDefinitions;
        this.agentChains = agentChains;
        this.executionStatuses = executionStatuses;
        this.executionIdCounter = executionIdCounter;
        this.statusUpdater = statusUpdater;
    }

    /**
     * Appends an orchestration state-mutation event to the {@link EventLogStore}.
     * Fire-and-forget: failures are logged as warnings and never propagate.
     *
     * @param eventType the event type string (one of the {@code EVT_*} constants)
     * @param payload   key-value data describing the mutation
     */
    void appendStateEvent(String eventType, Map<String, Object> payload) {
        if (eventLogStore == null) {
            return;
        }
        try {
            byte[] payloadBytes = MAPPER.writeValueAsBytes(payload);
            EventLogStore.EventEntry entry = EventLogStore.EventEntry.builder()
                    .eventId(UUID.randomUUID())
                    .eventType(eventType)
                    .eventVersion("1.0.0")
                    .payload(payloadBytes)
                    .contentType("application/json")
                    .build();
            eventLogStore
                    .append(systemTenant, entry)
                    .whenException(
                            e -> log.warn("[EventSource] failed to append {} event: {}", eventType, e.getMessage()));
        } catch (Exception e) {
            log.warn("[EventSource] failed to serialize {} event payload: {}", eventType, e.getMessage());
        }
    }

    /**
     * Replays all orchestration events from the event log to rebuild in-memory state.
     * Call this during application startup before handling any requests.
     *
     * @return a {@link Promise} that completes when the replay is done
     */
    public Promise<Void> rebuildFromEventLog() {
        if (eventLogStore == null) {
            log.info("[EventSource] No EventLogStore configured — skipping replay (in-memory mode)");
            return Promise.complete();
        }
        log.info("[EventSource] Rebuilding orchestration state from event log (tenant={})", systemTenant.tenantId());
        return rebuildFromEventLogBatch(0L, 0, 0L);
    }

    private Promise<Void> rebuildFromEventLogBatch(long offset, int totalReplayed, long maxExecutionId) {
        final int batchSize = 500;
        return eventLogStore.read(systemTenant, Offset.of(offset), batchSize).then(batch -> {
            if (batch == null || batch.isEmpty()) {
                finaliseRebuild(totalReplayed, maxExecutionId);
                return Promise.complete();
            }

            int newReplayed = totalReplayed;
            long newMaxExecId = maxExecutionId;

            for (EventLogStore.EventEntry entry : batch) {
                try {
                    replayEvent(entry);
                    newReplayed++;
                    if (entry.eventType().equals(EVT_EXECUTION_STARTED)) {
                        Map<String, Object> data =
                                MAPPER.readValue(toBytes(entry.payload()), new TypeReference<Map<String, Object>>() {});
                        String execId = (String) data.get("executionId");
                        if (execId != null && execId.startsWith("exec_")) {
                            try {
                                long counter = Long.parseLong(execId.substring(5));
                                if (counter > newMaxExecId) newMaxExecId = counter;
                            } catch (NumberFormatException ignored) {
                                /* non-numeric suffix */
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn(
                            "[EventSource] failed to replay event id={} type={}: {}",
                            entry.eventId(),
                            entry.eventType(),
                            e.getMessage());
                }
            }

            if (batch.size() < batchSize) {
                finaliseRebuild(newReplayed, newMaxExecId);
                return Promise.complete();
            }

            return rebuildFromEventLogBatch(offset + batch.size(), newReplayed, newMaxExecId);
        });
    }

    private void finaliseRebuild(int totalReplayed, long maxExecutionId) {
        if (maxExecutionId > 0) {
            executionIdCounter.set(maxExecutionId);
        }
        log.info(
                "[EventSource] Rebuilt state: {} agents, {} chains, {} executions replayed (total events={})",
                agentDefinitions.size(),
                agentChains.size(),
                executionStatuses.size(),
                totalReplayed);
    }

    @SuppressWarnings("unchecked")
    private void replayEvent(EventLogStore.EventEntry entry) throws Exception {
        byte[] payloadBytes = toBytes(entry.payload());
        Map<String, Object> data = MAPPER.readValue(payloadBytes, new TypeReference<Map<String, Object>>() {});

        switch (entry.eventType()) {
            case EVT_AGENT_REGISTERED -> {
                String agentId = (String) data.get("agentId");
                if (agentId != null && !agentDefinitions.containsKey(agentId)) {
                    String agentName = (String) data.getOrDefault("agentName", agentId);
                    String agentType = (String) data.getOrDefault("agentType", "unknown");
                    AgentDefinition restored = new AgentDefinition(
                            agentId, agentName, null, null, null, null, null, null, Map.of("agentType", agentType));
                    agentDefinitions.put(agentId, restored);
                }
            }
            case EVT_CHAIN_CREATED -> {
                String chainId = (String) data.get("chainId");
                List<String> agentIds = (List<String>) data.get("agentIds");
                if (chainId != null && agentIds != null && !agentChains.containsKey(chainId)) {
                    agentChains.put(chainId, new ArrayList<>(agentIds));
                }
            }
            case EVT_EXECUTION_STARTED -> {
                String executionId = (String) data.get("executionId");
                String chainId = (String) data.get("chainId");
                if (executionId != null && !executionStatuses.containsKey(executionId)) {
                    long ts = ((Number) data.getOrDefault("timestamp", System.currentTimeMillis())).longValue();
                    executionStatuses.put(
                            executionId,
                            new AgentExecutionStatus(
                                    executionId, chainId, ExecutionState.PENDING, 0.0, ts, null, null, Map.of()));
                }
            }
            case EVT_EXECUTION_COMPLETED -> {
                String executionId = (String) data.get("executionId");
                if (executionId != null) {
                    statusUpdater.update(executionId, ExecutionState.COMPLETED, 100.0, null);
                }
            }
            case EVT_EXECUTION_FAILED -> {
                String executionId = (String) data.get("executionId");
                String error = (String) data.getOrDefault("error", "replayed failure");
                if (executionId != null) {
                    statusUpdater.update(executionId, ExecutionState.FAILED, 0.0, error);
                }
            }
            case EVT_EXECUTION_CANCELLED -> {
                String executionId = (String) data.get("executionId");
                if (executionId != null) {
                    statusUpdater.update(executionId, ExecutionState.CANCELLED, 0.0, "Replayed: cancelled by user");
                }
            }
            default -> {
                // Ignore unrecognised event types — forward-compatible
            }
        }
    }

    private static byte[] toBytes(ByteBuffer buf) {
        if (buf == null) return new byte[0];
        byte[] bytes = new byte[buf.remaining()];
        buf.duplicate().get(bytes);
        return bytes;
    }
}
