/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.toolruntime;

import io.activej.promise.Promise;
import java.util.Map;

/**
 * Executes agent tool invocations in an isolated sandbox environment.
 *
 * <p>All tool calls from agents MUST pass through a {@code ToolSandbox} to enforce:
 * <ul>
 *   <li>Resource limits (CPU, memory, wall-clock time)</li>
 *   <li>Network egress policies</li>
 *   <li>Audit logging of tool inputs and outputs</li>
 * </ul>
 *
 * <p>Implementations may use OS-level containerisation, WASM isolation, or a
 * lightweight JVM sandbox. The default no-op impl ({@link NoopToolSandbox}) is
 * provided for local development and testing only.
 *
 * @doc.type interface
 * @doc.purpose Sandboxed execution of agent tool invocations
 * @doc.layer platform
 * @doc.pattern Service
 */
public interface ToolSandbox {

    /**
     * Execute a named tool with the given input payload.
     *
     * @param tenantId the calling tenant
     * @param agentId  the agent making the tool call
     * @param toolName the registered tool name
     * @param input    key-value input parameters
     * @return promise resolving to the tool's string output
     */
    Promise<String> execute(String tenantId, String agentId, String toolName, Map<String, Object> input);
}
