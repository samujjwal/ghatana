/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Infrastructure — Agent State Repository
 */
package com.ghatana.yappc.infrastructure.datacloud.repository;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.governance.security.TenantContext;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Data-Cloud-backed repository for YAPPC agent execution state.
 *
 * <p>Persists agent execution records so that:
 * <ul>
 *   <li>In-progress executions survive service restarts</li>
 *   <li>Historical execution records can be queried for observability and debugging</li>
 *   <li>Per-tenant execution isolation is enforced via {@link TenantContext}</li>
 * </ul>
 *
 * <h2>Collection Schema</h2>
 * <pre>
 * agent-executions/{tenantId}/{executionId}:
 *   executionId    : UUID
 *   agentId        : String
 *   agentType      : String
 *   projectId      : String (optional)
 *   status         : PENDING | RUNNING | SUCCEEDED | FAILED | CANCELLED
 *   inputPayload   : String (JSON, optional)
 *   resultPayload  : String (JSON, optional)
 *   errorMessage   : String (optional)
 *   startedAt      : ISO-8601 instant
 *   completedAt    : ISO-8601 instant (optional)
 *   durationMs     : long (optional)
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Durable Data-Cloud persistence for YAPPC agent execution state
 * @doc.layer infrastructure
 * @doc.pattern Repository
 */
public final class AgentStateRepository {

    private static final Logger log = LoggerFactory.getLogger(AgentStateRepository.class);
    private static final String COLLECTION = "agent-executions";

    private final DataCloudClient client;

    public AgentStateRepository(@NotNull DataCloudClient client) {
        this.client = Objects.requireNonNull(client, "DataCloudClient must not be null");
    }

    // ── CRUD ─────────────────────────────────────────────────────────────────

    /**
     * Persists a new agent execution record and returns the assigned execution ID.
     *
     * @param agentId    canonical ID of the agent being executed
     * @param agentType  type/class of the agent (e.g. {@code "PLANNING"})
     * @param projectId  optional project scope
     * @param inputJson  serialised JSON input for the agent
     * @return promise resolving to the generated {@link UUID} for this execution
     */
    public Promise<UUID> create(String agentId, String agentType,
                                String projectId, String inputJson) {
        String tenantId     = resolveTenantId();
        UUID   executionId  = UUID.randomUUID();
        Map<String, Object> doc = new HashMap<>();
        doc.put("executionId",  executionId.toString());
        doc.put("agentId",      agentId);
        doc.put("agentType",    agentType);
        doc.put("projectId",    projectId != null ? projectId : "");
        doc.put("status",       "PENDING");
        doc.put("inputPayload", inputJson != null ? inputJson : "{}");
        doc.put("startedAt",    Instant.now().toString());

        return client.save(tenantId, COLLECTION, doc)
                .map(ignored -> {
                    log.debug("Created agent execution: tenantId={} executionId={} agentId={}",
                            tenantId, executionId, agentId);
                    return executionId;
                });
    }

    /**
     * Marks an execution as {@code RUNNING}.
     */
    public Promise<Void> markRunning(@NotNull UUID executionId) {
        return updateStatus(executionId, "RUNNING", null, null);
    }

    /**
     * Marks an execution as {@code SUCCEEDED} and stores the result payload.
     *
     * @param resultJson serialised JSON result
     */
    public Promise<Void> markSucceeded(@NotNull UUID executionId, String resultJson) {
        return updateStatus(executionId, "SUCCEEDED", resultJson, null);
    }

    /**
     * Marks an execution as {@code FAILED} and records the error message.
     */
    public Promise<Void> markFailed(@NotNull UUID executionId, String errorMessage) {
        return updateStatus(executionId, "FAILED", null, errorMessage);
    }

    /**
     * Marks an execution as {@code CANCELLED}.
     */
    public Promise<Void> markCancelled(@NotNull UUID executionId) {
        return updateStatus(executionId, "CANCELLED", null, null);
    }

    /**
     * Fetches an execution record by ID within the current tenant scope.
     *
     * @return {@code Optional.empty()} if not found or not owned by tenant
     */
    public Promise<Optional<Map<String, Object>>> findById(@NotNull UUID executionId) {
        String tenantId = resolveTenantId();
        return client.findById(tenantId, COLLECTION, executionId.toString())
                .map(opt -> opt.map(DataCloudClient.Entity::data));
    }

    /**
     * Lists execution records for a given agent within the current tenant scope.
     *
     * @param agentId the agent identifier to query
     * @param limit   maximum number of records (most recent first)
     */
    public Promise<List<Map<String, Object>>> findByAgent(String agentId, int limit) {
        String tenantId = resolveTenantId();
        DataCloudClient.Query query = DataCloudClient.Query.builder()
                .filter(DataCloudClient.Filter.eq("agentId", agentId))
                .limit(limit)
                .build();
        return client.query(tenantId, COLLECTION, query)
                .map(entities -> entities.stream()
                        .map(DataCloudClient.Entity::data)
                        .collect(java.util.stream.Collectors.toList()));
    }

    /**
     * Lists all currently {@code RUNNING} executions in the current tenant scope.
     */
    public Promise<List<Map<String, Object>>> findRunning() {
        String tenantId = resolveTenantId();
        DataCloudClient.Query query = DataCloudClient.Query.builder()
                .filter(DataCloudClient.Filter.eq("status", "RUNNING"))
                .limit(100)
                .build();
        return client.query(tenantId, COLLECTION, query)
                .map(entities -> entities.stream()
                        .map(DataCloudClient.Entity::data)
                        .collect(java.util.stream.Collectors.toList()));
    }

    /**
     * Deletes execution records older than the given cutoff (supports data retention).
     *
     * @param before ISO-8601 instant — records with {@code startedAt} before this are deleted
     * @return count of deleted records
     */
    public Promise<Integer> deleteOlderThan(@NotNull Instant before) {
        String tenantId = resolveTenantId();
        log.info("Purging agent execution records before {} for tenant {}", before, tenantId);
        DataCloudClient.Query query = DataCloudClient.Query.builder()
                .filter(DataCloudClient.Filter.lt("startedAt", before.toString()))
                .limit(1000)
                .build();
        return client.query(tenantId, COLLECTION, query)
                .then(entities -> {
                    List<Promise<Void>> deletes = entities.stream()
                            .map(entity -> client.delete(tenantId, COLLECTION, entity.id()))
                            .collect(java.util.stream.Collectors.toList());
                    return io.activej.promise.Promises.all(deletes)
                            .map(ignored -> entities.size());
                });
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private Promise<Void> updateStatus(UUID executionId, String status,
                                       String resultJson, String errorMessage) {
        String tenantId = resolveTenantId();
        return client.findById(tenantId, COLLECTION, executionId.toString())
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException(
                                "Execution not found: " + executionId));
                    }
                    Map<String, Object> updated = new HashMap<>(opt.get().data());
                    updated.put("executionId",  executionId.toString());
                    updated.put("status",        status);
                    updated.put("updatedAt",     Instant.now().toString());
                    if (resultJson   != null) updated.put("resultPayload", resultJson);
                    if (errorMessage != null) updated.put("errorMessage",  errorMessage);
                    return client.save(tenantId, COLLECTION, updated).toVoid();
                })
                .whenComplete((v, ex) -> {
                    if (ex != null) {
                        log.error("Failed to update agent execution {} status to {}: {}",
                                executionId, status, ex.getMessage(), ex);
                    } else {
                        log.debug("Agent execution {} → {}", executionId, status);
                    }
                });
    }

    private static String resolveTenantId() {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null || tenantId.isBlank() || "default-tenant".equals(tenantId)) {
            throw new SecurityException(
                    "AgentStateRepository requires an active tenant context.");
        }
        return tenantId;
    }
}
