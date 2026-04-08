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
 * Canonical entry point for governed tool execution.
 *
 * <p>All side-effecting tool calls from any agent must route through a {@code ToolExecutor}.
 * The executor applies policy checks, approval gate logic, sandbox routing, and audit emission
 * before and after each execution.
 *
 * <p>Handlers are registered per {@code toolId}. If no handler is registered for a given tool,
 * the execution returns a {@code DENIED} result.
 *
 * @doc.type interface
 * @doc.purpose Canonical entry point for governed tool execution
 * @doc.layer platform
 * @doc.pattern Facade, Strategy
 */
public interface ToolExecutor {

    /**
     * Executes a tool call described by the envelope against the provided contract.
     *
     * <p>The implementation applies policy checks, approval workflows (if required),
     * sandbox routing, and audit emission before delegating to the registered {@link ToolHandler}.
     *
     * @param envelope the invocation envelope carrying agent identity, input, and trace metadata
     * @param contract the tool contract providing schema, governance class, and transport metadata
     * @return promise resolving to the {@link ToolExecutionResult}
     */
    Promise<ToolExecutionResult> execute(ToolExecutionEnvelope envelope, ToolContract contract);

    /**
     * Register a {@link ToolHandler} for the given tool ID.
     *
     * <p>Replaces any previously registered handler for the same {@code toolId}.
     *
     * @param toolId  the unique tool identifier (must match {@code ToolContract.toolId()})
     * @param handler the handler to associate with this tool
     */
    void register(String toolId, ToolHandler handler);
}
