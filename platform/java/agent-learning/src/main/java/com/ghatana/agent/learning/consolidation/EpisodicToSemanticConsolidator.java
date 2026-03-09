package com.ghatana.agent.learning.consolidation;

import com.ghatana.agent.memory.model.MemoryItemType;
import com.ghatana.agent.memory.model.episode.EnhancedEpisode;
import com.ghatana.agent.memory.model.fact.EnhancedFact;
import com.ghatana.agent.memory.store.MemoryPlane;
import com.ghatana.agent.memory.store.MemoryQuery;
import com.ghatana.agent.memory.store.semantic.SemanticMemoryManager;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Extracts facts (semantic memory) from episodes (episodic memory).
 * Uses an LLM to analyze episode inputs/outputs and derive subject-predicate-object triples.
 *
 * <p>Implements {@link ConsolidationStage} so it can be plugged into
 * {@link ConsolidationPipeline} alongside other stages.
 *
 * @doc.type class
 * @doc.purpose Episodic → Semantic consolidation
 * @doc.layer agent-learning
 */
public class EpisodicToSemanticConsolidator implements ConsolidationStage {

    private static final Logger log = LoggerFactory.getLogger(EpisodicToSemanticConsolidator.class);

    private final MemoryPlane memoryPlane;
    private final SemanticMemoryManager semanticManager;
    private final LLMFactExtractor factExtractor;

    public EpisodicToSemanticConsolidator(
            @NotNull MemoryPlane memoryPlane,
            @NotNull SemanticMemoryManager semanticManager,
            @NotNull LLMFactExtractor factExtractor) {
        this.memoryPlane = Objects.requireNonNull(memoryPlane);
        this.semanticManager = Objects.requireNonNull(semanticManager);
        this.factExtractor = Objects.requireNonNull(factExtractor);
    }

    // =========================================================================
    // ConsolidationStage SPI
    // =========================================================================

    @Override
    @NotNull
    public String name() {
        return "episodic-to-semantic";
    }

    @Override
    @NotNull
    public Promise<Integer> execute(@NotNull String agentId, @NotNull Instant since) {
        return extract(agentId, since);
    }

    /**
     * Extracts facts from un-consolidated episodes since the given time.
     *
     * @param agentId Agent whose episodes to process
     * @param since Only process episodes after this timestamp
     * @return Number of facts extracted
     */
    @NotNull
    public Promise<Integer> extract(@NotNull String agentId, @NotNull Instant since) {
        log.info("Extracting facts from episodes for agent {} since {}", agentId, since);

        MemoryQuery query = MemoryQuery.builder()
                .agentId(agentId)
                .itemTypes(List.of(MemoryItemType.EPISODE))
                .startTime(since)
                .limit(100)
                .build();

        return memoryPlane.queryEpisodes(query)
                .then(episodes -> {
                    if (episodes.isEmpty()) {
                        log.debug("No unconsolidated episodes found");
                        return Promise.of(0);
                    }
                    log.debug("Processing {} episodes", episodes.size());
                    return processEpisodesBatch(episodes, 0, 0);
                });
    }

    private Promise<Integer> processEpisodesBatch(List<EnhancedEpisode> episodes, int index, int extracted) {
        if (index >= episodes.size()) {
            return Promise.of(extracted);
        }

        EnhancedEpisode episode = episodes.get(index);
        return factExtractor.extractFacts(episode)
                .then(facts -> {
                    int count = 0;
                    for (EnhancedFact fact : facts) {
                        semanticManager.storeOrVersion(fact);
                        count++;
                    }
                    return processEpisodesBatch(episodes, index + 1, extracted + count);
                });
    }
}
