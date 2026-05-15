/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.learning.extractor;

import com.ghatana.agent.framework.memory.Episode;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Interface for typed learning extractors that synthesize learning candidates from episodes.
 * Phase 5 FIX: Replaces heuristic N-gram pattern synthesis with typed extractors.
 *
 * @doc.type interface
 * @doc.purpose Typed learning extractor interface
 * @doc.layer agent-core
 * @doc.pattern Strategy
 */
public interface LearningExtractor {

    /**
     * Extracts learning candidates from a batch of episodes.
     *
     * @param agentId agent identifier
     * @param episodes episodes to process
     * @return list of extracted learning candidates
     */
    @NotNull
    List<LearningCandidate> extract(@NotNull String agentId, @NotNull List<Episode> episodes);

    /**
     * Returns the type of learning this extractor produces.
     *
     * @return learning type
     */
    @NotNull
    LearningType type();
}
