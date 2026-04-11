package com.ghatana.agent.memory.model.taskstate;

import com.ghatana.agent.memory.model.*;
import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Task-state memory item for long-running multi-session workflows.
 * Tracks phases, checkpoints, blockers, invariants, dependencies,
 * and environment snapshots for resumable agent tasks.
 *
 * @doc.type value-object
 * @doc.purpose Task state for multi-session workflows
 * @doc.layer agent-memory
 */
@Value
@Builder
public class TaskState implements MemoryItem {

    @Builder.Default @NotNull String id = "";
    @Builder.Default @NotNull MemoryItemType type = MemoryItemType.TASK_STATE;
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

    // Task-specific fields
    @NotNull String taskId;
    @Builder.Default @NotNull String name = "";
    @Nullable String description;
    @NotNull String agentId;
    @NotNull TaskLifecycleStatus status;
    @Nullable String currentPhase;
    @Builder.Default @NotNull List<TaskPhase> phases = List.of();
    @Builder.Default @NotNull List<TaskCheckpoint> checkpoints = List.of();
    @Builder.Default @NotNull List<TaskBlocker> blockers = List.of();
    @Builder.Default @NotNull List<TaskInvariant> invariants = List.of();
    @Nullable DoneCriteria doneCriteria;
    @Builder.Default @NotNull List<TaskDependency> dependencies = List.of();
    @Nullable EnvironmentSnapshot environmentSnapshot;
    @Builder.Default @NotNull Map<String, String> metadata = Map.of();
    @Nullable Instant lastActiveAt;
}
