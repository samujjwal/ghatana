/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.memory.retrieval;

import com.ghatana.agent.mastery.MasteryItem;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * Prioritizer that applies negative knowledge filters to avoid known failure modes.
 *
 * @doc.type class
 * @doc.purpose Negative knowledge prioritization
 * @doc.layer agent-core
 * @doc.pattern Service
 */
public final class NegativeKnowledgePrioritizer {

    /**
     * Filters out memory items associated with known failure modes.
     *
     * @param items memory items to filter
     * @param failureModeIds known failure mode IDs to exclude
     * @return filtered list of items
     */
    @NotNull
    public static List<MasteryItem> filterByNegativeKnowledge(
            @NotNull List<MasteryItem> items,
            @NotNull List<String> failureModeIds) {
        Objects.requireNonNull(items, "items must not be null");
        Objects.requireNonNull(failureModeIds, "failureModeIds must not be null");

        return items.stream()
                .filter(item -> !hasKnownFailureMode(item, failureModeIds))
                .toList();
    }

    /**
     * Checks if a mastery item has any of the specified failure modes.
     *
     * @param item mastery item
     * @param failureModeIds failure mode IDs to check
     * @return true if item has any of the failure modes
     */
    private static boolean hasKnownFailureMode(@NotNull MasteryItem item, @NotNull List<String> failureModeIds) {
        for (String failureModeId : failureModeIds) {
            if (item.knownFailureModeIds().contains(failureModeId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Prioritizes items with negative knowledge awareness, demoting those with failure modes.
     *
     * @param items memory items to prioritize
     * @return prioritized list of items
     */
    @NotNull
    public static List<MasteryItem> prioritizeWithNegativeKnowledge(@NotNull List<MasteryItem> items) {
        Objects.requireNonNull(items, "items must not be null");

        return items.stream()
                .sorted((a, b) -> {
                    boolean aHasFailures = !a.knownFailureModeIds().isEmpty();
                    boolean bHasFailures = !b.knownFailureModeIds().isEmpty();

                    if (aHasFailures && !bHasFailures) {
                        return 1; // a has failures, b doesn't - demote a
                    }
                    if (!aHasFailures && bHasFailures) {
                        return -1; // a doesn't have failures, b does - prioritize a
                    }
                    return 0;
                })
                .toList();
    }
}
