package com.ghatana.agent.learning.consolidation;

import com.ghatana.agent.memory.model.episode.EnhancedEpisode;
import com.ghatana.agent.memory.model.procedure.EnhancedProcedure;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Induces procedures from clusters of similar, successful episodes.
 * Groups episodes by semantic similarity, then generalizes action patterns
 * into reusable procedures.
 *
 * @doc.type interface
 * @doc.purpose Episode cluster → Procedure induction
 * @doc.layer agent-learning
 */
public interface ProcedureInducer {

    /**
     * Analyzes a batch of episodes and induces zero or more procedures.
     *
     * @param episodes Episodes to analyze (should be pre-filtered to successful ones)
     * @return List of induced procedures
     */
    @NotNull Promise<List<EnhancedProcedure>> induce(@NotNull List<EnhancedEpisode> episodes);
}
