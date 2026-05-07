/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.registry;

import com.ghatana.agent.release.rollout.AgentRolloutApprovalState;
import com.ghatana.agent.release.rollout.AgentRolloutRecord;
import com.ghatana.agent.release.rollout.AgentRolloutRepository;
import com.ghatana.datacloud.client.DataCloudClient;
import com.ghatana.datacloud.entity.storage.QuerySpecInterface;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Data-Cloud-backed implementation of {@link AgentRolloutRepository}.
 *
 * <p>Each {@link AgentRolloutRecord} is persisted as an entity in the
 * {@value #COLLECTION} Data-Cloud collection.
 *
 * <p>All public methods are ActiveJ async and must not block the event loop.
 *
 * @doc.type class
 * @doc.purpose Data-Cloud-backed persistence for agent rollout records
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class DataCloudAgentRolloutRepository implements AgentRolloutRepository {

    private static final Logger log = LoggerFactory.getLogger(DataCloudAgentRolloutRepository.class);

    /** Data-Cloud collection name for agent rollouts. */
    public static final String COLLECTION = "agent-rollouts";

    // ─── field name constants ─────────────────────────────────────────────────
    private static final String F_ROLLOUT_ID           = "rolloutId";
    private static final String F_AGENT_RELEASE_ID     = "agentReleaseId";
    private static final String F_TENANT_ID            = "tenantId";
    private static final String F_TARGET_ENVIRONMENT   = "targetEnvironment";
    private static final String F_TRAFFIC_SPLIT        = "trafficSplitPercent";
    private static final String F_FALLBACK_RELEASE_ID  = "fallbackReleaseId";
    private static final String F_APPROVAL_STATE       = "approvalState";
    private static final String F_REQUESTED_BY         = "requestedBy";
    private static final String F_APPROVED_BY          = "approvedBy";
    private static final String F_REJECTED_BY          = "rejectedBy";
    private static final String F_REJECTED_REASON      = "rejectedReason";
    private static final String F_KILL_SWITCH          = "killSwitch";
    private static final String F_REQUESTED_AT         = "requestedAt";
    private static final String F_DECIDED_AT           = "decidedAt";
    private static final String F_EXPIRES_AT           = "expiresAt";

    private final DataCloudClient dataCloud;
    private final String tenantId;

    /**
     * Constructs a repository for a given tenant.
     *
     * @param dataCloud the Data Cloud client
     * @param tenantId  the tenant scoping all operations
     */
    public DataCloudAgentRolloutRepository(DataCloudClient dataCloud, String tenantId) {
        this.dataCloud = Objects.requireNonNull(dataCloud, "dataCloud");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
    }

    @Override
    public Promise<AgentRolloutRecord> save(AgentRolloutRecord record) {
        Objects.requireNonNull(record, "record");
        log.debug("Saving rollout record [{}] for agent release [{}]",
                record.rolloutId(), record.agentReleaseId());

        return dataCloud.createEntity(tenantId, COLLECTION, toDataMap(record))
                .map(entity -> record);
    }

    @Override
    public Promise<Optional<AgentRolloutRecord>> findById(String rolloutId) {
        Objects.requireNonNull(rolloutId, "rolloutId");

        QuerySpecInterface query = buildFieldEqQuery(F_ROLLOUT_ID, rolloutId);
        return dataCloud.queryEntities(tenantId, COLLECTION, query)
                .map(entities -> entities.stream()
                        .findFirst()
                        .map(e -> fromDataMap(e.getData())));
    }

    @Override
    public Promise<List<AgentRolloutRecord>> findByReleaseId(String agentReleaseId) {
        Objects.requireNonNull(agentReleaseId, "agentReleaseId");

        QuerySpecInterface query = buildFieldEqQuery(F_AGENT_RELEASE_ID, agentReleaseId);
        return dataCloud.queryEntities(tenantId, COLLECTION, query)
                .map(entities -> entities.stream()
                        .map(e -> fromDataMap(e.getData()))
                        .collect(Collectors.toList()));
    }

    @Override
    public Promise<List<AgentRolloutRecord>> findByTenantAndEnvironment(
            String tenantId, String targetEnvironment) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(targetEnvironment, "targetEnvironment");

        QuerySpecInterface query = buildTwoFieldEqQuery(
                F_TENANT_ID, tenantId,
                F_TARGET_ENVIRONMENT, targetEnvironment);
        return dataCloud.queryEntities(this.tenantId, COLLECTION, query)
                .map(entities -> entities.stream()
                        .map(e -> fromDataMap(e.getData()))
                        .collect(Collectors.toList()));
    }

    @Override
    public Promise<AgentRolloutRecord> approve(String rolloutId, String approvedBy) {
        Objects.requireNonNull(rolloutId, "rolloutId");
        Objects.requireNonNull(approvedBy, "approvedBy");

        return findById(rolloutId).then(optRecord -> {
            if (optRecord.isEmpty()) {
                return Promise.ofException(new IllegalStateException(
                        "RolloutRecord not found: " + rolloutId));
            }
            AgentRolloutRecord existing = optRecord.get();
            if (!existing.approvalState().isPending()) {
                return Promise.ofException(new IllegalStateException(
                        "Cannot approve rollout in state " + existing.approvalState()
                        + "; must be PENDING. rolloutId=" + rolloutId));
            }
            return save(existing.withApproved(approvedBy, Instant.now()));
        });
    }

    @Override
    public Promise<AgentRolloutRecord> reject(String rolloutId, String rejectedBy, String reason) {
        Objects.requireNonNull(rolloutId, "rolloutId");
        Objects.requireNonNull(rejectedBy, "rejectedBy");
        Objects.requireNonNull(reason, "reason");

        return findById(rolloutId).then(optRecord -> {
            if (optRecord.isEmpty()) {
                return Promise.ofException(new IllegalStateException(
                        "RolloutRecord not found: " + rolloutId));
            }
            AgentRolloutRecord existing = optRecord.get();
            if (!existing.approvalState().isPending()) {
                return Promise.ofException(new IllegalStateException(
                        "Cannot reject rollout in state " + existing.approvalState()
                        + "; must be PENDING. rolloutId=" + rolloutId));
            }
            return save(existing.withRejected(rejectedBy, reason, Instant.now()));
        });
    }

    @Override
    public Promise<AgentRolloutRecord> rollback(String rolloutId, String rolledBackBy) {
        Objects.requireNonNull(rolloutId, "rolloutId");
        Objects.requireNonNull(rolledBackBy, "rolledBackBy");

        return findById(rolloutId).then(optRecord -> {
            if (optRecord.isEmpty()) {
                return Promise.ofException(new IllegalStateException(
                        "RolloutRecord not found: " + rolloutId));
            }
            AgentRolloutRecord existing = optRecord.get();
            if (existing.approvalState() != AgentRolloutApprovalState.APPROVED) {
                return Promise.ofException(new IllegalStateException(
                        "Cannot rollback rollout in state " + existing.approvalState()
                        + "; must be APPROVED. rolloutId=" + rolloutId));
            }
            return save(existing.withRolledBack(rolledBackBy, Instant.now()));
        });
    }

    // ─── Serialization helpers ────────────────────────────────────────────────

    private static Map<String, Object> toDataMap(AgentRolloutRecord r) {
        Map<String, Object> m = new HashMap<>();
        m.put(F_ROLLOUT_ID,          r.rolloutId());
        m.put(F_AGENT_RELEASE_ID,    r.agentReleaseId());
        m.put(F_TENANT_ID,           r.tenantId());
        m.put(F_TARGET_ENVIRONMENT,  r.targetEnvironment());
        m.put(F_TRAFFIC_SPLIT,       r.trafficSplitPercent());
        m.put(F_APPROVAL_STATE,      r.approvalState().name());
        m.put(F_REQUESTED_BY,        r.requestedBy());
        m.put(F_KILL_SWITCH,         r.killSwitch());
        m.put(F_REQUESTED_AT,        r.requestedAt().toString());
        putIfNonNull(m, F_FALLBACK_RELEASE_ID, r.fallbackReleaseId());
        putIfNonNull(m, F_APPROVED_BY,         r.approvedBy());
        putIfNonNull(m, F_REJECTED_BY,         r.rejectedBy());
        putIfNonNull(m, F_REJECTED_REASON,     r.rejectedReason());
        putIfNonNull(m, F_DECIDED_AT,          r.decidedAt() != null ? r.decidedAt().toString() : null);
        putIfNonNull(m, F_EXPIRES_AT,          r.expiresAt() != null ? r.expiresAt().toString() : null);
        return m;
    }

    private static AgentRolloutRecord fromDataMap(Map<String, Object> m) {
        String decidedAtStr = str(m, F_DECIDED_AT);
        String expiresAtStr = str(m, F_EXPIRES_AT);

        return new AgentRolloutRecord(
                str(m, F_ROLLOUT_ID),
                str(m, F_AGENT_RELEASE_ID),
                str(m, F_TENANT_ID),
                str(m, F_TARGET_ENVIRONMENT),
                intVal(m, F_TRAFFIC_SPLIT),
                str(m, F_FALLBACK_RELEASE_ID),
                AgentRolloutApprovalState.valueOf(str(m, F_APPROVAL_STATE)),
                str(m, F_REQUESTED_BY),
                str(m, F_APPROVED_BY),
                str(m, F_REJECTED_BY),
                str(m, F_REJECTED_REASON),
                boolVal(m, F_KILL_SWITCH),
                Instant.parse(str(m, F_REQUESTED_AT)),
                decidedAtStr != null ? Instant.parse(decidedAtStr) : null,
                expiresAtStr != null ? Instant.parse(expiresAtStr) : null);
    }

    // ─── Query helpers ────────────────────────────────────────────────────────

    private static QuerySpecInterface buildFieldEqQuery(String field, String value) {
        return buildFilterQuery(field + "=" + value, 1000);
    }

    private static QuerySpecInterface buildTwoFieldEqQuery(
            String field1, String value1, String field2, String value2) {
        return buildFilterQuery(field1 + "=" + value1 + " AND " + field2 + "=" + value2, 1000);
    }

    private static QuerySpecInterface buildFilterQuery(String filter, int limit) {
        return new QuerySpecInterface() {
            private String f = filter;
            private Integer lim = limit;
            @Override public String getFilter()            { return f; }
            @Override public void setFilter(String v)     { this.f = v; }
            @Override public Integer getLimit()            { return lim; }
            @Override public void setLimit(Integer v)     { this.lim = v; }
            @Override public Integer getOffset()           { return 0; }
            @Override public void setOffset(Integer v)    { /* no-op */ }
            @Override public String getQueryType()         { return "filter"; }
            @Override public void setQueryType(String v)  { /* no-op */ }
        };
    }

    // ─── Data map utilities ───────────────────────────────────────────────────

    private static void putIfNonNull(Map<String, Object> m, String key, Object value) {
        if (value != null) {
            m.put(key, value);
        }
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : null;
    }

    private static int intVal(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return 0;
        if (v instanceof Number n) return n.intValue();
        return Integer.parseInt(v.toString());
    }

    private static boolean boolVal(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(v.toString());
    }
}
