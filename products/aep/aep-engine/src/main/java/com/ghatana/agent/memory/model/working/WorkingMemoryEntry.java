package com.ghatana.agent.memory.model.working;

import com.ghatana.agent.memory.model.*;
import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A single working memory entry that implements MemoryItem for
 * cross-tier queries. Typically ephemeral and not persisted.
 *
 * @doc.type value-object
 * @doc.purpose Working memory entry as MemoryItem
 * @doc.layer agent-memory
 */
@Value
@Builder
public class WorkingMemoryEntry implements MemoryItem {

    @NotNull String id;
    @Builder.Default @NotNull MemoryItemType type = MemoryItemType.WORKING;
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

    // Working-specific fields
    @NotNull String key;
    @NotNull Object value;
    @Builder.Default int priority = 0;
}
