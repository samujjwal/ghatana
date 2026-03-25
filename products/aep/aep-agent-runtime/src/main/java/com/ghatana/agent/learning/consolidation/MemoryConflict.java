package com.ghatana.agent.learning.consolidation;

import com.ghatana.agent.memory.model.MemoryItem;
import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a conflict between two memory items during consolidation.
 * For example, two facts with the same subject-predicate but different objects.
 *
 * @doc.type value-object
 * @doc.purpose Consolidation conflict between memory items
 * @doc.layer agent-learning
 */
@Value
@Builder
public class MemoryConflict {

    /** The existing item already in memory. */
    @NotNull
    MemoryItem existingItem;

    /** The incoming item that conflicts with the existing one. */
    @NotNull
    MemoryItem incomingItem;

    /** Type of conflict: CONTRADICTION, DUPLICATION, SUPERSESSION. */
    @NotNull
    @Builder.Default
    ConflictType type = ConflictType.CONTRADICTION;

    /** Optional description of what specifically conflicts. */
    @Nullable
    String description;

    public enum ConflictType {
        CONTRADICTION,
        DUPLICATION,
        SUPERSESSION
    }
}
