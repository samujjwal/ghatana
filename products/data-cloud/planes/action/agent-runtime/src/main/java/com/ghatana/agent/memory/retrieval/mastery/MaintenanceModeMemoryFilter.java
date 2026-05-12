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
 * Filters memory items based on maintenance mode requirements.
 *
 * <p>Maintenance-only items are only included when explicitly requested
 * or when the execution context matches their legacy version.
 *
 * @doc.type class
 * @doc.purpose Filters memory items by maintenance mode
 * @doc.layer agent-runtime
 * @doc.pattern Filter
 */
public class MaintenanceModeMemoryFilter {

    /**
     * Filters out maintenance-only items unless explicitly included.
     *
     * @param items the items to filter
     * @param includeMaintenance whether to include maintenance-only items
     * @return filtered list of items
     */
    @NotNull
    public List<MemoryItem> filter(@NotNull List<MemoryItem> items, boolean includeMaintenance) {
        if (includeMaintenance) {
            return items;
        }

        return items.stream()
                .filter(item -> !isMaintenanceOnly(item))
                .toList();
    }

    /**
     * Checks if a memory item is in maintenance-only mode.
     *
     * @param item the memory item
     * @return true if maintenance-only
     */
    private boolean isMaintenanceOnly(@NotNull MemoryItem item) {
        String masteryState = item.metadata().get("masteryState");
        return MasteryState.MAINTENANCE_ONLY.name().equals(masteryState);
    }
}
