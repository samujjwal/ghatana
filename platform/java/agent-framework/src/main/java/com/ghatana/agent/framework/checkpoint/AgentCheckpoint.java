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

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable snapshot of an agent's processing state at a specific point in time.
 *
 * <p>Checkpoints enable recovery of long-running agent workflows. The payload
 * is opaque to the checkpoint infrastructure — only the agent knows how to
 * serialize/deserialize its own state.
 *
 * @doc.type record
 * @doc.purpose Agent state snapshot for recovery
 * @doc.layer framework
 * @doc.pattern Value Object
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 */
@Value
@Builder(toBuilder = true)
public class AgentCheckpoint {

    /** Unique checkpoint identifier. */
    String checkpointId;

    /** ID of the agent that created this checkpoint. */
    String agentId;

    /** Execution/turn ID this checkpoint belongs to. */
    String executionId;

    /** Sequential checkpoint number within an execution. */
    long sequenceNumber;

    /** Timestamp when the checkpoint was created. */
    @Builder.Default
    Instant createdAt = Instant.now();

    /** Serialised agent state (opaque to infrastructure). */
    byte[] statePayload;

    /** Content type of statePayload (e.g. "application/json", "application/protobuf"). */
    @Builder.Default
    String payloadContentType = "application/json";

    /** Arbitrary metadata (e.g. phase, step, progress percentage). */
    @Builder.Default
    Map<String, String> metadata = Map.of();

    /** Whether this checkpoint has been marked as the final/completed state. */
    @Builder.Default
    boolean terminal = false;
}
