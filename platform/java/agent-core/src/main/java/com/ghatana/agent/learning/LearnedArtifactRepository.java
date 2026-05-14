/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * @doc.type interface
 * @doc.purpose Durable store for evaluated and promotable learned artifacts
 * @doc.layer agent-core
 * @doc.pattern Repository
 */
/**
 * Durable store for evaluated and promotable learned artifacts.
 */
public interface LearnedArtifactRepository {

    @NotNull Promise<LearnedArtifact> save(@NotNull LearnedArtifact artifact);

    @NotNull Promise<Optional<LearnedArtifact>> findById(@NotNull String artifactId);

    @NotNull Promise<List<LearnedArtifact>> findByAgent(@NotNull String agentId);

    @NotNull Promise<List<LearnedArtifact>> findActiveByAgentAndTarget(
            @NotNull String agentId,
            @NotNull LearningTarget target);

    /**
     * Finds artifacts by their candidate ID.
     *
     * @param candidateId learning candidate identifier
     * @return promise of list of artifacts for the candidate
     */
    @NotNull Promise<List<LearnedArtifact>> findByCandidateId(@NotNull String candidateId);

    /**
     * Finds artifacts by tenant and candidate ID for idempotency checks.
     *
     * <p>The pair {@code tenantId + candidateId} must be unique to prevent duplicate
     * artifact creation when the same candidate is promoted more than once.
     *
     * @param tenantId    tenant identifier
     * @param candidateId learning candidate identifier
     * @return promise of list of artifacts matching the tenant + candidate pair
     */
    @NotNull Promise<List<LearnedArtifact>> findByTenantAndCandidateId(
            @NotNull String tenantId, @NotNull String candidateId);

    /**
     * Finds artifacts by tenant, content digest, and learning target for idempotency checks.
     *
     * <p>The triple {@code tenantId + contentDigest + target} must be unique to prevent
     * duplicate artifacts when the same content is promoted for the same target.
     *
     * @param tenantId      tenant identifier
     * @param contentDigest SHA-256 digest of the artifact payload
     * @param target        learning target
     * @return promise of list of artifacts matching the idempotency triple
     */
    @NotNull Promise<List<LearnedArtifact>> findByTenantContentDigestAndTarget(
            @NotNull String tenantId, @NotNull String contentDigest, @NotNull LearningTarget target);
}
