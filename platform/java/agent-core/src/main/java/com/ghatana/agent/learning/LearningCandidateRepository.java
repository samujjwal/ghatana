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
 * @doc.purpose Durable store for proposed learned artifacts before promotion
 * @doc.layer agent-core
 * @doc.pattern Repository
 */
/**
 * Durable store for proposed learned artifacts before promotion.
 */
public interface LearningCandidateRepository {

    @NotNull Promise<LearningCandidate> save(@NotNull LearningCandidate candidate);

    @NotNull Promise<Optional<LearningCandidate>> findById(@NotNull String candidateId);

    @NotNull Promise<List<LearningCandidate>> findByAgentRelease(@NotNull String agentReleaseId);

    @NotNull Promise<List<LearningCandidate>> findByState(@NotNull LearningCandidateState state);
}
