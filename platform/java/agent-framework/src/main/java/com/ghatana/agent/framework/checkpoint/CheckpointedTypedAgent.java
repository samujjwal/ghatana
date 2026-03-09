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

import com.ghatana.agent.*;
import com.ghatana.agent.framework.api.AgentContext;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Decorator that automatically checkpoints an agent's processing results
 * according to a {@link CheckpointPolicy}.
 *
 * <p>After each {@code process()} call the decorator evaluates whether a
 * checkpoint should be taken (based on invocation count or elapsed time)
 * and, if so, persists the result via the {@link AgentCheckpointStore}.
 *
 * <h2>State Serialization</h2>
 * <p>The caller provides a {@code stateSerializer} function that converts
 * the agent result into a byte array for the checkpoint payload. This keeps
 * the checkpoint infrastructure agnostic of the agent's output type.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * CheckpointedTypedAgent<Req, Resp> agent = CheckpointedTypedAgent.<Req, Resp>builder()
 *     .delegate(rawAgent)
 *     .store(dataCloudCheckpointStore)
 *     .policy(CheckpointPolicy.everyN(10))
 *     .stateSerializer(resp -> objectMapper.writeValueAsBytes(resp))
 *     .build();
 * }</pre>
 *
 * @param <I> input type
 * @param <O> output type
 *
 * @doc.type class
 * @doc.purpose Auto-checkpointing decorator for TypedAgent
 * @doc.layer framework
 * @doc.pattern Decorator
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 */
public final class CheckpointedTypedAgent<I, O> implements TypedAgent<I, O> {

    private static final Logger log = LoggerFactory.getLogger(CheckpointedTypedAgent.class);

    private final TypedAgent<I, O> delegate;
    private final AgentCheckpointStore store;
    private final CheckpointPolicy policy;
    private final Function<AgentResult<O>, byte[]> stateSerializer;

    private final AtomicLong invocationCount = new AtomicLong();
    private final AtomicLong sequenceCounter = new AtomicLong();
    private final AtomicReference<Instant> lastCheckpointTime = new AtomicReference<>(Instant.now());

    private CheckpointedTypedAgent(Builder<I, O> b) {
        this.delegate = Objects.requireNonNull(b.delegate, "delegate");
        this.store = Objects.requireNonNull(b.store, "store");
        this.policy = Objects.requireNonNull(b.policy, "policy");
        this.stateSerializer = Objects.requireNonNull(b.stateSerializer, "stateSerializer");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TypedAgent delegation
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    @NotNull
    public AgentDescriptor descriptor() { return delegate.descriptor(); }

    @Override
    @NotNull
    public Promise<Void> initialize(@NotNull AgentConfig config) {
        return delegate.initialize(config);
    }

    @Override
    @NotNull
    public Promise<Void> shutdown() {
        if (policy.isOnShutdown()) {
            log.info("Shutdown checkpoint requested for agent '{}'", descriptor().getAgentId());
        }
        return delegate.shutdown();
    }

    @Override
    @NotNull
    public Promise<HealthStatus> healthCheck() { return delegate.healthCheck(); }

    @Override
    @NotNull
    public Promise<Void> reconfigure(@NotNull AgentConfig newConfig) {
        return delegate.reconfigure(newConfig);
    }

    @Override
    public boolean validateInput(@NotNull I input) { return delegate.validateInput(input); }

    // ═══════════════════════════════════════════════════════════════════════════
    // Processing — with checkpoint
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    @NotNull
    public Promise<AgentResult<O>> process(@NotNull AgentContext ctx, @NotNull I input) {
        return delegate.process(ctx, input)
                .then(result -> {
                    long count = invocationCount.incrementAndGet();
                    if (shouldCheckpoint(count)) {
                        return saveCheckpoint(ctx, result).map($ -> result);
                    }
                    return Promise.of(result);
                });
    }

    @Override
    @NotNull
    public Promise<List<AgentResult<O>>> processBatch(@NotNull AgentContext ctx,
                                                       @NotNull List<I> inputs) {
        return delegate.processBatch(ctx, inputs);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Checkpoint logic
    // ═══════════════════════════════════════════════════════════════════════════

    private boolean shouldCheckpoint(long count) {
        if (policy.getEveryNInvocations() > 0 && count % policy.getEveryNInvocations() == 0) {
            return true;
        }
        if (policy.getTimeBased() != null) {
            Duration sinceLastCheckpoint = Duration.between(lastCheckpointTime.get(), Instant.now());
            return sinceLastCheckpoint.compareTo(policy.getTimeBased()) >= 0;
        }
        return false;
    }

    private Promise<Void> saveCheckpoint(AgentContext ctx, AgentResult<O> result) {
        String agentId = descriptor().getAgentId();
        try {
            byte[] payload = stateSerializer.apply(result);
            AgentCheckpoint checkpoint = AgentCheckpoint.builder()
                    .checkpointId(UUID.randomUUID().toString())
                    .agentId(agentId)
                    .executionId(ctx.getTurnId())
                    .sequenceNumber(sequenceCounter.incrementAndGet())
                    .statePayload(payload)
                    .metadata(Map.of(
                            "status", result.getStatus().name(),
                            "confidence", String.valueOf(result.getConfidence())
                    ))
                    .terminal(false)
                    .build();

            return store.save(checkpoint)
                    .whenResult($ -> {
                        lastCheckpointTime.set(Instant.now());
                        log.debug("Checkpoint saved: agent={}, seq={}, turn={}",
                                agentId, checkpoint.getSequenceNumber(), ctx.getTurnId());
                    })
                    .whenException(e -> log.warn("Checkpoint save failed for agent '{}': {}",
                            agentId, e.getMessage()));
        } catch (Exception e) {
            log.warn("Checkpoint serialization failed for agent '{}': {}", agentId, e.getMessage());
            return Promise.complete();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Builder
    // ═══════════════════════════════════════════════════════════════════════════

    public static <I, O> Builder<I, O> builder() {
        return new Builder<>();
    }

    public static final class Builder<I, O> {
        private TypedAgent<I, O> delegate;
        private AgentCheckpointStore store;
        private CheckpointPolicy policy;
        private Function<AgentResult<O>, byte[]> stateSerializer;

        private Builder() {}

        public Builder<I, O> delegate(@NotNull TypedAgent<I, O> delegate) {
            this.delegate = delegate;
            return this;
        }

        public Builder<I, O> store(@NotNull AgentCheckpointStore store) {
            this.store = store;
            return this;
        }

        public Builder<I, O> policy(@NotNull CheckpointPolicy policy) {
            this.policy = policy;
            return this;
        }

        public Builder<I, O> stateSerializer(@NotNull Function<AgentResult<O>, byte[]> serializer) {
            this.stateSerializer = serializer;
            return this;
        }

        public CheckpointedTypedAgent<I, O> build() {
            return new CheckpointedTypedAgent<>(this);
        }
    }
}
