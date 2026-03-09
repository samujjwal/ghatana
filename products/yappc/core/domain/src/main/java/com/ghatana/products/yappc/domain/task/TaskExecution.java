package com.ghatana.products.yappc.domain.task;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Map;

/**
 * Task execution record.
 *
 * @param id        Execution ID
 * @param taskId    Task ID
 * @param userId    User ID
 * @param projectId Project ID (if applicable)
 * @param input     Task input
 * @param output    Task output (if completed)
 * @param status    Execution status
 * @param error     Error message (if failed)
 * @param startTime Start timestamp
 * @param endTime   End timestamp (if completed)
 * @param metadata  Additional metadata
 * @doc.type record
 * @doc.purpose Historical task execution record
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record TaskExecution(
        @NotNull String id,
        @NotNull String taskId,
        @NotNull String userId,
        @Nullable String projectId,
        @NotNull Object input,
        @Nullable Object output,
        @NotNull TaskExecutionStatus status,
        @Nullable String error,
        @NotNull Instant startTime,
        @Nullable Instant endTime,
        @NotNull Map<String, Object> metadata
) {
    public TaskExecution {
        metadata = Map.copyOf(metadata);
    }

    public long getDurationMs() {
        if (endTime == null) {
            return Instant.now().toEpochMilli() - startTime.toEpochMilli();
        }
        return endTime.toEpochMilli() - startTime.toEpochMilli();
    }
}
