package com.ghatana.agent.memory.store.procedural;

import com.ghatana.agent.memory.model.MemoryItemType;
import com.ghatana.agent.memory.model.procedure.EnhancedProcedure;
import com.ghatana.agent.memory.store.MemoryPlane;
import com.ghatana.agent.memory.store.ScoredMemoryItem;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Selects the most appropriate procedure for a given situation
 * using semantic search and confidence-based ranking.
 *
 * <p>Selection strategy:
 * <ol>
 *   <li>Semantic search finds candidate procedures</li>
 *   <li>Filter by minimum confidence threshold</li>
 *   <li>Rank by composite score: relevance × confidence × recency</li>
 *   <li>Return the top-1 procedure</li>
 * </ol>
 *
 * @doc.type class
 * @doc.purpose Procedure selection by situation
 * @doc.layer agent-memory
 */
public class ProcedureSelector {

    private static final Logger log = LoggerFactory.getLogger(ProcedureSelector.class);

    private static final double DEFAULT_MIN_CONFIDENCE = 0.5;
    private static final int DEFAULT_K = 10;

    private final MemoryPlane memoryPlane;
    private final double minConfidence;

    public ProcedureSelector(@NotNull MemoryPlane memoryPlane) {
        this(memoryPlane, DEFAULT_MIN_CONFIDENCE);
    }

    public ProcedureSelector(@NotNull MemoryPlane memoryPlane, double minConfidence) {
        this.memoryPlane = Objects.requireNonNull(memoryPlane, "memoryPlane");
        this.minConfidence = minConfidence;
    }

    /**
     * Selects the best procedure for the given situation based on semantic
     * similarity and confidence.
     *
     * @param situation Natural language description of the situation
     * @return The best matching procedure, or empty if none found
     */
    @NotNull
    public Promise<Optional<EnhancedProcedure>> selectBest(@NotNull String situation) {
        return memoryPlane.searchSemantic(
                        situation,
                        List.of(MemoryItemType.PROCEDURE),
                        DEFAULT_K,
                        null,
                        null)
                .map(scored -> scored.stream()
                        .filter(s -> s.getItem() instanceof EnhancedProcedure)
                        .filter(s -> ((EnhancedProcedure) s.getItem()).getConfidence() >= minConfidence)
                        .max(Comparator.comparingDouble(s -> compositeScore(s, (EnhancedProcedure) s.getItem())))
                        .map(s -> (EnhancedProcedure) s.getItem()));
    }

    /**
     * Composite score: semantic relevance × procedure confidence
     */
    private double compositeScore(ScoredMemoryItem scored, EnhancedProcedure proc) {
        double relevance = scored.getScore();
        double confidence = proc.getConfidence();
        return relevance * 0.6 + confidence * 0.4;
    }
}
