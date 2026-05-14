/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.registry;

import com.ghatana.agent.learning.LearnedArtifact;
import com.ghatana.agent.learning.LearnedArtifactRepository;
import com.ghatana.agent.learning.LearningTarget;
import com.ghatana.agent.learning.PromotionState;
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
 * @doc.type class
 * @doc.purpose Data-Cloud-backed persistence for learned artifacts
 * @doc.layer data-cloud
 * @doc.pattern Repository
 */
/**
 * Data-Cloud-backed persistence for learned artifacts.
 */
public final class DataCloudLearnedArtifactRepository implements LearnedArtifactRepository {

    public static final String COLLECTION = "learned-artifact";

    private static final String F_ARTIFACT_ID = "artifactId";
    private static final String F_AGENT_ID = "agentId";
    private static final String F_AGENT_RELEASE_ID = "agentReleaseId";
    private static final String F_TARGET = "target";
    private static final String F_STATE = "state";
    private static final String F_PAYLOAD = "payload";
    private static final String F_PROVENANCE_REFS = "provenanceRefs";
    private static final String F_PROMOTION_EVIDENCE_ID = "promotionEvidenceId";
    private static final String F_ROLLBACK_REF = "rollbackRef";
    private static final String F_CREATED_AT = "createdAt";
    private static final String F_CANDIDATE_ID = "candidateId";
    private static final String F_SKILL_ID = "skillId";
    private static final String F_TENANT_ID = "tenantId";
    private static final String F_SOURCE_EPISODE_IDS = "sourceEpisodeIds";
    private static final String F_CONTENT_DIGEST = "contentDigest";

    private final DataCloudClient dataCloud;
    private final String tenantId;

    public DataCloudLearnedArtifactRepository(@NotNull DataCloudClient dataCloud, @NotNull String tenantId) {
        this.dataCloud = Objects.requireNonNull(dataCloud, "dataCloud");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
    }

    @Override
    public @NotNull Promise<LearnedArtifact> save(@NotNull LearnedArtifact artifact) {
        return dataCloud.createEntity(tenantId, COLLECTION, toDataMap(artifact)).map(entity -> artifact);
    }

    @Override
    public @NotNull Promise<Optional<LearnedArtifact>> findById(@NotNull String artifactId) {
        return dataCloud.queryEntities(tenantId, COLLECTION, fieldEq(F_ARTIFACT_ID, artifactId))
                .map(entities -> entities.stream().findFirst().map(e -> fromDataMap(e.getData())));
    }

    @Override
    public @NotNull Promise<List<LearnedArtifact>> findByAgent(@NotNull String agentId) {
        return dataCloud.queryEntities(tenantId, COLLECTION, fieldEq(F_AGENT_ID, agentId))
                .map(entities -> entities.stream().map(e -> fromDataMap(e.getData())).toList());
    }

    @Override
    public @NotNull Promise<List<LearnedArtifact>> findActiveByAgentAndTarget(
            @NotNull String agentId,
            @NotNull LearningTarget target) {
        return dataCloud.queryEntities(tenantId, COLLECTION,
                        filterSpec(F_AGENT_ID + " = '" + agentId + "' AND "
                                + F_TARGET + " = '" + target.name() + "' AND "
                                + F_STATE + " = '" + PromotionState.ACTIVE.name() + "'"))
                .map(entities -> entities.stream()
                        .map(e -> fromDataMap(e.getData()))
                        .filter(a -> a.state() == PromotionState.ACTIVE)
                        .toList());
    }

    @Override
    public @NotNull Promise<List<LearnedArtifact>> findByCandidateId(@NotNull String candidateId) {
        return dataCloud.queryEntities(tenantId, COLLECTION, fieldEq(F_CANDIDATE_ID, candidateId))
                .map(entities -> entities.stream().map(e -> fromDataMap(e.getData())).toList());
    }

    @Override
    public @NotNull Promise<List<LearnedArtifact>> findByTenantAndCandidateId(
            @NotNull String queryTenantId, @NotNull String candidateId) {
        return dataCloud.queryEntities(queryTenantId, COLLECTION,
                        filterSpec(F_CANDIDATE_ID + " = '" + candidateId + "'"))
                .map(entities -> entities.stream().map(e -> fromDataMap(e.getData())).toList());
    }

    @Override
    public @NotNull Promise<List<LearnedArtifact>> findByTenantContentDigestAndTarget(
            @NotNull String queryTenantId, @NotNull String contentDigest, @NotNull LearningTarget target) {
        return dataCloud.queryEntities(queryTenantId, COLLECTION,
                        filterSpec(F_CONTENT_DIGEST + " = '" + contentDigest + "' AND "
                                + F_TARGET + " = '" + target.name() + "'"))
                .map(entities -> entities.stream().map(e -> fromDataMap(e.getData())).toList());
    }

    private static Map<String, Object> toDataMap(LearnedArtifact artifact) {
        Map<String, Object> data = new HashMap<>();
        data.put(F_ARTIFACT_ID, artifact.artifactId());
        data.put(F_AGENT_ID, artifact.agentId());
        data.put(F_AGENT_RELEASE_ID, artifact.agentReleaseId());
        data.put(F_TARGET, artifact.target().name());
        data.put(F_STATE, artifact.state().name());
        data.put(F_PAYLOAD, artifact.payload());
        data.put(F_PROVENANCE_REFS, artifact.provenanceRefs());
        data.put(F_PROMOTION_EVIDENCE_ID, artifact.promotionEvidenceId());
        data.put(F_ROLLBACK_REF, artifact.rollbackRef());
        data.put(F_CREATED_AT, artifact.createdAt().toString());
        data.put(F_CANDIDATE_ID, artifact.candidateId());
        data.put(F_SKILL_ID, artifact.skillId());
        data.put(F_TENANT_ID, artifact.tenantId());
        data.put(F_SOURCE_EPISODE_IDS, artifact.sourceEpisodeIds());
        data.put(F_CONTENT_DIGEST, artifact.contentDigest());
        return data;
    }

    @SuppressWarnings("unchecked")
    private static LearnedArtifact fromDataMap(Map<String, Object> data) {
        return new LearnedArtifact(
                str(data, F_ARTIFACT_ID),
                str(data, F_AGENT_ID),
                str(data, F_AGENT_RELEASE_ID),
                LearningTarget.valueOf(str(data, F_TARGET)),
                PromotionState.valueOf(str(data, F_STATE)),
                (Map<String, Object>) data.getOrDefault(F_PAYLOAD, Map.of()),
                (List<String>) data.getOrDefault(F_PROVENANCE_REFS, List.of()),
                str(data, F_PROMOTION_EVIDENCE_ID),
                str(data, F_ROLLBACK_REF),
                Instant.parse(str(data, F_CREATED_AT)),
                str(data, F_CANDIDATE_ID),
                str(data, F_SKILL_ID),
                str(data, F_TENANT_ID),
                (List<String>) data.getOrDefault(F_SOURCE_EPISODE_IDS, List.of()),
                str(data, F_CONTENT_DIGEST));
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
