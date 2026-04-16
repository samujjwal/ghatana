/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.toolruntime;

import io.activej.promise.Promise;

import java.util.Map;

/**
 * Sandbox implementation that denies all tool execution requests.
 *
 * <p>Used as the terminal delegate for fail-closed deployments where a policy
 * decision may approve a request but no concrete execution sandbox has been
 * provisioned yet.
 *
 * @doc.type class
 * @doc.purpose Fail-closed ToolSandbox that blocks execution when no runtime sandbox exists
 * @doc.layer platform
 * @doc.pattern Service
 */
public final class FailClosedToolSandbox implements ToolSandbox {

    @Override
    public Promise<String> execute(
            String tenantId,
            String agentId,
            String toolName,
            Map<String, Object> input) {
        return Promise.ofException(new IllegalStateException(
            "Tool execution blocked: no concrete execution sandbox is configured for tool '" + toolName + "'"));
    }
}
