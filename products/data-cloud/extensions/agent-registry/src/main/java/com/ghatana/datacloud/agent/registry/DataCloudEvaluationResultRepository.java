/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.registry;

import com.ghatana.agent.release.EvaluationResult;
import com.ghatana.agent.release.EvaluationResultRepository;
import com.ghatana.datacloud.client.DataCloudClient;
import com.ghatana.datacloud.entity.storage.QuerySpecInterface;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Data-Cloud-backed implementation of {@link EvaluationResultRepository}.
 *
 * <p>Each {@link EvaluationResult} is persisted as an entity in the
 * {@value #COLLECTION} Data-Cloud collection. The entity data map mirrors
 * the record fields for filter query support.
 *
 * <p>All public methods are ActiveJ async and must not block the event loop.
 *
 * @doc.type class
 * @doc.purpose Data-Cloud-backed persistence for agent evaluation results
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class DataCloudEvaluationResultRepository implements EvaluationResultRepository {

    private static final Logger log = LoggerFactory.getLogger(DataCloudEvaluationResultRepository.class);

    /** Data-Cloud collection name for evaluation results. */
    public static final String COLLECTION = "evaluation-results";

    // ─── field name constants ──────────────────────────────────────────────────
    private static final String F_EVALUATION_ID    = "evaluationId";
    private static final String F_AGENT_RELEASE_ID = "agentReleaseId";
    private static final String F_TENANT_ID        = "tenantId";
    private static final String F_EVALUATOR_TYPE   = "evaluatorType";
    private static final String F_SCORE            = "score";
    private static final String F_PASSED           = "passed";
    private static final String F_RUBRIC_NAME      = "rubricName";
    private static final String F_RUN_ID           = "runId";
    private static final String F_TRACE_ID         = "traceId";
    private static final String F_EVALUATED_AT     = "evaluatedAt";

    private final DataCloudClient dataCloud;
    private final String tenantId;

    /**
     * Creates a new repository instance.
     *
     * @param dataCloud the Data-Cloud client
     * @param tenantId  the tenant scope for all operations
     */
    public DataCloudEvaluationResultRepository(DataCloudClient dataCloud, String tenantId) {
        this.dataCloud = Objects.requireNonNull(dataCloud, "dataCloud");
        this.tenantId  = Objects.requireNonNull(tenantId, "tenantId");
    }

    // ─── EvaluationResultRepository ───────────────────────────────────────────

    @Override
    public Promise<EvaluationResult> save(EvaluationResult result) {
        Objects.requireNonNull(result, "result");
        Map<String, Object> data = toDataMap(result);
        return dataCloud.createEntity(tenantId, COLLECTION, data)
                .map(entity -> {
                    log.debug("Saved EvaluationResult [{}] release={} passed={} entity={}",
                            result.evaluationId(), result.agentReleaseId(), result.passed(), entity.getId());
                    return result;
                });
    }

    @Override
    public Promise<Optional<EvaluationResult>> findById(String evaluationId) {
        Objects.requireNonNull(evaluationId, "evaluationId");
        QuerySpecInterface query = buildFieldEqQuery(F_EVALUATION_ID, evaluationId);
        return dataCloud.queryEntities(tenantId, COLLECTION, query)
                .map(entities -> entities.stream()
                        .findFirst()
                        .map(e -> fromDataMap(e.getData())));
    }

    @Override
    public Promise<List<EvaluationResult>> findByRelease(String agentReleaseId, String tenantId) {
        Objects.requireNonNull(agentReleaseId, "agentReleaseId");
        QuerySpecInterface query = buildFieldEqQuery(F_AGENT_RELEASE_ID, agentReleaseId);
        return dataCloud.queryEntities(this.tenantId, COLLECTION, query)
                .map(entities -> entities.stream()
                        .map(e -> fromDataMap(e.getData()))
                        .collect(Collectors.toCollection(ArrayList::new)));
    }

    @Override
    public Promise<List<EvaluationResult>> findPassingByRelease(String agentReleaseId, String tenantId) {
        Objects.requireNonNull(agentReleaseId, "agentReleaseId");
        QuerySpecInterface query = buildTwoFieldEqQuery(
                F_AGENT_RELEASE_ID, agentReleaseId,
                F_PASSED, "true");
        return dataCloud.queryEntities(this.tenantId, COLLECTION, query)
                .map(entities -> entities.stream()
                        .map(e -> fromDataMap(e.getData()))
                        .filter(EvaluationResult::passed)
                        .collect(Collectors.toCollection(ArrayList::new)));
    }

    @Override
    public Promise<Long> countPassing(String agentReleaseId, String tenantId) {
        return findPassingByRelease(agentReleaseId, tenantId)
                .map(list -> (long) list.size());
    }

    @Override
    public Promise<Long> deleteByRelease(String agentReleaseId, String tenantId) {
        Objects.requireNonNull(agentReleaseId, "agentReleaseId");
        QuerySpecInterface query = buildFieldEqQuery(F_AGENT_RELEASE_ID, agentReleaseId);
        return dataCloud.queryEntities(this.tenantId, COLLECTION, query)
                .then(entities -> {
                    List<String> ids = entities.stream()
                            .map(e -> (String) e.getData().get(F_EVALUATION_ID))
                            .filter(Objects::nonNull)
                            .collect(Collectors.toCollection(ArrayList::new));
                    if (ids.isEmpty()) {
                        return Promise.of(0L);
                    }
                    // Delete each entity sequentially; DataCloud client does not expose bulk delete
                    return deleteSequentially(ids, 0L);
                });
    }

    private Promise<Long> deleteSequentially(List<String> ids, long deleted) {
        if (ids.isEmpty()) {
            return Promise.of(deleted);
        }
        String evaluationId = ids.removeFirst();
        QuerySpecInterface query = buildFieldEqQuery(F_EVALUATION_ID, evaluationId);
        return dataCloud.queryEntities(tenantId, COLLECTION, query)
                .then(entities -> {
                    if (entities.isEmpty()) {
                        return deleteSequentially(ids, deleted);
                    }
                    java.util.UUID entityId = entities.getFirst().getId();
                    return dataCloud.deleteEntity(tenantId, COLLECTION, entityId)
                            .then(ignored -> deleteSequentially(ids, deleted + 1));
                });
    }

    // ─── Serialization helpers ─────────────────────────────────────────────────

    private static Map<String, Object> toDataMap(EvaluationResult r) {
        Map<String, Object> m = new HashMap<>();
        m.put(F_EVALUATION_ID,    r.evaluationId());
        m.put(F_AGENT_RELEASE_ID, r.agentReleaseId());
        m.put(F_TENANT_ID,        r.tenantId());
        m.put(F_EVALUATOR_TYPE,   r.evaluatorType());
        m.put(F_SCORE,            r.score());
        m.put(F_PASSED,           String.valueOf(r.passed()));
        m.put(F_EVALUATED_AT,     r.evaluatedAt().toString());
        if (r.rubricName() != null)  m.put(F_RUBRIC_NAME, r.rubricName());
        if (r.runId() != null)       m.put(F_RUN_ID, r.runId());
        if (r.traceId() != null)     m.put(F_TRACE_ID, r.traceId());
        if (!r.data().isEmpty())     m.putAll(r.data());
        return m;
    }

    private static EvaluationResult fromDataMap(Map<String, Object> m) {
        double score;
        Object rawScore = m.get(F_SCORE);
        if (rawScore instanceof Number n) {
            score = n.doubleValue();
        } else {
            score = Double.parseDouble(String.valueOf(rawScore));
        }
        Object rawPassed = m.get(F_PASSED);
        boolean passed = rawPassed instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(rawPassed));

        return new EvaluationResult(
                str(m, F_EVALUATION_ID),
                str(m, F_AGENT_RELEASE_ID),
                str(m, F_TENANT_ID),
                str(m, F_EVALUATOR_TYPE),
                score,
                passed,
                (String) m.get(F_RUBRIC_NAME),
                (String) m.get(F_RUN_ID),
                (String) m.get(F_TRACE_ID),
                Instant.parse(str(m, F_EVALUATED_AT)),
                Map.of()
        );
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? String.valueOf(v) : null;
    }

    private static QuerySpecInterface buildFieldEqQuery(String field, String value) {
        return filterSpec(field + " = '" + value + "'");
    }

    private static QuerySpecInterface buildTwoFieldEqQuery(
            String field1, String value1,
            String field2, String value2) {
        return filterSpec(field1 + " = '" + value1 + "' AND " + field2 + " = '" + value2 + "'");
    }

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
