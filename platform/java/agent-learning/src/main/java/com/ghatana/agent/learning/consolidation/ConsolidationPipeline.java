package com.ghatana.agent.learning.consolidation;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

/**
 * Orchestrates memory consolidation through a pluggable list of {@link ConsolidationStage}s.
 *
 * <p>Stages are executed sequentially in the order provided. Built-in stages:
 * <ul>
 *   <li>{@link EpisodicToSemanticConsolidator} — Extract facts from episodes (LLM-based)</li>
 *   <li>{@link EpisodicToProceduralConsolidator} — Induce procedures from successful episodes</li>
 * </ul>
 *
 * <p>Products can inject custom stages (e.g., EpisodicToPreferenceConsolidator) by
 * implementing the {@link ConsolidationStage} SPI and passing them to the constructor.
 *
 * <p>Mirrors human sleep-cycle memory consolidation.
 *
 * @doc.type class
 * @doc.purpose Pluggable memory consolidation pipeline
 * @doc.layer agent-learning
 * @doc.pattern Pipeline / SPI
 */
public class ConsolidationPipeline {

    private static final Logger log = LoggerFactory.getLogger(ConsolidationPipeline.class);

    private final List<ConsolidationStage> stages;
    private final ConflictResolver conflictResolver;

    /**
     * Creates a consolidation pipeline with the given stages.
     *
     * @param stages           ordered list of consolidation stages to execute
     * @param conflictResolver resolver for cross-tier conflicts
     */
    public ConsolidationPipeline(
            @NotNull List<ConsolidationStage> stages,
            @NotNull ConflictResolver conflictResolver) {
        if (stages.isEmpty()) {
            throw new IllegalArgumentException("At least one consolidation stage is required");
        }
        this.stages = List.copyOf(stages);
        this.conflictResolver = Objects.requireNonNull(conflictResolver);
    }

    /**
     * Convenience constructor with the two built-in stages.
     *
     * @param semanticConsolidator  episodic → semantic stage
     * @param proceduralConsolidator episodic → procedural stage
     * @param conflictResolver      conflict resolver
     */
    public ConsolidationPipeline(
            @NotNull EpisodicToSemanticConsolidator semanticConsolidator,
            @NotNull EpisodicToProceduralConsolidator proceduralConsolidator,
            @NotNull ConflictResolver conflictResolver) {
        this(List.of(semanticConsolidator, proceduralConsolidator), conflictResolver);
    }

    /**
     * Runs all consolidation stages sequentially for the given agent.
     *
     * @param agentId Agent to consolidate
     * @param since   Only process episodes after this timestamp
     * @return Consolidation result with per-stage counts
     */
    @NotNull
    public Promise<ConsolidationResult> consolidate(@NotNull String agentId, @NotNull Instant since) {
        log.info("Starting consolidation for agent {} since {} ({} stages)", agentId, since, stages.size());

        return executeStages(agentId, since, 0, new LinkedHashMap<>())
                .map(stageCounts -> new ConsolidationResult(
                        agentId,
                        stageCounts,
                        0, // conflicts resolved by inline resolution
                        Instant.now()
                ));
    }

    private Promise<Map<String, Integer>> executeStages(
            String agentId, Instant since, int index, Map<String, Integer> accumulated) {
        if (index >= stages.size()) {
            return Promise.of(accumulated);
        }

        ConsolidationStage stage = stages.get(index);
        log.debug("Executing consolidation stage: {}", stage.name());

        return stage.execute(agentId, since)
                .then(count -> {
                    accumulated.put(stage.name(), count);
                    log.debug("Stage {} produced {} items", stage.name(), count);
                    return executeStages(agentId, since, index + 1, accumulated);
                });
    }

    /**
     * Returns the registered stages (unmodifiable).
     *
     * @return list of stages
     */
    @NotNull
    public List<ConsolidationStage> getStages() {
        return stages;
    }

    /**
     * Result of a consolidation run.
     *
     * @param agentId           agent that was consolidated
     * @param stageResults      map of stage name → items produced
     * @param conflictsResolved count of conflicts resolved
     * @param completedAt       when consolidation completed
     */
    public record ConsolidationResult(
            @NotNull String agentId,
            @NotNull Map<String, Integer> stageResults,
            int conflictsResolved,
            @NotNull Instant completedAt) {

        /**
         * Backward-compatible accessor for total facts extracted.
         *
         * @return facts extracted count (from "episodic-to-semantic" stage, or 0)
         */
        public int factsExtracted() {
            return stageResults.getOrDefault("episodic-to-semantic", 0);
        }

        /**
         * Backward-compatible accessor for total procedures induced.
         *
         * @return procedures induced count (from "episodic-to-procedural" stage, or 0)
         */
        public int proceduresInduced() {
            return stageResults.getOrDefault("episodic-to-procedural", 0);
        }

        /** Total items produced across all stages. */
        public int totalItemsProduced() {
            return stageResults.values().stream().mapToInt(Integer::intValue).sum();
        }
    }
}
