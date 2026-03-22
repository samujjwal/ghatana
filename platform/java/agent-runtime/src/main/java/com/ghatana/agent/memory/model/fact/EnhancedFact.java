package com.ghatana.agent.memory.model.fact;

import com.ghatana.agent.memory.model.*;
import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Enhanced semantic fact that implements the canonical MemoryItem interface.
 * Extends the existing Fact concept with provenance, confidence decay,
 * TTL, version history, and inter-fact links.
 *
 * <p>Facts represent SPO (subject-predicate-object) triples with typed
 * confidence and versioning for truth maintenance.
 *
 * @doc.type value-object
 * @doc.purpose Enhanced semantic memory item
 * @doc.layer agent-memory
 */
@Value
@Builder
public class EnhancedFact implements MemoryItem {

    @NotNull String id;
    @Builder.Default @NotNull MemoryItemType type = MemoryItemType.FACT;
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

    // Fact-specific fields (backward-compatible with existing Fact)
    @NotNull String subject;
    @NotNull String predicate;
    @NotNull String object;
    @Builder.Default @NotNull String source = "unknown";
    @Builder.Default @NotNull Instant learnedAt = Instant.now();

    // Enhanced fields
    @Builder.Default int version = 1;
    @Builder.Default @NotNull List<FactVersion> versionHistory = List.of();
}
