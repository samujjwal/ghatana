/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.memory.retrieval;

import com.ghatana.agent.mastery.MasteryItem;
import com.ghatana.agent.mastery.MasteryState;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * Filter that excludes memory items in maintenance mode from active retrieval.
 *
 * @doc.type class
 * @doc.purpose Maintenance mode memory filtering
 * @doc.layer agent-core
 * @doc.pattern Service
 */
public final class MaintenanceModeMemoryFilter {

    /**
     * Filters out memory items in maintenance mode.
     *
     * @param items memory items to filter
     * @return filtered list of items
     */
    @NotNull
    public static List<MasteryItem> filterMaintenanceOnly(@NotNull List<MasteryItem> items) {
        Objects.requireNonNull(items, "items must not be null");
        return items.stream()
                .filter(item -> item.state() != MasteryState.MAINTENANCE_ONLY)
                .toList();
    }

    /**
     * Includes only maintenance mode items.
     *
     * @param items memory items to filter
     * @return list of maintenance mode items
     */
    @NotNull
    public static List<MasteryItem> filterMaintenanceModeOnly(@NotNull List<MasteryItem> items) {
        Objects.requireNonNull(items, "items must not be null");
        return items.stream()
                .filter(item -> item.state() == MasteryState.MAINTENANCE_ONLY)
                .toList();
    }

    /**
     * Returns true if the item is in maintenance mode.
     *
     * @param item mastery item
     * @return true if maintenance mode
     */
    public static boolean isMaintenanceMode(@NotNull MasteryItem item) {
        return item.state() == MasteryState.MAINTENANCE_ONLY;
    }
}
