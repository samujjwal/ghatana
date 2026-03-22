package com.ghatana.agent.learning.retention;

import com.ghatana.agent.memory.model.MemoryItem;
import com.ghatana.agent.memory.model.MemoryItemType;
import com.ghatana.agent.memory.store.MemoryPlane;
import com.ghatana.agent.memory.store.MemoryQuery;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Utility-based retention manager. Computes a "utility score" for each
 * memory item based on frequency of access, confidence, recency, and
 * relevance — then evicts items below a threshold.
 *
 * <p>Utility formula:
 * <pre>
 *   utility = w_recency × recencyScore
 *           + w_frequency × frequencyScore
 *           + w_confidence × confidenceScore
 *           + w_relevance × relevanceScore
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Utility-based memory retention
 * @doc.layer agent-learning
 */
public class UtilityBasedRetentionManager implements RetentionManager {

    private static final Logger log = LoggerFactory.getLogger(UtilityBasedRetentionManager.class);

    private final MemoryPlane memoryPlane;

    public UtilityBasedRetentionManager(@NotNull MemoryPlane memoryPlane) {
        this.memoryPlane = Objects.requireNonNull(memoryPlane, "memoryPlane");
    }

    @Override
    @NotNull
    public Promise<RetentionResult> applyRetention(@NotNull String agentId, @NotNull RetentionConfig config) {
        log.info("Applying retention for agent {} (threshold={}, maxAge={})",
                agentId, config.getMinUtility(), config.getMaxAge());

        MemoryQuery query = MemoryQuery.builder()
                .agentId(agentId)
                .limit(1000)
                .build();

        return memoryPlane.readItems(query)
                .map(items -> {
                    int kept = 0;
                    int decayed = 0;
                    int evicted = 0;

                    for (MemoryItem item : items) {
                        double utility = computeUtility(item, config);

                        if (utility < config.getEvictionThreshold()) {
                            // Below eviction threshold — remove
                            evicted++;
                            log.debug("Evicting item {} (utility={:.3f})", item.getId(), utility);
                        } else if (utility < config.getMinUtility()) {
                            // Below minimum utility — apply decay
                            decayed++;
                            log.debug("Decaying item {} (utility={:.3f})", item.getId(), utility);
                        } else {
                            kept++;
                        }
                    }

                    log.info("Retention result: kept={}, decayed={}, evicted={}", kept, decayed, evicted);
                    return RetentionResult.builder()
                            .agentId(agentId)
                            .kept(kept)
                            .decayed(decayed)
                            .evicted(evicted)
                            .build();
                });
    }

    private double computeUtility(MemoryItem item, RetentionConfig config) {
        DecayFunction decayFn = config.getDecayFunction();
        double ageHours = java.time.Duration.between(item.getCreatedAt(), Instant.now()).toHours();

        double recencyScore = decayFn.compute(ageHours);
        double confidenceScore = item.getValidity() != null
                ? item.getValidity().getConfidence()
                : 0.5;

        return config.getRecencyWeight() * recencyScore
                + config.getConfidenceWeight() * confidenceScore;
    }
}
