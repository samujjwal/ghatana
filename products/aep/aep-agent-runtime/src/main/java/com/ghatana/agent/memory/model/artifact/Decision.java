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
 * A decision made by an agent, including rationale, alternatives
 * considered, and the chosen option.
 *
 * @doc.type value-object
 * @doc.purpose Decision artifact for memory
 * @doc.layer agent-memory
 */
@Value
@Builder
public class Decision implements TypedArtifact {

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

    // Decision-specific fields
    @NotNull String summary;
    @NotNull String rationale;
    @Builder.Default @NotNull List<String> alternatives = List.of();
    @NotNull String chosenOption;
    double confidence;
    @Nullable Map<String, Object> context;

    @Override
    public @NotNull ArtifactType getArtifactType() {
        return ArtifactType.DECISION;
    }
}
