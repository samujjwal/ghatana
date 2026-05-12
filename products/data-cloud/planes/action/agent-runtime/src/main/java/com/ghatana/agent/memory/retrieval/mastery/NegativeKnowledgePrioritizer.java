/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.memory.retrieval.mastery;

import com.ghatana.agent.memory.retrieval.mastery.RetrievedContext.MemoryItem;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Prioritizes negative knowledge items in retrieval results.
 *
 * <p>Negative knowledge (what not to do) is placed at the highest
 * priority for safety reasons.
 *
 * @doc.type class
 * @doc.purpose Prioritizes negative knowledge in retrieval
 * @doc.layer agent-runtime
 * @doc.pattern Prioritizer
 */
public class NegativeKnowledgePrioritizer {

    /**
     * Separates and prioritizes negative knowledge from other memory items.
     *
     * @param items all memory items
     * @return prioritized list with negative knowledge first
     */
    @NotNull
    public List<MemoryItem> prioritize(@NotNull List<MemoryItem> items) {
        List<MemoryItem> negativeKnowledge = items.stream()
                .filter(this::isNegativeKnowledge)
                .toList();

        List<MemoryItem> otherItems = items.stream()
                .filter(item -> !isNegativeKnowledge(item))
                .toList();

        // Concatenate: negative knowledge first, then other items
        return java.util.stream.Stream.concat(
                negativeKnowledge.stream(),
                otherItems.stream()
        ).toList();
    }

    /**
     * Checks if a memory item represents negative knowledge.
     *
     * @param item the memory item
     * @return true if negative knowledge
     */
    private boolean isNegativeKnowledge(@NotNull MemoryItem item) {
        String type = item.type();
        String negativeFlag = item.metadata().get("negativeKnowledge");

        return "NEGATIVE_KNOWLEDGE".equals(type)
                || "true".equalsIgnoreCase(negativeFlag)
                || "prohibited".equalsIgnoreCase(item.metadata().get("action"));
    }
}
