/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.tools;

/**
 * Transport type indicating how a tool implementation is invoked.
 *
 * <ul>
 *   <li>{@link #IN_PROCESS} — the tool runs in the same JVM/process as the agent.</li>
 *   <li>{@link #SANDBOX} — the tool is isolated in a controlled execution environment.</li>
 *   <li>{@link #REMOTE} — the tool is called via a remote HTTP/gRPC endpoint.</li>
 *   <li>{@link #MCP} — the tool is called via the Model Context Protocol (MCP).</li>
 * </ul>
 *
 * @doc.type enum
 * @doc.purpose Transport type for a tool implementation
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public enum ToolTransport {

    /** Tool executes in the same JVM as the calling agent. No network or IPC boundary. */
    IN_PROCESS,

    /** Tool executes in a sandboxed environment, isolated from the main process. */
    SANDBOX,

    /** Tool is invoked via a remote HTTP or gRPC endpoint outside the platform. */
    REMOTE,

    /** Tool is invoked via the Model Context Protocol. */
    MCP;

    /**
     * Returns {@code true} if this transport crosses a process or network boundary.
     *
     * @return whether there is an external boundary
     */
    public boolean isExternal() {
        return this == REMOTE || this == MCP;
    }

    /**
     * Returns {@code true} if this transport requires a sandbox policy evaluation.
     *
     * @return whether sandboxing applies
     */
    public boolean requiresSandboxPolicy() {
        return this == SANDBOX;
    }
}
