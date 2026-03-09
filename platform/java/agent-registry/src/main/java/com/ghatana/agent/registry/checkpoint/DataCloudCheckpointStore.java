/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.ghatana.agent.registry.checkpoint;

import com.ghatana.agent.framework.checkpoint.AgentCheckpoint;
import com.ghatana.agent.framework.checkpoint.AgentCheckpointStore;
import com.ghatana.datacloud.client.DataCloudClient;
import com.ghatana.datacloud.entity.storage.QuerySpecInterface;
import com.ghatana.datacloud.entity.storage.FilterCriteria;
import com.ghatana.datacloud.entity.storage.SortSpec;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * {@link AgentCheckpointStore} implementation backed by Data-Cloud.
 *
 * <p>Persists checkpoints as entities in a dedicated {@code agent-checkpoints}
 * collection within Data-Cloud. Each checkpoint is stored as an entity record
 * with the checkpoint metadata serialised into the entity data map. The opaque
 * {@code statePayload} is Base64-encoded for safe JSON storage.
 *
 * <h2>Data-Cloud Collection</h2>
 * <pre>
 * Collection: agent-checkpoints
 * Fields:
 *   checkpointId       — unique checkpoint ID
 *   agentId            — owning agent
 *   executionId        — execution/turn
 *   sequenceNumber     — ordering within execution
 *   statePayload       — Base64-encoded opaque state
 *   payloadContentType — MIME type of state
 *   metadata           — arbitrary key-value pairs
 *   terminal           — whether this is the final checkpoint
 *   createdAt          — ISO-8601 timestamp
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Data-Cloud-backed checkpoint persistence
 * @doc.layer registry
 * @doc.pattern Repository
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 */
public class DataCloudCheckpointStore implements AgentCheckpointStore {

    private static final Logger log = LoggerFactory.getLogger(DataCloudCheckpointStore.class);
    private static final String COLLECTION = "agent-checkpoints";

    private final DataCloudClient dataCloud;
    private final String tenantId;

    public DataCloudCheckpointStore(@NotNull DataCloudClient dataCloud,
                                     @NotNull String tenantId) {
        this.dataCloud = Objects.requireNonNull(dataCloud, "dataCloud");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Save
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    @NotNull
    public Promise<Void> save(@NotNull AgentCheckpoint checkpoint) {
        Map<String, Object> data = toMap(checkpoint);

        return dataCloud.createEntity(tenantId, COLLECTION, data)
                .map(entity -> {
                    log.debug("Saved checkpoint {} for agent={} exec={} seq={}",
                            checkpoint.getCheckpointId(),
                            checkpoint.getAgentId(),
                            checkpoint.getExecutionId(),
                            checkpoint.getSequenceNumber());
                    return (Void) null;
                });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Load
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    @NotNull
    public Promise<Optional<AgentCheckpoint>> loadLatest(@NotNull String agentId,
                                                          @NotNull String executionId) {
        return loadAll(agentId, executionId)
                .map(list -> list.isEmpty()
                        ? Optional.empty()
                        : Optional.of(list.get(list.size() - 1)));
    }

    @Override
    @NotNull
    public Promise<List<AgentCheckpoint>> loadAll(@NotNull String agentId,
                                                    @NotNull String executionId) {
        CheckpointQuery query = new CheckpointQuery();
        query.setFilters(Map.of("agentId", agentId, "executionId", executionId));
        query.setSortFields(List.of("sequenceNumber"));
        query.setLimit(1000);

        return dataCloud.queryEntities(tenantId, COLLECTION, query)
                .map(entities -> entities.stream()
                        .map(e -> fromMap(e.getData()))
                        .filter(Objects::nonNull)
                        .sorted(Comparator.comparingLong(AgentCheckpoint::getSequenceNumber))
                        .collect(Collectors.toList()));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Delete
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    @NotNull
    public Promise<Void> delete(@NotNull String checkpointId) {
        CheckpointQuery query = new CheckpointQuery();
        query.setFilters(Map.of("checkpointId", checkpointId));
        query.setLimit(1);

        return dataCloud.queryEntities(tenantId, COLLECTION, query)
                .then(entities -> {
                    if (entities.isEmpty()) {
                        return Promise.complete();
                    }
                    return dataCloud.deleteEntity(tenantId, COLLECTION, entities.get(0).getId());
                });
    }

    @Override
    @NotNull
    public Promise<Void> deleteByExecution(@NotNull String agentId,
                                            @NotNull String executionId) {
        CheckpointQuery query = new CheckpointQuery();
        query.setFilters(Map.of("agentId", agentId, "executionId", executionId));
        query.setLimit(1000);

        return dataCloud.queryEntities(tenantId, COLLECTION, query)
                .then(entities -> {
                    if (entities.isEmpty()) {
                        return Promise.complete();
                    }
                    List<UUID> ids = entities.stream()
                            .map(e -> e.getId())
                            .collect(Collectors.toList());
                    return dataCloud.bulkDeleteEntities(tenantId, COLLECTION, ids)
                            .map(count -> (Void) null);
                });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Serialisation
    // ═══════════════════════════════════════════════════════════════════════════

    private static Map<String, Object> toMap(AgentCheckpoint cp) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("checkpointId", cp.getCheckpointId());
        map.put("agentId", cp.getAgentId());
        map.put("executionId", cp.getExecutionId());
        map.put("sequenceNumber", cp.getSequenceNumber());
        map.put("createdAt", cp.getCreatedAt().toString());
        map.put("payloadContentType", cp.getPayloadContentType());
        map.put("terminal", cp.isTerminal());
        map.put("metadata", cp.getMetadata());

        if (cp.getStatePayload() != null) {
            map.put("statePayload", Base64.getEncoder().encodeToString(cp.getStatePayload()));
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private static AgentCheckpoint fromMap(Map<String, Object> data) {
        if (data == null || !data.containsKey("checkpointId")) {
            return null;
        }
        byte[] payload = null;
        Object payloadObj = data.get("statePayload");
        if (payloadObj instanceof String encoded) {
            payload = Base64.getDecoder().decode(encoded);
        }

        Object seqObj = data.get("sequenceNumber");
        long seq = seqObj instanceof Number n ? n.longValue() : 0L;

        Object termObj = data.get("terminal");
        boolean terminal = termObj instanceof Boolean b ? b : false;

        Map<String, String> metadata = Map.of();
        Object metaObj = data.get("metadata");
        if (metaObj instanceof Map<?, ?> m) {
            Map<String, String> parsed = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : m.entrySet()) {
                parsed.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
            metadata = parsed;
        }

        return AgentCheckpoint.builder()
                .checkpointId((String) data.get("checkpointId"))
                .agentId((String) data.get("agentId"))
                .executionId((String) data.get("executionId"))
                .sequenceNumber(seq)
                .createdAt(data.containsKey("createdAt")
                        ? Instant.parse((String) data.get("createdAt"))
                        : Instant.now())
                .statePayload(payload)
                .payloadContentType((String) data.getOrDefault("payloadContentType", "application/json"))
                .metadata(metadata)
                .terminal(terminal)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Inner QuerySpec implementation
    // ═══════════════════════════════════════════════════════════════════════════

    private static class CheckpointQuery implements QuerySpecInterface {
        private Map<String, Object> filters = Map.of();
        private List<String> sortFields = List.of();
        private Integer limit = 100;
        private Integer offset = 0;
        private String queryType;
        private String filter;

        public Map<String, Object> getFilters() { return filters; }
        public void setFilters(Map<String, Object> filters) { this.filters = filters; }
        public List<String> getSortFields() { return sortFields; }
        public void setSortFields(List<String> sortFields) { this.sortFields = sortFields; }
        @Override public Integer getLimit() { return limit; }
        @Override public void setLimit(Integer limit) { this.limit = limit; }
        @Override public Integer getOffset() { return offset; }
        @Override public void setOffset(Integer offset) { this.offset = offset; }
        @Override public String getQueryType() { return queryType; }
        @Override public void setQueryType(String queryType) { this.queryType = queryType; }
        @Override public String getFilter() { return filter; }
        @Override public void setFilter(String filter) { this.filter = filter; }
    }
}
