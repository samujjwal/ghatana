/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.toolruntime;

import io.activej.promise.Promise;
import java.util.Map;

/**
 * Pass-through {@link ToolSandbox} for local development and testing.
 *
 * <p><strong>WARNING:</strong> This implementation applies no resource limits,
 * network restrictions, or audit logging. It MUST NOT be used in production.
 *
 * @doc.type class
 * @doc.purpose No-op ToolSandbox for dev/test environments
 * @doc.layer platform
 * @doc.pattern Service
 */
public final class NoopToolSandbox implements ToolSandbox {

    /** Singleton instance for convenience in tests. */
    public static final NoopToolSandbox INSTANCE = new NoopToolSandbox();

    @Override
    public Promise<String> execute(
            String tenantId, String agentId, String toolName, Map<String, Object> input) {
        return Promise.of("noop:" + toolName);
    }
}
