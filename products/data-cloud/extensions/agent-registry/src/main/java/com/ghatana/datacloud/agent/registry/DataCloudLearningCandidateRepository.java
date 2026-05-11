/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.registry;

import com.ghatana.agent.learning.LearningCandidate;
import com.ghatana.agent.learning.LearningCandidateRepository;
import com.ghatana.agent.learning.LearningCandidateState;
import com.ghatana.agent.learning.LearningTarget;
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
 * Data-Cloud-backed persistence for learning candidates.
 */
public final class DataCloudLearningCandidateRepository implements LearningCandidateRepository {

    public static final String COLLECTION = "learning-candidate";

    private static final String F_CANDIDATE_ID = "candidateId";
    private static final String F_AGENT_ID = "agentId";
    private static final String F_AGENT_RELEASE_ID = "agentReleaseId";
    private static final String F_TRACE_ID = "traceId";
    private static final String F_TARGET = "target";
    private static final String F_STATE = "state";
    private static final String F_PROVENANCE_REFS = "provenanceRefs";
    private static final String F_PROPOSED_ARTIFACT = "proposedArtifact";
    private static final String F_CREATED_AT = "createdAt";

    private final DataCloudClient dataCloud;
    private final String tenantId;

    public DataCloudLearningCandidateRepository(@NotNull DataCloudClient dataCloud, @NotNull String tenantId) {
        this.dataCloud = Objects.requireNonNull(dataCloud, "dataCloud");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
    }

    @Override
    public @NotNull Promise<LearningCandidate> save(@NotNull LearningCandidate candidate) {
        return dataCloud.createEntity(tenantId, COLLECTION, toDataMap(candidate)).map(entity -> candidate);
    }

    @Override
    public @NotNull Promise<Optional<LearningCandidate>> findById(@NotNull String candidateId) {
        return dataCloud.queryEntities(tenantId, COLLECTION, fieldEq(F_CANDIDATE_ID, candidateId))
                .map(entities -> entities.stream().findFirst().map(e -> fromDataMap(e.getData())));
    }

    @Override
    public @NotNull Promise<List<LearningCandidate>> findByAgentRelease(@NotNull String agentReleaseId) {
        return dataCloud.queryEntities(tenantId, COLLECTION, fieldEq(F_AGENT_RELEASE_ID, agentReleaseId))
                .map(entities -> entities.stream().map(e -> fromDataMap(e.getData())).toList());
    }

    @Override
    public @NotNull Promise<List<LearningCandidate>> findByState(@NotNull LearningCandidateState state) {
        return dataCloud.queryEntities(tenantId, COLLECTION, fieldEq(F_STATE, state.name()))
                .map(entities -> entities.stream().map(e -> fromDataMap(e.getData())).toList());
    }

    private static Map<String, Object> toDataMap(LearningCandidate c) {
        Map<String, Object> data = new HashMap<>();
        data.put(F_CANDIDATE_ID, c.candidateId());
        data.put(F_AGENT_ID, c.agentId());
        data.put(F_AGENT_RELEASE_ID, c.agentReleaseId());
        data.put(F_TRACE_ID, c.traceId());
        data.put(F_TARGET, c.target().name());
        data.put(F_STATE, c.state().name());
        data.put(F_PROVENANCE_REFS, c.provenanceRefs());
        data.put(F_PROPOSED_ARTIFACT, c.proposedArtifact());
        data.put(F_CREATED_AT, c.createdAt().toString());
        return data;
    }

    @SuppressWarnings("unchecked")
    private static LearningCandidate fromDataMap(Map<String, Object> data) {
        return new LearningCandidate(
                str(data, F_CANDIDATE_ID),
                str(data, F_AGENT_ID),
                str(data, F_AGENT_RELEASE_ID),
                str(data, F_TRACE_ID),
                LearningTarget.valueOf(str(data, F_TARGET)),
                LearningCandidateState.valueOf(str(data, F_STATE)),
                (List<String>) data.getOrDefault(F_PROVENANCE_REFS, List.of()),
                (Map<String, Object>) data.getOrDefault(F_PROPOSED_ARTIFACT, Map.of()),
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
