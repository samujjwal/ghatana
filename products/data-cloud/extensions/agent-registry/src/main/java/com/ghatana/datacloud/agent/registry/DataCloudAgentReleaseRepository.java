/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.registry;

import com.ghatana.agent.release.AgentRelease;
import com.ghatana.agent.release.AgentReleaseBuilder;
import com.ghatana.agent.release.AgentReleaseRepository;
import com.ghatana.agent.release.AgentReleaseState;
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
 * Data-Cloud-backed implementation of {@link AgentReleaseRepository}.
 *
 * <p>Each {@link AgentRelease} is persisted as an entity in the
 * {@value #COLLECTION} Data-Cloud collection. The entity's data map mirrors
 * the record fields so that filter queries can target individual attributes.
 *
 * <p>All public methods are ActiveJ async and must not block the event loop.
 *
 * @doc.type class
 * @doc.purpose Data-Cloud-backed persistence for AgentRelease lifecycle
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class DataCloudAgentReleaseRepository implements AgentReleaseRepository {

    private static final Logger log = LoggerFactory.getLogger(DataCloudAgentReleaseRepository.class);

    /** Data-Cloud collection name for agent releases. */
    public static final String COLLECTION = "agent-releases";

    // ─── field name constants for the data map ────────────────────────────────
    private static final String F_AGENT_RELEASE_ID           = "agentReleaseId";
    private static final String F_AGENT_ID                   = "agentId";
    private static final String F_SPEC_VERSION               = "specVersion";
    private static final String F_RELEASE_VERSION            = "releaseVersion";
    private static final String F_STATE                      = "state";
    private static final String F_SPEC_DIGEST                = "specDigest";
    private static final String F_POLICY_PACK_ID             = "policyPackId";
    private static final String F_POLICY_PACK_DIGEST         = "policyPackDigest";
    private static final String F_EVAL_PACK_ID               = "evaluationPackId";
    private static final String F_EVAL_PACK_DIGEST           = "evaluationPackDigest";
    private static final String F_MEMORY_CONTRACT_ID         = "memoryContractId";
    private static final String F_COMPATIBLE_RUNTIMES        = "compatibleRuntimeVersions";
    private static final String F_SIGNING_REF                = "signingReference";
    private static final String F_TOOL_CONTRACT_VERSION      = "toolContractVersion";
    private static final String F_TELEMETRY_CONTRACT_VERSION = "telemetryContractVersion";
    private static final String F_EXPLANATION_CONTRACT       = "explanationContractVersion";
    private static final String F_REDACTION_PROFILE_ID       = "redactionProfileId";
    private static final String F_THREAT_MODEL_ID            = "threatModelId";
    private static final String F_DATA_CLASSES               = "dataClassesHandled";
    private static final String F_PERMITTED_PURPOSES         = "permittedPurposes";
    private static final String F_CAPABILITY_MATURITY        = "capabilityMaturityProfile";
    private static final String F_CREATED_AT                 = "createdAt";
    private static final String F_UPDATED_AT                 = "updatedAt";
    private static final String F_CREATED_BY                 = "createdBy";

    private final DataCloudClient dataCloud;
    private final String tenantId;

    /**
     * Creates a new repository instance.
     *
     * @param dataCloud the Data-Cloud client
     * @param tenantId  the tenant scope for all operations
     */
    public DataCloudAgentReleaseRepository(DataCloudClient dataCloud, String tenantId) {
        this.dataCloud = Objects.requireNonNull(dataCloud, "dataCloud");
        this.tenantId  = Objects.requireNonNull(tenantId, "tenantId");
    }

    // ─── AgentReleaseRepository ───────────────────────────────────────────────

    @Override
    public Promise<AgentRelease> save(AgentRelease release) {
        Objects.requireNonNull(release, "release");
        Map<String, Object> data = toDataMap(release);

        return dataCloud.createEntity(tenantId, COLLECTION, data)
                .map(entity -> {
                    log.debug("Saved AgentRelease [{}] v{} state={} entity={}",
                            release.agentReleaseId(), release.releaseVersion(),
                            release.state(), entity.getId());
                    return release;
                });
    }

    @Override
    public Promise<Optional<AgentRelease>> findById(String agentReleaseId) {
        Objects.requireNonNull(agentReleaseId, "agentReleaseId");

        QuerySpecInterface query = buildFieldEqQuery(F_AGENT_RELEASE_ID, agentReleaseId);
        return dataCloud.queryEntities(tenantId, COLLECTION, query)
                .map(entities -> entities.stream()
                        .findFirst()
                        .map(e -> fromDataMap(e.getData())));
    }

    @Override
    public Promise<List<AgentRelease>> findByAgentId(String agentId) {
        Objects.requireNonNull(agentId, "agentId");

        QuerySpecInterface query = buildFieldEqQuery(F_AGENT_ID, agentId);
        return dataCloud.queryEntities(tenantId, COLLECTION, query)
                .map(entities -> entities.stream()
                        .map(e -> fromDataMap(e.getData()))
                        .collect(Collectors.toList()));
    }

    @Override
    public Promise<Optional<AgentRelease>> findActiveRelease(String agentId, String tenantId) {
        Objects.requireNonNull(agentId, "agentId");

        QuerySpecInterface query = buildTwoFieldEqQuery(
                F_AGENT_ID, agentId,
                F_STATE, AgentReleaseState.ACTIVE.name());
        return dataCloud.queryEntities(this.tenantId, COLLECTION, query)
                .map(entities -> entities.stream()
                        .findFirst()
                        .map(e -> fromDataMap(e.getData())));
    }

    @Override
    public Promise<AgentRelease> transition(String agentReleaseId, AgentReleaseState targetState, String principalId) {
        Objects.requireNonNull(agentReleaseId, "agentReleaseId");
        Objects.requireNonNull(targetState, "targetState");

        return findById(agentReleaseId)
                .then(optRelease -> {
                    if (optRelease.isEmpty()) {
                        return Promise.ofException(new IllegalStateException(
                                "AgentRelease not found: " + agentReleaseId));
                    }
                    AgentRelease existing = optRelease.get();
                    AgentRelease updated = existing.withState(targetState, Instant.now());
                    return save(updated);
                });
    }

    @Override
    public Promise<List<AgentRelease>> findByState(AgentReleaseState state) {
        Objects.requireNonNull(state, "state");

        QuerySpecInterface query = buildFieldEqQuery(F_STATE, state.name());
        return dataCloud.queryEntities(tenantId, COLLECTION, query)
                .map(entities -> entities.stream()
                        .map(e -> fromDataMap(e.getData()))
                        .collect(Collectors.toList()));
    }

    @Override
    public Promise<Optional<AgentRelease>> findGoverningRelease(String agentId, String tenantId) {
        Objects.requireNonNull(agentId, "agentId");

        // The filter expression matches any of the governing states.
        // Data Cloud filter syntax example: "agentId=X AND state IN (ACTIVE,CANARY,SHADOW,BLOCKED)"
        // Since Data Cloud uses flexible string filters, we query by agentId and filter states client-side.
        QuerySpecInterface query = buildFieldEqQuery(F_AGENT_ID, agentId);
        return dataCloud.queryEntities(this.tenantId, COLLECTION, query)
                .map(entities -> {
                    java.util.Set<AgentReleaseState> governingStates = java.util.Set.of(
                            AgentReleaseState.ACTIVE, AgentReleaseState.CANARY,
                            AgentReleaseState.SHADOW, AgentReleaseState.BLOCKED);
                    return entities.stream()
                            .map(e -> fromDataMap(e.getData()))
                            .filter(r -> governingStates.contains(r.state()))
                            .max(java.util.Comparator.comparing(AgentRelease::updatedAt));
                });
    }

    /**
     * Finds all agent releases associated with a specific evaluation pack.
     *
     * @param evaluationPackId the evaluation pack ID to filter by
     * @return a Promise of a list of agent releases with the given evaluation pack
     */
    public Promise<List<AgentRelease>> findByEvaluationPack(String evaluationPackId) {
        Objects.requireNonNull(evaluationPackId, "evaluationPackId");

        QuerySpecInterface query = buildFieldEqQuery(F_EVAL_PACK_ID, evaluationPackId);
        return dataCloud.queryEntities(tenantId, COLLECTION, query)
                .map(entities -> entities.stream()
                        .map(e -> fromDataMap(e.getData()))
                        .collect(Collectors.toList()));
    }

    /**
     * Finds all agent releases with a specific capability maturity profile.
     *
     * @param capabilityMaturity the capability maturity profile to filter by
     * @return a Promise of a list of agent releases with the given capability maturity
     */
    public Promise<List<AgentRelease>> findByCapabilityMaturity(String capabilityMaturity) {
        Objects.requireNonNull(capabilityMaturity, "capabilityMaturity");

        QuerySpecInterface query = buildFieldEqQuery(F_CAPABILITY_MATURITY, capabilityMaturity);
        return dataCloud.queryEntities(tenantId, COLLECTION, query)
                .map(entities -> entities.stream()
                        .map(e -> fromDataMap(e.getData()))
                        .collect(Collectors.toList()));
    }

    // ─── Serialization helpers ────────────────────────────────────────────────

    private static Map<String, Object> toDataMap(AgentRelease r) {
        Map<String, Object> m = new HashMap<>();
        m.put(F_AGENT_RELEASE_ID,           r.agentReleaseId());
        m.put(F_AGENT_ID,                   r.agentId());
        m.put(F_SPEC_VERSION,               r.specVersion());
        m.put(F_RELEASE_VERSION,            r.releaseVersion());
        m.put(F_STATE,                      r.state().name());
        putIfNonNull(m, F_SPEC_DIGEST,                r.specDigest());
        putIfNonNull(m, F_POLICY_PACK_ID,             r.policyPackId());
        putIfNonNull(m, F_POLICY_PACK_DIGEST,         r.policyPackDigest());
        putIfNonNull(m, F_EVAL_PACK_ID,               r.evaluationPackId());
        putIfNonNull(m, F_EVAL_PACK_DIGEST,           r.evaluationPackDigest());
        putIfNonNull(m, F_MEMORY_CONTRACT_ID,         r.memoryContractId());
        m.put(F_COMPATIBLE_RUNTIMES,        r.compatibleRuntimeVersions());
        putIfNonNull(m, F_SIGNING_REF,                r.signingReference());
        putIfNonNull(m, F_TOOL_CONTRACT_VERSION,      r.toolContractVersion());
        putIfNonNull(m, F_TELEMETRY_CONTRACT_VERSION, r.telemetryContractVersion());
        putIfNonNull(m, F_EXPLANATION_CONTRACT,       r.explanationContractVersion());
        putIfNonNull(m, F_REDACTION_PROFILE_ID,       r.redactionProfileId());
        putIfNonNull(m, F_THREAT_MODEL_ID,            r.threatModelId());
        m.put(F_DATA_CLASSES,               r.dataClassesHandled());
        m.put(F_PERMITTED_PURPOSES,         r.permittedPurposes());
        putIfNonNull(m, F_CAPABILITY_MATURITY, r.capabilityMaturityProfile());
        m.put(F_CREATED_AT,                 r.createdAt().toString());
        m.put(F_UPDATED_AT,                 r.updatedAt().toString());
        putIfNonNull(m, F_CREATED_BY,               r.createdBy());
        return m;
    }

    @SuppressWarnings("unchecked")
    private static AgentRelease fromDataMap(Map<String, Object> m) {
        return new AgentReleaseBuilder()
                .agentReleaseId((String) m.get(F_AGENT_RELEASE_ID))
                .agentId((String) m.get(F_AGENT_ID))
                .specVersion((String) m.getOrDefault(F_SPEC_VERSION, "1.0.0"))
                .releaseVersion((String) m.get(F_RELEASE_VERSION))
                .state(AgentReleaseState.valueOf((String) m.get(F_STATE)))
                .specDigest((String) m.get(F_SPEC_DIGEST))
                .policyPackId((String) m.get(F_POLICY_PACK_ID))
                .policyPackDigest((String) m.get(F_POLICY_PACK_DIGEST))
                .evaluationPackId((String) m.get(F_EVAL_PACK_ID))
                .evaluationPackDigest((String) m.get(F_EVAL_PACK_DIGEST))
                .memoryContractId((String) m.get(F_MEMORY_CONTRACT_ID))
                .compatibleRuntimeVersions(
                        m.get(F_COMPATIBLE_RUNTIMES) instanceof List<?> l
                        ? (List<String>) l
                        : List.of())
                .signingReference((String) m.get(F_SIGNING_REF))
                .toolContractVersion((String) m.get(F_TOOL_CONTRACT_VERSION))
                .telemetryContractVersion((String) m.get(F_TELEMETRY_CONTRACT_VERSION))
                .explanationContractVersion((String) m.get(F_EXPLANATION_CONTRACT))
                .redactionProfileId((String) m.get(F_REDACTION_PROFILE_ID))
                .threatModelId((String) m.get(F_THREAT_MODEL_ID))
                .dataClassesHandled(
                        m.get(F_DATA_CLASSES) instanceof Iterable<?> i
                        ? new java.util.HashSet<>(toStringList(i))
                        : java.util.Set.of())
                .permittedPurposes(
                        m.get(F_PERMITTED_PURPOSES) instanceof Iterable<?> i
                        ? new java.util.HashSet<>(toStringList(i))
                        : java.util.Set.of())
                .capabilityMaturityProfile((String) m.get(F_CAPABILITY_MATURITY))
                .createdAt(parseInstant(m.get(F_CREATED_AT)))
                .updatedAt(parseInstant(m.get(F_UPDATED_AT)))
                .createdBy((String) m.get(F_CREATED_BY))
                .build();
    }

    private static void putIfNonNull(Map<String, Object> m, String key, Object value) {
        if (value != null) {
            m.put(key, value);
        }
    }

    private static Instant parseInstant(Object value) {
        if (value instanceof Instant i) return i;
        if (value instanceof String s) return Instant.parse(s);
        return Instant.now();
    }

    private static List<String> toStringList(Iterable<?> iterable) {
        List<String> result = new java.util.ArrayList<>();
        for (Object o : iterable) {
            if (o instanceof String s) result.add(s);
        }
        return result;
    }

    // ─── Query builders ───────────────────────────────────────────────────────

    private static QuerySpecInterface buildFieldEqQuery(String field, String value) {
        return filterSpec(field + " = '" + value + "'");
    }

    private static QuerySpecInterface buildTwoFieldEqQuery(
            String field1, String value1,
            String field2, String value2) {
        return filterSpec(field1 + " = '" + value1 + "' AND " + field2 + " = '" + value2 + "'");
    }

    /**
     * Creates a minimal {@link QuerySpecInterface} that carries a filter string.
     * The filter is applied by the Data-Cloud query engine.
     */
    private static QuerySpecInterface filterSpec(String filter) {
        return new QuerySpecInterface() {
            private String f = filter;
            private Integer limit = 1000;
            private Integer offset = 0;

            @Override public String getFilter() { return f; }
            @Override public void setFilter(String v) { this.f = v; }
            @Override public Integer getLimit() { return limit; }
            @Override public void setLimit(Integer v) { this.limit = v; }
            @Override public Integer getOffset() { return offset; }
            @Override public void setOffset(Integer v) { this.offset = v; }
            @Override public String getQueryType() { return "filter"; }
            @Override public void setQueryType(String v) { /* no-op */ }
        };
    }
}
