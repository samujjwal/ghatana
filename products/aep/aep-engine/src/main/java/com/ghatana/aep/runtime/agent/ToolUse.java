package com.ghatana.agent.memory.model.artifact;

import com.ghatana.agent.memory.model.*;
import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Records a tool invocation with input, output, timing, and success status.
 *
 * @doc.type value-object
 * @doc.purpose Tool use artifact for memory
 * @doc.layer agent-memory
 */
@Value
@Builder
public class ToolUse implements TypedArtifact {

    @NotNull String id;
    @NotNull Instant createdAt;
    @Builder.Default @NotNull Instant updatedAt = Instant.now();
    @Nullable Instant expiresAt;
    @NotNull Provenance provenance;
    @Nullable float[] embedding;
    @NotNull Validity validity;
    @Builder.Default @NotNull List<MemoryLink> links = List.of();
    @Builder.Default @NotNull Map<String, String> labels = Map.of();
    @NotNull String tenantId;
    @Nullable String sphereId;
    @Builder.Default @NotNull String classification = "INTERNAL";

    // ToolUse-specific fields
    @NotNull String summary;
    @NotNull String toolName;
    @NotNull String input;
    @Nullable String output;
    long durationMs;
    boolean success;
    @Nullable String errorMessage;

    @Override
    public @NotNull ArtifactType getArtifactType() {
        return ArtifactType.TOOL_USE;
    }
}
