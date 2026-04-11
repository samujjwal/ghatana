package com.ghatana.agent.learning.consolidation;

import com.ghatana.agent.memory.model.MemoryItem;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * Interface for resolving conflicts between memory items
 * during consolidation.
 *
 * @doc.type interface
 * @doc.purpose Memory conflict resolution SPI
 * @doc.layer agent-learning
 */
public interface ConflictResolver {

    /**
     * Resolves a conflict between two memory items.
     *
     * @param conflict The conflict to resolve
     * @return The winning item
     */
    @NotNull Promise<MemoryItem> resolve(@NotNull MemoryConflict conflict);
}
