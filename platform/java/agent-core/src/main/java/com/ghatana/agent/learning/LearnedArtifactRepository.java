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
 * Durable store for evaluated and promotable learned artifacts.
 */
public interface LearnedArtifactRepository {

    @NotNull Promise<LearnedArtifact> save(@NotNull LearnedArtifact artifact);

    @NotNull Promise<Optional<LearnedArtifact>> findById(@NotNull String artifactId);

    @NotNull Promise<List<LearnedArtifact>> findByAgent(@NotNull String agentId);

    @NotNull Promise<List<LearnedArtifact>> findActiveByAgentAndTarget(
            @NotNull String agentId,
            @NotNull LearningTarget target);
}
