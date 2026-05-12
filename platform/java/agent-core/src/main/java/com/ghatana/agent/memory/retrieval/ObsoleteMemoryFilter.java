/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.memory.retrieval;

import com.ghatana.agent.context.version.VersionContext;
import com.ghatana.agent.mastery.MasteryItem;
import com.ghatana.agent.mastery.MasteryState;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * Filter that excludes obsolete memory items from retrieval.
 *
 * @doc.type class
 * @doc.purpose Obsolete memory filtering
 * @doc.layer agent-core
 * @doc.pattern Service
 */
public final class ObsoleteMemoryFilter {

    /**
     * Filters out obsolete memory items.
     *
     * @param items memory items to filter
     * @return filtered list of items
     */
    @NotNull
    public static List<MasteryItem> filterObsolete(@NotNull List<MasteryItem> items) {
        Objects.requireNonNull(items, "items must not be null");
        return items.stream()
                .filter(item -> item.state() != MasteryState.OBSOLETE)
                .filter(item -> item.state() != MasteryState.RETIRED)
                .toList();
    }

    /**
     * Returns true if the item is obsolete or retired.
     *
     * @param item mastery item
     * @return true if obsolete or retired
     */
    public static boolean isObsolete(@NotNull MasteryItem item) {
        return item.state() == MasteryState.OBSOLETE || item.state() == MasteryState.RETIRED;
    }

    /**
     * Filters items based on version scope obsolescence.
     *
     * @param items memory items to filter
     * @return filtered list of items
     */
    @NotNull
    public static List<MasteryItem> filterByVersionObsolescence(@NotNull List<MasteryItem> items, @NotNull VersionContext versionContext) {
        Objects.requireNonNull(items, "items must not be null");
        return items.stream()
                .filter(item -> !item.versionScope().isObsolete(versionContext))
                .toList();
    }
}
