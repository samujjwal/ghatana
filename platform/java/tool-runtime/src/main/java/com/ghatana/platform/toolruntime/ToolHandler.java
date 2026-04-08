/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.toolruntime;

import com.ghatana.agent.framework.tools.ToolContract;
import com.ghatana.agent.framework.tools.ToolExecutionEnvelope;
import com.ghatana.agent.framework.tools.ToolExecutionResult;
import io.activej.promise.Promise;

/**
 * Strategy for executing a single tool invocation.
 *
 * <p>A {@code ToolHandler} is registered with a {@link ToolExecutor} for a specific tool ID.
 * Implementations may run the tool in-process, in a sandbox, via HTTP, or via the
 * Model Context Protocol.
 *
 * @doc.type interface
 * @doc.purpose Strategy for executing a single tool invocation
 * @doc.layer platform
 * @doc.pattern Strategy
 */
public interface ToolHandler {

    /**
     * Handle the tool invocation described by the given envelope and contract.
     *
     * @param envelope  the execution envelope containing invocation metadata and input
     * @param contract  the tool contract providing schema, policy, and transport metadata
     * @return promise resolving to the execution result
     */
    Promise<ToolExecutionResult> handle(ToolExecutionEnvelope envelope, ToolContract contract);
}
