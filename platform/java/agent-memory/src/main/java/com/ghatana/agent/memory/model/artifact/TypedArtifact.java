package com.ghatana.agent.memory.model.artifact;

import com.ghatana.agent.memory.model.MemoryItem;
import com.ghatana.agent.memory.model.MemoryItemType;
import org.jetbrains.annotations.NotNull;

/**
 * Base sealed interface for all typed artifacts.
 * Typed artifacts are domain-specific memory items (decisions, tool uses,
 * observations, errors, lessons, entities) that share the MemoryItem
 * envelope while adding structured, typed content.
 *
 * @doc.type interface
 * @doc.purpose Base for typed artifact memory items
 * @doc.layer agent-memory
 */
public sealed interface TypedArtifact extends MemoryItem
        permits Decision, ToolUse, Observation, ErrorArtifact, Lesson, Entity {

    /** The specific artifact type. */
    @NotNull ArtifactType getArtifactType();

    /** Human-readable summary of this artifact. */
    @NotNull String getSummary();

    @Override
    default @NotNull MemoryItemType getType() {
        return MemoryItemType.ARTIFACT;
    }
}
