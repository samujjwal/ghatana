/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.api;

import io.activej.promise.Promise;

import java.util.List;

/**
 * Core contract for a typed, asynchronous agent.
 *
 * <p>All agent implementations (deterministic, probabilistic, LLM, etc.)
 * implement this interface. Products compile against this contract and
 * never depend on specific runtime implementations.
 *
 * @param <I> input type
 * @param <O> output type
 *
 * @doc.type interface
 * @doc.purpose Core agent processing contract
 * @doc.layer core
 * @doc.pattern Contract
 */
public interface TypedAgent<I, O> {

    /**
     * Returns the agent's metadata descriptor.
     */
    AgentDescriptor descriptor();

    /**
     * Initialises the agent with the given configuration.
     */
    Promise<Void> initialize(AgentConfig config);

    /**
     * Processes a single input and returns a result.
     */
    Promise<AgentResult<O>> process(AgentContext ctx, I input);

    /**
     * Processes a batch of inputs.
     */
    Promise<List<AgentResult<O>>> processBatch(AgentContext ctx, List<I> inputs);

    /**
     * Returns the current health status.
     */
    Promise<HealthStatus> healthCheck();

    /**
     * Shuts the agent down, releasing resources.
     */
    Promise<Void> shutdown();
}
