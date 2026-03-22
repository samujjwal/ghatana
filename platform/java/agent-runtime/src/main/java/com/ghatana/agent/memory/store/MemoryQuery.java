package com.ghatana.agent.memory.store;

import com.ghatana.agent.memory.model.MemoryItemType;
import com.ghatana.agent.memory.model.ValidityStatus;
import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * Query parameters for reading memory items.
 * All fields are optional — unset fields are not filtered.
 *
 * @doc.type value-object
 * @doc.purpose Memory query specification
 * @doc.layer agent-memory
 */
@Value
@Builder
public class MemoryQuery {

    /** Filter by memory item types. */
    @Nullable List<MemoryItemType> itemTypes;

    /** Filter by tenant. */
    @Nullable String tenantId;

    /** Filter by agent. */
    @Nullable String agentId;

    /** Filter by sphere (privacy boundary). */
    @Nullable String sphereId;

    /** Time range start (inclusive). */
    @Nullable Instant startTime;

    /** Time range end (inclusive). */
    @Nullable Instant endTime;

    /** Filter by labels/tags. */
    @Nullable List<String> tags;

    /** Minimum confidence threshold. */
    @Builder.Default double minConfidence = 0.0;

    /** Filter by validity status. */
    @Nullable List<ValidityStatus> validityStatuses;

    /** Full-text search query. */
    @Nullable String textQuery;

    /** Maximum results. */
    @Builder.Default int limit = 50;

    /** Offset for pagination. */
    @Builder.Default int offset = 0;
}
