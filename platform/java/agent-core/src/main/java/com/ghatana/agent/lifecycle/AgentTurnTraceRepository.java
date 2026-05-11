/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.lifecycle;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * Durable store for governed turn and phase traces.
 */
public interface AgentTurnTraceRepository {

    @NotNull Promise<AgentTurnTrace> save(@NotNull AgentTurnTrace trace);

    @NotNull Promise<Optional<AgentTurnTrace>> findByTraceId(@NotNull String traceId);

    @NotNull Promise<List<AgentTurnTrace>> findByAgent(@NotNull String agentId);
}
