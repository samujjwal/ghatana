/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.memory.retrieval.mastery;

import com.ghatana.agent.mastery.MasteryState;
import com.ghatana.agent.memory.retrieval.mastery.RetrievedContext.MemoryItem;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Filters out obsolete and retired memory items.
 *
 * <p>Obsolete and retired items are excluded from retrieval by default
 * to prevent execution of deprecated procedures.
 *
 * @doc.type class
 * @doc.purpose Filters out obsolete memory items
 * @doc.layer agent-runtime
 * @doc.pattern Filter
 */
public class ObsoleteMemoryFilter {

    /**
     * Filters out obsolete and retired items unless explicitly included.
     *
     * @param items the items to filter
     * @param includeObsolete whether to include obsolete items
     * @return filtered list of items
     */
    @NotNull
    public List<MemoryItem> filter(@NotNull List<MemoryItem> items, boolean includeObsolete) {
        if (includeObsolete) {
            return items;
        }

        return items.stream()
                .filter(item -> !isObsolete(item))
                .toList();
    }

    /**
     * Checks if a memory item is obsolete or retired.
     *
     * @param item the memory item
     * @return true if obsolete or retired
     */
    private boolean isObsolete(@NotNull MemoryItem item) {
        String masteryState = item.metadata().get("masteryState");
        return MasteryState.OBSOLETE.name().equals(masteryState)
                || MasteryState.RETIRED.name().equals(masteryState);
    }
}
