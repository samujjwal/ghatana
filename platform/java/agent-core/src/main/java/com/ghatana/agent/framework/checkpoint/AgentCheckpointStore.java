/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.ghatana.agent.framework.checkpoint;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * SPI for persisting and retrieving {@link AgentCheckpoint} instances.
 *
 * <p>Implementations are expected to use <b>Data-Cloud exclusively</b> for
 * storage (per clean architecture). In-memory implementations may be used
 * for testing only.
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li>{@link #save(AgentCheckpoint)} — persist a new checkpoint</li>
 *   <li>{@link #loadLatest(String, String)} — retrieve the most recent checkpoint for recovery</li>
 *   <li>{@link #loadAll(String, String)} — retrieve all checkpoints for an execution (audit/replay)</li>
 *   <li>{@link #delete(String)} — delete a specific checkpoint</li>
 *   <li>{@link #deleteByExecution(String, String)} — clean up all checkpoints after successful completion</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose SPI for agent checkpoint persistence (backed by Data-Cloud)
 * @doc.layer framework
 * @doc.pattern Repository
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 */
public interface AgentCheckpointStore {

    /**
     * Persists a checkpoint. If a checkpoint with the same ID already exists,
     * it is overwritten.
     *
     * @param checkpoint the checkpoint to save
     * @return a Promise completing when the checkpoint is durably stored
     */
    @NotNull
    Promise<Void> save(@NotNull AgentCheckpoint checkpoint);

    /**
     * Loads the most recent (highest sequence number) checkpoint for the given
     * agent and execution.
     *
     * @param agentId     the agent ID
     * @param executionId the execution/turn ID
     * @return a Promise of the latest checkpoint, or empty if none exists
     */
    @NotNull
    Promise<Optional<AgentCheckpoint>> loadLatest(@NotNull String agentId,
                                                   @NotNull String executionId);

    /**
     * Loads all checkpoints for the given agent and execution, ordered by
     * sequence number ascending.
     *
     * @param agentId     the agent ID
     * @param executionId the execution/turn ID
     * @return a Promise of the ordered list (may be empty)
     */
    @NotNull
    Promise<List<AgentCheckpoint>> loadAll(@NotNull String agentId,
                                            @NotNull String executionId);

    /**
     * Deletes a specific checkpoint by its ID.
     *
     * @param checkpointId the checkpoint ID
     * @return a Promise completing when deletion is done
     */
    @NotNull
    Promise<Void> delete(@NotNull String checkpointId);

    /**
     * Deletes all checkpoints for the given agent and execution.
     * Typically called after an execution completes successfully.
     *
     * @param agentId     the agent ID
     * @param executionId the execution/turn ID
     * @return a Promise completing when all checkpoints are deleted
     */
    @NotNull
    Promise<Void> deleteByExecution(@NotNull String agentId,
                                     @NotNull String executionId);
}
