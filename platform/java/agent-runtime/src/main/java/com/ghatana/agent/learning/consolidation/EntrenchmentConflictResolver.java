package com.ghatana.agent.learning.consolidation;

import com.ghatana.agent.memory.model.MemoryItem;
import com.ghatana.agent.memory.model.Provenance;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves conflicts using entrenchment ordering — items that have been
 * used more frequently and confirmed more often have higher entrenchment
 * and win conflicts.
 *
 * <p>Based on the belief revision principle: more entrenched beliefs
 * are harder to dislodge.
 *
 * @doc.type class
 * @doc.purpose Entrenchment-based conflict resolution
 * @doc.layer agent-learning
 */
public class EntrenchmentConflictResolver implements ConflictResolver {

    private static final Logger log = LoggerFactory.getLogger(EntrenchmentConflictResolver.class);

    @Override
    @NotNull
    public Promise<MemoryItem> resolve(@NotNull MemoryConflict conflict) {
        MemoryItem existing = conflict.getExistingItem();
        MemoryItem incoming = conflict.getIncomingItem();

        double existingEntrenchment = computeEntrenchment(existing);
        double incomingEntrenchment = computeEntrenchment(incoming);

        MemoryItem winner;
        if (incomingEntrenchment > existingEntrenchment) {
            log.info("Incoming item wins conflict: {} (entrenchment {} > {})",
                    incoming.getId(), incomingEntrenchment, existingEntrenchment);
            winner = incoming;
        } else {
            log.info("Existing item wins conflict: {} (entrenchment {} >= {})",
                    existing.getId(), existingEntrenchment, incomingEntrenchment);
            winner = existing;
        }

        return Promise.of(winner);
    }

    /**
     * Computes entrenchment based on:
     * <ul>
     *   <li>Confidence (weight: 0.4)</li>
     *   <li>Provenance source reliability (weight: 0.3)</li>
     *   <li>Recency (weight: 0.3)</li>
     * </ul>
     */
    private double computeEntrenchment(MemoryItem item) {
        double confidence = item.getValidity() != null
                ? item.getValidity().getConfidence()
                : 0.5;

        double sourceReliability = 0.5; // default
        if (item.getProvenance() != null) {
            sourceReliability = switch (item.getProvenance().getConfidenceSource()) {
                case HUMAN -> 0.95;
                case TOOL_OUTPUT -> 0.80;
                case CONSOLIDATION -> 0.70;
                case LLM_INFERENCE -> 0.60;
                case STATISTICAL -> 0.40;
            };
        }

        // Recency: exponential decay from creation time (7-day half-life)
        double ageHours = java.time.Duration.between(item.getCreatedAt(), java.time.Instant.now()).toHours();
        double recency = Math.exp(-0.693 * ageHours / (7.0 * 24.0));

        return 0.4 * confidence + 0.3 * sourceReliability + 0.3 * recency;
    }
}
