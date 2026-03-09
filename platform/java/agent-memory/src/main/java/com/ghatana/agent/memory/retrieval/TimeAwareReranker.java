package com.ghatana.agent.memory.retrieval;

import com.ghatana.agent.memory.model.MemoryItem;
import com.ghatana.agent.memory.store.ScoredMemoryItem;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reranks retrieved items by combining relevance score with recency
 * and importance weights.
 *
 * <p><b>Formula:</b>
 * {@code finalScore = relevanceScore * recencyWeight(age, halfLife) * importanceWeight}
 *
 * @doc.type class
 * @doc.purpose Time-aware reranking of retrieval results
 * @doc.layer agent-memory
 */
public class TimeAwareReranker {

    /** Default half-life in days for recency decay. */
    private static final double DEFAULT_HALF_LIFE_DAYS = 7.0;

    private final double halfLifeDays;

    public TimeAwareReranker(double halfLifeDays) {
        this.halfLifeDays = halfLifeDays;
    }

    public TimeAwareReranker() {
        this(DEFAULT_HALF_LIFE_DAYS);
    }

    /**
     * Reranks items by combining the original score with recency and importance.
     *
     * @param items Items to rerank
     * @return Reranked items with updated scores
     */
    @NotNull
    public List<ScoredMemoryItem> rerank(@NotNull List<ScoredMemoryItem> items) {
        Instant now = Instant.now();

        return items.stream()
                .map(scored -> {
                    MemoryItem item = scored.getItem();
                    double relevance = scored.getScore();
                    double recency = computeRecencyWeight(item.getCreatedAt(), now);
                    double importance = computeImportanceWeight(item);
                    double finalScore = relevance * recency * importance;

                    Map<String, String> metadata = new java.util.HashMap<>(scored.getRetrievalMetadata());
                    metadata.put("recency_weight", String.format("%.4f", recency));
                    metadata.put("importance_weight", String.format("%.4f", importance));
                    metadata.put("final_score", String.format("%.4f", finalScore));

                    return new ScoredMemoryItem(item, finalScore, Map.copyOf(metadata));
                })
                .sorted(Comparator.comparingDouble(ScoredMemoryItem::getScore).reversed())
                .collect(Collectors.toList());
    }

    private double computeRecencyWeight(Instant createdAt, Instant now) {
        long ageDays = ChronoUnit.DAYS.between(createdAt, now);
        if (ageDays <= 0) return 1.0;
        // Exponential decay: e^(-ln(2) * age / halfLife)
        return Math.exp(-Math.log(2) * ageDays / halfLifeDays);
    }

    private double computeImportanceWeight(MemoryItem item) {
        double base = item.getValidity().getConfidence();
        // Boost items that have links (more connected = more important)
        double linkBoost = 1.0 + (item.getLinks().size() * 0.05);
        return Math.min(base * linkBoost, 1.0);
    }
}
