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
 * Durable store for evidence that justifies promoting a learned artifact.
 */
public interface PromotionEvidenceRepository {

    @NotNull Promise<PromotionEvidence> save(@NotNull PromotionEvidence evidence);

    @NotNull Promise<Optional<PromotionEvidence>> findById(@NotNull String evidenceId);

    @NotNull Promise<List<PromotionEvidence>> findByCandidate(@NotNull String candidateId);
}
