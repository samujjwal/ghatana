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

import java.time.Duration;

/**
 * Policy controlling when checkpoints are taken during agent execution.
 *
 * <p>Checkpoints can be triggered by invocation count, elapsed time, or both.
 * The decorator {@link CheckpointedTypedAgent} evaluates this policy after
 * every {@code process()} call.
 *
 * @doc.type record
 * @doc.purpose Configurable checkpoint frequency policy
 * @doc.layer framework
 * @doc.pattern Value Object
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 */
@Value
@Builder(toBuilder = true)
public class CheckpointPolicy {

    /**
     * Checkpoint after every N invocations. 0 = disabled.
     */
    @Builder.Default
    int everyNInvocations = 0;

    /**
     * Checkpoint if at least this duration has elapsed since the last checkpoint.
     * {@code null} = disabled.
     */
    Duration timeBased;

    /**
     * Whether to checkpoint on shutdown (for graceful recovery).
     */
    @Builder.Default
    boolean onShutdown = true;

    /**
     * Whether to delete checkpoints after a successful terminal result.
     */
    @Builder.Default
    boolean deleteOnCompletion = true;

    /**
     * Returns a policy that checkpoints every N invocations.
     */
    public static CheckpointPolicy everyN(int n) {
        return CheckpointPolicy.builder().everyNInvocations(n).build();
    }

    /**
     * Returns a policy that checkpoints at most every {@code interval}.
     */
    public static CheckpointPolicy timed(Duration interval) {
        return CheckpointPolicy.builder().timeBased(interval).build();
    }

    /**
     * Returns a policy that never auto-checkpoints (manual only).
     */
    public static CheckpointPolicy manual() {
        return CheckpointPolicy.builder()
                .everyNInvocations(0)
                .timeBased(null)
                .onShutdown(false)
                .deleteOnCompletion(false)
                .build();
    }
}
