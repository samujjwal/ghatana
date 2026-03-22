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
 * A lesson learned from agent experience, generalizable across contexts.
 *
 * @doc.type value-object
 * @doc.purpose Lesson artifact for memory
 * @doc.layer agent-memory
 */
@Value
@Builder
public class Lesson implements TypedArtifact {

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

    // Lesson-specific fields
    @NotNull String summary;
    @NotNull String condition;
    @NotNull String insight;
    @Builder.Default double applicability = 1.0;
    @Builder.Default @NotNull List<String> derivedFromEpisodes = List.of();
    @Builder.Default @NotNull String verificationStatus = "UNVERIFIED";

    @Override
    public @NotNull ArtifactType getArtifactType() {
        return ArtifactType.LESSON;
    }
}
