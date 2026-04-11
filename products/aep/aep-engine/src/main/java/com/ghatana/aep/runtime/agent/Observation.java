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
 * An observation extracted from the environment or conversation.
 *
 * @doc.type value-object
 * @doc.purpose Observation artifact for memory
 * @doc.layer agent-memory
 */
@Value
@Builder
public class Observation implements TypedArtifact {

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

    // Observation-specific fields
    @NotNull String summary;
    @NotNull String content;
    @NotNull String source;
    @Builder.Default double significance = 0.5;
    @Builder.Default @NotNull List<String> relatedEntities = List.of();

    @Override
    public @NotNull ArtifactType getArtifactType() {
        return ArtifactType.OBSERVATION;
    }
}
