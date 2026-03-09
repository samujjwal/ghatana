package com.ghatana.agent.learning.consolidation;

import com.ghatana.agent.memory.model.MemoryItemType;
import com.ghatana.agent.memory.model.episode.EnhancedEpisode;
import com.ghatana.agent.memory.model.procedure.EnhancedProcedure;
import com.ghatana.agent.memory.store.MemoryPlane;
import com.ghatana.agent.memory.store.MemoryQuery;
import com.ghatana.agent.memory.store.procedural.ProceduralMemoryManager;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Induces procedures (skills) from clusters of successful episodes.
 * Groups similar episodes and extracts common action patterns.
 *
 * <p>Implements {@link ConsolidationStage} so it can be plugged into
 * {@link ConsolidationPipeline} alongside other stages.
 *
 * @doc.type class
 * @doc.purpose Episodic → Procedural consolidation
 * @doc.layer agent-learning
 */
public class EpisodicToProceduralConsolidator implements ConsolidationStage {

    private static final Logger log = LoggerFactory.getLogger(EpisodicToProceduralConsolidator.class);

    private final MemoryPlane memoryPlane;
    private final ProceduralMemoryManager proceduralManager;
    private final ProcedureInducer procedureInducer;

    public EpisodicToProceduralConsolidator(
            @NotNull MemoryPlane memoryPlane,
            @NotNull ProceduralMemoryManager proceduralManager,
            @NotNull ProcedureInducer procedureInducer) {
        this.memoryPlane = Objects.requireNonNull(memoryPlane);
        this.proceduralManager = Objects.requireNonNull(proceduralManager);
        this.procedureInducer = Objects.requireNonNull(procedureInducer);
    }

    // =========================================================================
    // ConsolidationStage SPI
    // =========================================================================

    @Override
    @NotNull
    public String name() {
        return "episodic-to-procedural";
    }

    @Override
    @NotNull
    public Promise<Integer> execute(@NotNull String agentId, @NotNull Instant since) {
        return induce(agentId, since);
    }

    /**
     * Induces procedures from successful episode clusters.
     *
     * @param agentId Agent whose episodes to process
     * @param since Only process episodes after this timestamp
     * @return Number of procedures induced
     */
    @NotNull
    public Promise<Integer> induce(@NotNull String agentId, @NotNull Instant since) {
        log.info("Inducing procedures from episodes for agent {} since {}", agentId, since);

        MemoryQuery query = MemoryQuery.builder()
                .agentId(agentId)
                .itemTypes(List.of(MemoryItemType.EPISODE))
                .startTime(since)
                .limit(100)
                .build();

        return memoryPlane.queryEpisodes(query)
                .then(episodes -> {
                    if (episodes.size() < 3) {
                        log.debug("Not enough episodes for procedure induction (need ≥ 3, got {})", episodes.size());
                        return Promise.of(0);
                    }
                    return procedureInducer.induce(episodes)
                            .then(procedures -> storeProcedures(procedures, 0));
                });
    }

    private Promise<Integer> storeProcedures(List<EnhancedProcedure> procedures, int index) {
        if (index >= procedures.size()) {
            return Promise.of(procedures.size());
        }
        return proceduralManager.storeOrMerge(procedures.get(index))
                .then(stored -> storeProcedures(procedures, index + 1));
    }
}
