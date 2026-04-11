package com.ghatana.agent.memory.model;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a directed link between two memory items.
 * Links enable knowledge graphs over the memory plane:
 * facts can support or contradict each other, procedures
 * can be derived from episodes, etc.
 *
 * @doc.type value-object
 * @doc.purpose Inter-memory-item relationships
 * @doc.layer agent-memory
 */
@Value
@Builder
public class MemoryLink {

    /** ID of the target memory item. */
    @NotNull
    String targetItemId;

    /** Type of relationship. */
    @NotNull
    LinkType linkType;

    /** Strength of relationship in [0.0, 1.0]. */
    @Builder.Default
    double strength = 1.0;

    /** Human-readable description of why this link exists. */
    @Nullable
    String description;
}
