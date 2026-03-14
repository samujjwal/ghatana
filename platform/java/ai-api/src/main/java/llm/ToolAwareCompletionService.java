package com.ghatana.ai.llm;

import io.activej.promise.Promise;

import java.util.List;

/**
 * Extended completion service with tool/function calling support.
 *
 * <p>
 * <b>Purpose</b><br>
 * Extends the base CompletionService with support for LLM tool calling,
 * enabling agents to invoke external tools during reasoning.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * ToolAwareCompletionService service = ...;
 * List<ToolDefinition> tools = List.of(
 *     ToolDefinition.builder()
 *         .name("search_code")
 *         .description("Search codebase")
 *         .build()
 * );
 * CompletionResult result = service.completeWithTools(request, tools).getResult();
 * if (result.hasToolCalls()) {
 *     for (ToolCall call : result.getToolCalls()) {
 *         // Execute tool and continue conversation
 *     }
 * }
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose LLM completion with tool/function calling
 * @doc.layer infrastructure
 * @doc.pattern Adapter
 */
public interface ToolAwareCompletionService extends CompletionService {

    /**
     * Generates a completion with available tools. The LLM may choose to call
     * one or more tools instead of generating text.
     *
     * @param request The completion request
     * @param tools Available tools for the LLM to use
     * @return Promise completing with the result (may contain tool calls)
     */
    Promise<CompletionResult> completeWithTools(CompletionRequest request, List<ToolDefinition> tools);

    /**
     * Continues a conversation after tool execution. Sends tool results back to
     * the LLM for continued reasoning.
     *
     * @param request The original request
     * @param toolResults Results from executed tool calls
     * @return Promise completing with the next response
     */
    Promise<CompletionResult> continueWithToolResults(
            CompletionRequest request,
            List<ToolCallResult> toolResults
    );
}
