package com.ghatana.agent.memory.retrieval;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * Configuration for context injection formatting.
 *
 * @doc.type value-object
 * @doc.purpose Context injection configuration
 * @doc.layer agent-memory
 */
@Value
@Builder
public class InjectionConfig {

    /** Maximum tokens allowed in the injected context. */
    @Builder.Default int maxTokens = 4000;

    /** Whether to group results by memory tier. */
    @Builder.Default boolean groupByTier = true;

    /** Whether to include provenance information. */
    @Builder.Default boolean includeProvenance = true;

    /** Whether to include confidence scores. */
    @Builder.Default boolean includeConfidence = true;

    /** Whether to mark conflicting items with markers. */
    @Builder.Default boolean includeConflictMarkers = true;

    /** Output format. */
    @NotNull
    @Builder.Default
    Format format = Format.MARKDOWN;

    public enum Format {
        MARKDOWN,
        XML,
        JSON
    }
}
