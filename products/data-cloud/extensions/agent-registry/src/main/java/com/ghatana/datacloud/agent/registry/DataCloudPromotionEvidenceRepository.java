/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.registry;

import com.ghatana.agent.learning.PromotionEvidence;
import com.ghatana.agent.learning.PromotionEvidenceRepository;
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
 * Data-Cloud-backed persistence for promotion evidence.
 */
public final class DataCloudPromotionEvidenceRepository implements PromotionEvidenceRepository {

    public static final String COLLECTION = "promotion-evidence";

    private static final String F_EVIDENCE_ID = "evidenceId";
    private static final String F_CANDIDATE_ID = "candidateId";
    private static final String F_EVALUATION_PACK_ID = "evaluationPackId";
    private static final String F_EVALUATION_REFS = "evaluationRefs";
    private static final String F_METRICS = "metrics";
    private static final String F_APPROVED_BY = "approvedBy";
    private static final String F_CREATED_AT = "createdAt";

    private final DataCloudClient dataCloud;
    private final String tenantId;

    public DataCloudPromotionEvidenceRepository(@NotNull DataCloudClient dataCloud, @NotNull String tenantId) {
        this.dataCloud = Objects.requireNonNull(dataCloud, "dataCloud");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
    }

    @Override
    public @NotNull Promise<PromotionEvidence> save(@NotNull PromotionEvidence evidence) {
        return dataCloud.createEntity(tenantId, COLLECTION, toDataMap(evidence)).map(entity -> evidence);
    }

    @Override
    public @NotNull Promise<Optional<PromotionEvidence>> findById(@NotNull String evidenceId) {
        return dataCloud.queryEntities(tenantId, COLLECTION, fieldEq(F_EVIDENCE_ID, evidenceId))
                .map(entities -> entities.stream().findFirst().map(e -> fromDataMap(e.getData())));
    }

    @Override
    public @NotNull Promise<List<PromotionEvidence>> findByCandidate(@NotNull String candidateId) {
        return dataCloud.queryEntities(tenantId, COLLECTION, fieldEq(F_CANDIDATE_ID, candidateId))
                .map(entities -> entities.stream().map(e -> fromDataMap(e.getData())).toList());
    }

    private static Map<String, Object> toDataMap(PromotionEvidence evidence) {
        Map<String, Object> data = new HashMap<>();
        data.put(F_EVIDENCE_ID, evidence.evidenceId());
        data.put(F_CANDIDATE_ID, evidence.candidateId());
        data.put(F_EVALUATION_PACK_ID, evidence.evaluationPackId());
        data.put(F_EVALUATION_REFS, evidence.evaluationRefs());
        data.put(F_METRICS, evidence.metrics());
        data.put(F_APPROVED_BY, evidence.approvedBy());
        data.put(F_CREATED_AT, evidence.createdAt().toString());
        return data;
    }

    @SuppressWarnings("unchecked")
    private static PromotionEvidence fromDataMap(Map<String, Object> data) {
        return new PromotionEvidence(
                str(data, F_EVIDENCE_ID),
                str(data, F_CANDIDATE_ID),
                str(data, F_EVALUATION_PACK_ID),
                (List<String>) data.getOrDefault(F_EVALUATION_REFS, List.of()),
                (Map<String, Object>) data.getOrDefault(F_METRICS, Map.of()),
                str(data, F_APPROVED_BY),
                Instant.parse(str(data, F_CREATED_AT)));
    }

    private static QuerySpecInterface fieldEq(String field, String value) {
        return filterSpec(field + " = '" + value + "'");
    }

    private static QuerySpecInterface filterSpec(String filter) {
        return new QuerySpecInterface() {
            private String f = filter;
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
