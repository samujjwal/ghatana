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
 * Records an error encountered during agent execution.
 * Named ErrorArtifact to avoid collision with java.lang.Error.
 *
 * @doc.type value-object
 * @doc.purpose Error artifact for memory
 * @doc.layer agent-memory
 */
@Value
@Builder
public class ErrorArtifact implements TypedArtifact {

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

    // Error-specific fields
    @NotNull String summary;
    @NotNull String errorType;
    @NotNull String message;
    @Nullable String stackTrace;
    @Nullable String recoveryAction;
    @Nullable String category;

    @Override
    public @NotNull ArtifactType getArtifactType() {
        return ArtifactType.ERROR;
    }
}
