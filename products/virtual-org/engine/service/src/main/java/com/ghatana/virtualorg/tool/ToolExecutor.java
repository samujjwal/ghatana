package com.ghatana.virtualorg.tool;

import com.ghatana.virtualorg.v1.TaskProto;
import com.ghatana.virtualorg.v1.ToolCallProto;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Executor for running tools with timeout, retry, and error handling.
 *
 * <p><b>Purpose</b><br>
 * Port interface for tool execution orchestration with timeout management,
 * retry logic, and parallel execution capabilities.
 *
 * <p><b>Architecture Role</b><br>
 * Service port interface for tool execution. Implementations provide:
 * - Timeout enforcement (prevent hanging tools)
 * - Retry logic (transient failure recovery)
 * - Error handling and logging
 * - Parallel execution (multiple tools concurrently)
 * - Result aggregation
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * ToolExecutor executor = new DefaultToolExecutor(
 *     toolRegistry,
 *     Duration.ofSeconds(30),  // timeout
 *     3,                       // max retries
 *     eventloop
 * );
 * 
 * // Execute single tool
 * ToolCallProto result = executor.execute(
 *     "git",
 *     Map.of("operation", "clone", "url", "...")
 * ).getResult();
 * 
 * // Execute multiple tools in parallel
 * List<ToolCallProto> results = executor.executeAll(toolCalls).getResult();
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Tool execution orchestration port with timeout and retry
 * @doc.layer product
 * @doc.pattern Port
 */
public interface ToolExecutor {

    /**
     * Executes a tool and returns the result as a ToolCallProto.
     *
     * @param toolName  the tool name
     * @param arguments the tool arguments
     * @return a promise of the tool call result
     */
    @NotNull
    Promise<ToolCallProto> execute(@NotNull String toolName, @NotNull Map<String, String> arguments);

    /**
     * Executes multiple tools in parallel.
     *
     * @param toolCalls the tool calls to execute
     * @return a promise of the results
     */
    @NotNull
    Promise<java.util.List<ToolCallProto>> executeAll(@NotNull java.util.List<ToolCallProto> toolCalls);
    
    /**
     * Executes tools and returns results (compatibility method).
     *
     * @param toolCalls the tool calls to execute
     * @param task the associated task
     * @return a promise of tool results (as strings for now)
     */
    @NotNull
    default Promise<java.util.List<String>> executeTools(@NotNull java.util.List<ToolCallProto> toolCalls, @NotNull TaskProto task) {
        // Default: delegate to executeAll and convert to strings
        return executeAll(toolCalls)
            .map(results -> results.stream()
                .map(ToolCallProto::toString)
                .collect(java.util.stream.Collectors.toList()));
    }
}
