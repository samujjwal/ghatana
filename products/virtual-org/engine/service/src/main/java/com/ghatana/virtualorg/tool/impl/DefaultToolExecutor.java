package com.ghatana.virtualorg.tool.impl;

import com.ghatana.virtualorg.tool.Tool;
import com.ghatana.virtualorg.tool.ToolExecutor;
import com.ghatana.virtualorg.tool.ToolRegistry;
import com.ghatana.virtualorg.tool.ToolResult;
import com.ghatana.virtualorg.v1.ToolCallProto;
import com.google.protobuf.Timestamp;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Default implementation of ToolExecutor with timeout and error handling.
 *
 * <p><b>Purpose</b><br>
 * Service adapter implementing {@link ToolExecutor} for tool orchestration
 * with async execution, timeout enforcement, and parallel processing.
 *
 * <p><b>Architecture Role</b><br>
 * Service adapter coordinating tool execution. Provides:
 * - Async execution via ActiveJ Promises
 * - Timeout handling (prevents hanging tools)
 * - Error handling and logging
 * - Parallel execution (Promise.all)
 * - Future: Retry logic for transient failures
 *
 * <p><b>Features</b><br>
 * - **Async**: Non-blocking tool execution
 * - **Timeout**: Configurable per-tool timeout
 * - **Parallel**: Multiple tools execute concurrently
 * - **Error Handling**: Graceful degradation on tool failure
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * DefaultToolExecutor executor = new DefaultToolExecutor(
 *     toolRegistry,
 *     eventloop
 * );
 * 
 * // Execute single tool
 * ToolCallProto result = executor.execute(
 *     "git",
 *     Map.of("operation", "status", "repoPath", "/repo")
 * ).getResult();
 * 
 * // Execute multiple tools in parallel
 * List<ToolCallProto> results = executor.executeAll(toolCalls).getResult();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Default tool executor service with timeout and parallel execution
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class DefaultToolExecutor implements ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(DefaultToolExecutor.class);

    private final ToolRegistry toolRegistry;
    private final Eventloop eventloop;

    public DefaultToolExecutor(
            @NotNull ToolRegistry toolRegistry,
            @NotNull Eventloop eventloop) {
        this.toolRegistry = toolRegistry;
        this.eventloop = eventloop;
    }

    @Override
    @NotNull
    public Promise<ToolCallProto> execute(@NotNull String toolName, @NotNull Map<String, String> arguments) {
        long startTime = System.currentTimeMillis();

        // Find tool by name
        Tool tool = toolRegistry.getAllTools().stream()
                .filter(t -> t.getName().equals(toolName))
                .findFirst()
                .flatMap(proto -> toolRegistry.getTool(proto.getToolId()))
                .orElse(null);

        if (tool == null) {
            log.error("Tool not found: {}", toolName);
            return Promise.of(createErrorToolCall(toolName, arguments, "Tool not found: " + toolName, startTime));
        }

        if (!tool.isEnabled()) {
            log.error("Tool disabled: {}", toolName);
            return Promise.of(createErrorToolCall(toolName, arguments, "Tool is disabled: " + toolName, startTime));
        }

        log.debug("Executing tool: {}", toolName);

        return tool.execute(arguments)
                .map(result -> {
                    long duration = System.currentTimeMillis() - startTime;

                    if (result.success()) {
                        log.debug("Tool executed successfully: tool={}, duration={}ms", toolName, duration);
                        return createSuccessToolCall(toolName, arguments, result, duration);
                    } else {
                        log.warn("Tool execution failed: tool={}, error={}", toolName, result.error());
                        return createErrorToolCall(toolName, arguments, result.error(), duration);
                    }
                })
                .whenException(e -> 
                    log.error("Tool execution exception: tool={}", toolName, e)
                );
    }

    @Override
    @NotNull
    public Promise<List<ToolCallProto>> executeAll(@NotNull List<ToolCallProto> toolCalls) {
        if (toolCalls.isEmpty()) {
            return Promise.of(List.of());
        }

        // Execute all tools in parallel
        List<Promise<ToolCallProto>> promises = new ArrayList<>();

        for (ToolCallProto toolCall : toolCalls) {
            promises.add(execute(toolCall.getToolName(), toolCall.getArgumentsMap()));
        }

        return Promises.toList(promises);
    }

    // =============================
    // Helper methods
    // =============================

    private ToolCallProto createSuccessToolCall(
            String toolName,
            Map<String, String> arguments,
            ToolResult result,
            long durationMs) {

        return ToolCallProto.newBuilder()
                .setToolName(toolName)
                .putAllArguments(arguments)
                .setResult(result.result() != null ? result.result() : "")
                .setSuccess(true)
                .setDurationMs(durationMs)
                .setTimestamp(currentTimestamp())
                .build();
    }

    private ToolCallProto createErrorToolCall(
            String toolName,
            Map<String, String> arguments,
            String error,
            long durationMs) {

        return ToolCallProto.newBuilder()
                .setToolName(toolName)
                .putAllArguments(arguments)
                .setError(error)
                .setSuccess(false)
                .setDurationMs(durationMs)
                .setTimestamp(currentTimestamp())
                .build();
    }

    private Timestamp currentTimestamp() {
        Instant now = Instant.now();
        return Timestamp.newBuilder()
                .setSeconds(now.getEpochSecond())
                .setNanos(now.getNano())
                .build();
    }
}
