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
import java.util.function.Function;

/**
 * A {@link ToolHandler} that executes a tool in the same JVM process as the calling agent.
 *
 * <p>The execution function receives the input map from the envelope and returns an output object.
 * Any exception thrown by the function is caught and converted into a {@code FAILED}
 * {@link ToolExecutionResult}.
 *
 * @doc.type class
 * @doc.purpose In-process ToolHandler wrapping a Java Function
 * @doc.layer platform
 * @doc.pattern Adapter
 */
public final class InProcessToolHandler implements ToolHandler {

    private final Function<Map<String, Object>, Object> function;

    /**
     * Construct a handler that delegates to the given function.
     *
     * @param function the tool implementation; must not be null
     */
    public InProcessToolHandler(Function<Map<String, Object>, Object> function) {
        this.function = Objects.requireNonNull(function, "function must not be null");
    }

    @Override
    public Promise<ToolExecutionResult> handle(ToolExecutionEnvelope envelope, ToolContract contract) {
        Instant start = Instant.now();
        try {
            Object output = function.apply(envelope.input());
            Instant end = Instant.now();
            return Promise.of(ToolExecutionResult.succeeded(
                    envelope.invocationId(),
                    output,
                    Map.of(),
                    envelope.invocationId(),
                    end,
                    Duration.between(start, end)));
        } catch (Exception e) {
            Instant end = Instant.now();
            return Promise.of(ToolExecutionResult.failed(
                    envelope.invocationId(),
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(),
                    envelope.invocationId(),
                    end,
                    Duration.between(start, end)));
        }
    }
}
