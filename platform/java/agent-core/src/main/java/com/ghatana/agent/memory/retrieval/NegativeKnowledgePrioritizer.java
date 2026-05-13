/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.memory.retrieval;

import com.ghatana.agent.memory.model.MemoryItem;
import com.ghatana.agent.memory.model.MemoryItemType;
import com.ghatana.agent.mastery.MasteryItem;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Prioritizer that applies negative knowledge filters to avoid known failure modes.
 * Negative knowledge (known failure modes and anti-patterns) is prioritized before procedures
 * to ensure the agent avoids repeating mistakes.
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
     * Filters out memory items associated with known failure modes from memory items.
     *
     * @param items memory items to filter
     * @param failureModeIds known failure mode IDs to exclude
     * @return filtered list of items
     */
    @NotNull
    public static List<MemoryItem> filterMemoryItemsByNegativeKnowledge(
            @NotNull List<MemoryItem> items,
            @NotNull List<String> failureModeIds) {
        Objects.requireNonNull(items, "items must not be null");
        Objects.requireNonNull(failureModeIds, "failureModeIds must not be null");

        return items.stream()
                .filter(item -> !hasKnownFailureModeInMemory(item, failureModeIds))
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
     * Checks if a memory item has any of the specified failure modes in its labels.
     *
     * @param item memory item
     * @param failureModeIds failure mode IDs to check
     * @return true if item has any of the failure modes
     */
    private static boolean hasKnownFailureModeInMemory(@NotNull MemoryItem item, @NotNull List<String> failureModeIds) {
        if (item.getLabels() == null) {
            return false;
        }
        for (String failureModeId : failureModeIds) {
            if (item.getLabels().containsKey("failureMode:" + failureModeId)) {
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

    /**
     * Prioritizes memory items by type, with negative knowledge at the highest priority.
     * Retrieval priority order:
     * 1. Negative knowledge (highest priority - what to avoid)
     * 2. Semantic facts (stable knowledge)
     * 3. Procedures (reusable skills)
     * 4. Episodes (contextual experiences)
     * 5. Task state (workflow patterns)
     * 6. Working memory (ephemeral state)
     * 7. Preferences (user preferences)
     * 8. Artifacts (generic artifacts)
     * 9. Custom types (product-specific)
     *
     * @param items memory items to prioritize
     * @return prioritized list of items
     */
    @NotNull
    public static List<MemoryItem> prioritizeMemoryItemsByType(@NotNull List<MemoryItem> items) {
        Objects.requireNonNull(items, "items must not be null");

        return items.stream()
                .sorted(createTypePriorityComparator())
                .toList();
    }

    /**
     * Creates a comparator for ordering memory items by type priority.
     * Higher priority items come first.
     *
     * @return comparator for ordering
     */
    @NotNull
    private static Comparator<MemoryItem> createTypePriorityComparator() {
        return (item1, item2) -> {
            int priority1 = getTypePriority(item1);
            int priority2 = getTypePriority(item2);
            // Higher priority items come first (reverse compare)
            return Integer.compare(priority2, priority1);
        };
    }

    /**
     * Returns the retrieval priority for a memory item.
     * Higher values indicate higher priority.
     *
     * @param item memory item
     * @return retrieval priority (100-0)
     */
    private static int getTypePriority(@NotNull MemoryItem item) {
        return getTypePriority(item.getType());
    }

    /**
     * Returns the retrieval priority for a memory item type.
     * Higher values indicate higher priority.
     *
     * @param type memory item type
     * @return retrieval priority (100-0)
     */
    private static int getTypePriority(@NotNull MemoryItemType type) {
        return switch (type) {
            case NEGATIVE_KNOWLEDGE -> 100; // Highest priority - avoid known failure modes
            case FACT -> 90; // Stable knowledge
            case PROCEDURE -> 80; // Reusable skills
            case EPISODE -> 60; // Contextual experiences
            case TASK_STATE -> 50; // Workflow patterns
            case WORKING -> 30; // Ephemeral state
            case PREFERENCE -> 40; // User preferences
            case ARTIFACT -> 20; // Generic artifacts
            case CUSTOM -> 10; // Product-specific types
        };
    }

    /**
     * Extracts negative knowledge items from a list of memory items.
     *
     * @param items memory items to extract from
     * @return list of negative knowledge items
     */
    @NotNull
    public static List<MemoryItem> extractNegativeKnowledge(@NotNull List<MemoryItem> items) {
        Objects.requireNonNull(items, "items must not be null");

        return items.stream()
                .filter(item -> item.getType() == MemoryItemType.NEGATIVE_KNOWLEDGE)
                .toList();
    }

    /**
     * Prioritizes memory items with negative knowledge at the front, followed by other items.
     * Negative knowledge items are sorted by their recency (most recent first).
     * Other items maintain their original order.
     *
     * @param items memory items to prioritize
     * @return prioritized list with negative knowledge first
     */
    @NotNull
    public static List<MemoryItem> prioritizeNegativeKnowledgeFirst(@NotNull List<MemoryItem> items) {
        Objects.requireNonNull(items, "items must not be null");

        List<MemoryItem> negativeKnowledge = new ArrayList<>();
        List<MemoryItem> otherItems = new ArrayList<>();

        // Separate negative knowledge from other items
        for (MemoryItem item : items) {
            if (item.getType() == MemoryItemType.NEGATIVE_KNOWLEDGE) {
                negativeKnowledge.add(item);
            } else {
                otherItems.add(item);
            }
        }

        // Sort negative knowledge by recency (most recent first)
        negativeKnowledge.sort(Comparator.comparing(MemoryItem::getCreatedAt).reversed());

        // Combine: negative knowledge first, then other items
        List<MemoryItem> result = new ArrayList<>(negativeKnowledge);
        result.addAll(otherItems);
        return result;
    }
}
