/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.memory.retrieval;

import com.ghatana.agent.memory.model.MemoryItem;
import com.ghatana.agent.memory.model.MemoryQuery;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Bundle of retrieved memory items with trace information and retrieval decisions.
 *
 * @param selectedItems items that passed filters and were selected for retrieval
 * @param rejectedItems items that were rejected with reasons
 * @param trace trace information including query details, counts, and rejection reasons
 * @param query the original query that produced this bundle
 * @param decisions retrieval decisions explaining why each item was included or excluded
 *
 * @doc.type record
 * @doc.purpose Bundle of retrieved memory items with trace and decisions
 * @doc.layer agent-core
 * @doc.pattern ValueObject
 */
public record RetrievalBundle(
        @NotNull List<MemoryItem> selectedItems,
        @NotNull List<MemoryRetrievalService.RejectedItem> rejectedItems,
        @NotNull Map<String, Object> trace,
        @NotNull MemoryQuery query,
        @NotNull List<RetrievalDecision> decisions
) {
    public RetrievalBundle {
        selectedItems = List.copyOf(selectedItems);
        rejectedItems = List.copyOf(rejectedItems);
        trace = Map.copyOf(trace);
        decisions = List.copyOf(decisions);
    }

    /**
     * Returns the total number of items considered (selected + rejected).
     *
     * @return total item count
     */
    public int totalCount() {
        return selectedItems.size() + rejectedItems.size();
    }

    /**
     * Returns the selection rate (selected / total).
     *
     * @return selection rate between 0.0 and 1.0
     */
    public double selectionRate() {
        int total = totalCount();
        return total > 0 ? (double) selectedItems.size() / total : 0.0;
    }

    /**
     * Returns retrieval decisions for selected items only.
     *
     * @return list of decisions for included items
     */
    @NotNull
    public List<RetrievalDecision> includedDecisions() {
        return decisions.stream()
                .filter(RetrievalDecision::included)
                .toList();
    }

    /**
     * Returns retrieval decisions for rejected items only.
     *
     * @return list of decisions for excluded items
     */
    @NotNull
    public List<RetrievalDecision> excludedDecisions() {
        return decisions.stream()
                .filter(d -> !d.included())
                .toList();
    }
}
