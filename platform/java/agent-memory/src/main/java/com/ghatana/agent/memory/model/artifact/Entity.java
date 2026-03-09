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
 * A named entity extracted from agent context (person, tool, concept, etc.).
 *
 * @doc.type value-object
 * @doc.purpose Entity artifact for memory
 * @doc.layer agent-memory
 */
@Value
@Builder
public class Entity implements TypedArtifact {

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

    // Entity-specific fields
    @NotNull String summary;
    @NotNull String entityType;
    @NotNull String name;
    @Builder.Default @NotNull Map<String, String> attributes = Map.of();
    @Builder.Default @NotNull Map<String, String> externalIds = Map.of();
    @Builder.Default @NotNull List<String> relationships = List.of();

    @Override
    public @NotNull ArtifactType getArtifactType() {
        return ArtifactType.ENTITY;
    }
}
