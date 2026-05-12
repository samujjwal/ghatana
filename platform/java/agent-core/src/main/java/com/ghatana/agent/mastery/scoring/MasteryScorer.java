/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery.scoring;

import com.ghatana.agent.mastery.MasteryItem;
import com.ghatana.agent.mastery.MasteryScore;
import org.jetbrains.annotations.NotNull;

/**
 * Scorer for computing mastery scores from evidence.
 *
 * @doc.type interface
 * @doc.purpose Scorer for computing mastery scores
 * @doc.layer agent-core
 * @doc.pattern Scorer
 */
public interface MasteryScorer {

    /**
     * Computes a mastery score from evidence.
     *
     * @param evidence evidence bundle
     * @return mastery score
     */
    @NotNull
    MasteryScore score(@NotNull EvidenceBundle evidence);

    /**
     * Updates an existing mastery score with new evidence.
     *
     * @param currentScore current mastery score
     * @param evidence new evidence bundle
     * @return updated mastery score
     */
    @NotNull
    MasteryScore update(@NotNull MasteryScore currentScore, @NotNull EvidenceBundle evidence);
}
