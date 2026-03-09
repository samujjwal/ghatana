package com.ghatana.virtualorg.memory;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/**
 * An entry in agent memory representing a past task execution.
 *
 * <p><b>Purpose</b><br>
 * Value object storing a searchable memory entry with content, similarity score,
 * task metadata, and outcome. Used for semantic search and experience-based learning.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * MemoryEntry entry = new MemoryEntry(
 *     UUID.randomUUID().toString(),
 *     "Successfully processed transaction T123",
 *     0.85f,
 *     "transaction.process",
 *     true,
 *     Instant.now(),
 *     Map.of("amount", "1000.00", "currency", "USD")
 * );
 * }</pre>
 *
 * <p><b>Validation</b><br>
 * Canonical constructor validates:
 * - id: non-null, non-blank
 * - content: non-null
 * - score: 0.0 to 1.0 range (cosine similarity)
 * - taskType: non-null
 * - timestamp: non-null
 * - metadata: non-null (may be empty map)
 *
 * @param id           unique identifier
 * @param content      the memory content
 * @param score        similarity score (0.0 to 1.0)
 * @param taskType     the type of task this relates to
 * @param success      whether the task was successful
 * @param timestamp    when this memory was created
 * @param metadata     additional metadata
 * @doc.type record
 * @doc.purpose Memory entry value object with similarity score and task metadata
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record MemoryEntry(
        @NotNull String id,
        @NotNull String content,
        float score,
        @NotNull String taskType,
        boolean success,
        @NotNull Instant timestamp,
        @NotNull java.util.Map<String, String> metadata
) {
    public MemoryEntry {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id cannot be null or blank");
        }
        if (content == null) {
            throw new IllegalArgumentException("content cannot be null");
        }
        if (score < 0.0f || score > 1.0f) {
            throw new IllegalArgumentException("score must be between 0.0 and 1.0");
        }
        if (taskType == null) {
            throw new IllegalArgumentException("taskType cannot be null");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp cannot be null");
        }
        if (metadata == null) {
            throw new IllegalArgumentException("metadata cannot be null");
        }
    }
}
