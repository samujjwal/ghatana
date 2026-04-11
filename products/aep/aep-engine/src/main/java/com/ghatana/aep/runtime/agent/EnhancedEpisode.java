package com.ghatana.agent.memory.model.episode;

import com.ghatana.agent.memory.model.*;
import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Enhanced episode that implements the canonical MemoryItem interface.
 * Extends the existing Episode concept with provenance, embedding, validity,
 * links, typed input/output, tool executions, cost metrics, and redaction level.
 *
 * <p><b>Backward-compatible</b> with the existing {@code Episode} model in
 * agent-framework via {@link EpisodeBuilder#fromLegacyEpisode}.
 *
 * @doc.type value-object
 * @doc.purpose Enhanced episodic memory item
 * @doc.layer agent-memory
 */
@Value
@Builder
public class EnhancedEpisode implements MemoryItem {

    @NotNull String id;
    @Builder.Default @NotNull MemoryItemType type = MemoryItemType.EPISODE;
    @Builder.Default @NotNull Instant createdAt = Instant.now();
    @Builder.Default @NotNull Instant updatedAt = Instant.now();
    @Nullable Instant expiresAt;
    @Builder.Default @NotNull Provenance provenance = Provenance.builder().build();
    @Nullable float[] embedding;
    @Builder.Default @NotNull Validity validity = Validity.builder().build();
    @Builder.Default @NotNull List<MemoryLink> links = List.of();
    @Builder.Default @NotNull Map<String, String> labels = Map.of();
    @Builder.Default @NotNull String tenantId = "default";
    @Nullable String sphereId;
    @Builder.Default @NotNull String classification = "INTERNAL";

    // Episode-specific fields (backward-compatible with existing Episode)
    @NotNull String agentId;
    @NotNull String turnId;
    @Builder.Default @NotNull Instant timestamp = Instant.now();
    @NotNull String input;
    @NotNull String output;
    @Nullable String action;
    @Builder.Default @NotNull Map<String, Object> context = Map.of();
    @Builder.Default @NotNull List<String> tags = List.of();
    @Builder.Default double reward = 0.0;

    // Enhanced fields
    @Builder.Default @NotNull List<ToolExecution> toolExecutions = List.of();
    @Builder.Default double cost = 0.0;
    @Builder.Default long latencyMs = 0;
    @Nullable String redactionLevel;

    /**
     * Record of a single tool execution within an episode.
     */
    @Value
    @Builder
    public static class ToolExecution {
        @NotNull String toolName;
        @NotNull String input;
        @Nullable String output;
        long durationMs;
        boolean success;
        @Nullable String errorMessage;
    }
}
