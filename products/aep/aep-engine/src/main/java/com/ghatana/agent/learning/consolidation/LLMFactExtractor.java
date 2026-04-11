package com.ghatana.agent.learning.consolidation;

import com.ghatana.agent.memory.model.episode.EnhancedEpisode;
import com.ghatana.agent.memory.model.fact.EnhancedFact;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Extracts subject-predicate-object facts from episodes using an LLM.
 *
 * <p>The LLM analyzes episode input/output and extracts structured knowledge
 * triples that can be stored in semantic memory.
 *
 * @doc.type interface
 * @doc.purpose LLM-based fact extraction from episodes
 * @doc.layer agent-learning
 */
public interface LLMFactExtractor {

    /**
     * Extracts zero or more facts from an episode.
     *
     * @param episode The episode to analyze
     * @return List of extracted facts
     */
    @NotNull Promise<List<EnhancedFact>> extractFacts(@NotNull EnhancedEpisode episode);
}
