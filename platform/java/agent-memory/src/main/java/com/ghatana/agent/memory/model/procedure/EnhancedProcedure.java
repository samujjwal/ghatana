package com.ghatana.agent.memory.model.procedure;

import com.ghatana.agent.memory.model.*;
import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Enhanced procedure that implements the canonical MemoryItem interface.
 * Extends the existing Policy concept with versioned steps, success rate,
 * context prerequisites, environment constraints, and parameterized templates.
 *
 * @doc.type value-object
 * @doc.purpose Enhanced procedural memory item
 * @doc.layer agent-memory
 */
@Value
@Builder
public class EnhancedProcedure implements MemoryItem {

    @NotNull String id;
    @Builder.Default @NotNull MemoryItemType type = MemoryItemType.PROCEDURE;
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

    // Convenience copies (also available via provenance/validity)
    @Nullable String agentId;
    @Builder.Default double confidence = 0.0;
    @Builder.Default @NotNull List<String> tags = List.of();
    @Nullable Instant lastUsedAt;

    // Procedure-specific fields (backward-compatible with existing Policy)
    @NotNull String situation;
    @NotNull String action;
    @Builder.Default int useCount = 0;
    @Builder.Default @NotNull List<String> learnedFromEpisodes = List.of();

    // Enhanced fields
    @Builder.Default int version = 1;
    @Builder.Default @NotNull List<ProcedureStep> steps = List.of();
    @Builder.Default double successRate = 0.0;
    @Builder.Default @NotNull Map<String, Object> prerequisites = Map.of();
    @Builder.Default @NotNull Map<String, Object> environmentConstraints = Map.of();
    @Builder.Default @NotNull List<ProcedureVersion> versionHistory = List.of();
}
