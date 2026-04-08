/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.toolruntime;

import com.ghatana.agent.framework.tools.ToolContract;
import com.ghatana.agent.framework.tools.ToolExecutionEnvelope;
import com.ghatana.agent.framework.tools.ToolExecutionResult;
import io.activej.promise.Promise;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * A {@link ToolHandler} that executes tools via the isolated {@link ToolSandbox}.
 *
 * <p>The output from the sandbox (a raw string) is wrapped as the output object in the result.
 * Any sandbox-level error causes a {@code FAILED} result.
 *
 * @doc.type class
 * @doc.purpose Sandbox-isolated ToolHandler delegating to ToolSandbox
 * @doc.layer platform
 * @doc.pattern Adapter
 */
public final class SandboxToolHandler implements ToolHandler {

    private final ToolSandbox sandbox;

    /**
     * Construct a handler backed by the given sandbox.
     *
     * @param sandbox the sandbox to delegate to; must not be null
     */
    public SandboxToolHandler(ToolSandbox sandbox) {
        this.sandbox = Objects.requireNonNull(sandbox, "sandbox must not be null");
    }

    @Override
    public Promise<ToolExecutionResult> handle(ToolExecutionEnvelope envelope, ToolContract contract) {
        Instant start = Instant.now();
        return sandbox.execute(
                        envelope.tenantId(),
                        envelope.callerAgentId(),
                        contract.name(),
                        envelope.input())
                .then(
                        output -> {
                            Instant end = Instant.now();
                            return Promise.of(ToolExecutionResult.succeeded(
                                    envelope.invocationId(),
                                    output,
                                    Map.of(),
                                    envelope.invocationId(),
                                    end,
                                    Duration.between(start, end)));
                        },
                        ex -> {
                            Instant end = Instant.now();
                            return Promise.of(ToolExecutionResult.failed(
                                    envelope.invocationId(),
                                    ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName(),
                                    envelope.invocationId(),
                                    end,
                                    Duration.between(start, end)));
                        });
    }
}
