package com.ghatana.agent.learning.consolidation;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/**
 * SPI for pluggable consolidation stages.
 *
 * <p>Each stage transforms memory from one tier to another (e.g., episodic → semantic,
 * episodic → procedural, episodic → preference). Products can implement custom stages
 * and inject them into {@link ConsolidationPipeline}.
 *
 * @doc.type interface
 * @doc.purpose Pluggable consolidation stage SPI
 * @doc.layer agent-learning
 * @doc.pattern Strategy / SPI
 */
public interface ConsolidationStage {

    /**
     * Human-readable name of this stage (for logging/metrics).
     *
     * @return stage name (e.g., "episodic-to-semantic", "episodic-to-procedural")
     */
    @NotNull String name();

    /**
     * Executes this consolidation stage for the given agent.
     *
     * @param agentId agent whose memory to consolidate
     * @param since   only process items created after this timestamp
     * @return number of items produced/consolidated by this stage
     */
    @NotNull Promise<Integer> execute(@NotNull String agentId, @NotNull Instant since);
}
