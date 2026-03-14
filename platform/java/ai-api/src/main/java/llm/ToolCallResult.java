package com.ghatana.ai.llm;

import java.util.Objects;

/**
 * Result of executing a tool call.
 *
 * <p>
 * <b>Purpose</b><br>
 * Captures the outcome of a tool execution, including the result content and
 * success/failure status. Used to send tool results back to the LLM.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * // Successful tool execution
 * ToolCallResult success = ToolCallResult.success("call-123", "search_code", "{\"results\": [...]}");
 *
 * // Failed tool execution
 * ToolCallResult failure = ToolCallResult.failure("call-123", "search_code", "Connection timeout");
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Tool execution result for LLM conversation continuation
 * @doc.layer platform
 * @doc.pattern Value Object
 */
public final class ToolCallResult {

    private final String toolCallId;
    private final String toolName;
    private final String result;
    private final boolean success;

    private ToolCallResult(String toolCallId, String toolName, String result, boolean success) {
        this.toolCallId = Objects.requireNonNull(toolCallId, "toolCallId must not be null");
        this.toolName = Objects.requireNonNull(toolName, "toolName must not be null");
        this.result = Objects.requireNonNull(result, "result must not be null");
        this.success = success;
    }

    /**
     * Creates a successful tool result.
     *
     * @param toolCallId The ID of the tool call (from LLM response)
     * @param toolName The name of the tool
     * @param result The tool output/result
     * @return A successful ToolCallResult
     */
    public static ToolCallResult success(String toolCallId, String toolName, String result) {
        return new ToolCallResult(toolCallId, toolName, result, true);
    }

    /**
     * Creates a failed tool result.
     *
     * @param toolCallId The ID of the tool call (from LLM response)
     * @param toolName The name of the tool
     * @param error The error message
     * @return A failed ToolCallResult
     */
    public static ToolCallResult failure(String toolCallId, String toolName, String error) {
        return new ToolCallResult(toolCallId, toolName, error, false);
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public String getToolName() {
        return toolName;
    }

    public String getResult() {
        return result;
    }

    public boolean isSuccess() {
        return success;
    }

    @Override
    public String toString() {
        return "ToolCallResult{"
                + "toolCallId='" + toolCallId + '\''
                + ", toolName='" + toolName + '\''
                + ", success=" + success
                + ", result='" + (result.length() > 100 ? result.substring(0, 100) + "..." : result) + '\''
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ToolCallResult that = (ToolCallResult) o;
        return success == that.success
                && Objects.equals(toolCallId, that.toolCallId)
                && Objects.equals(toolName, that.toolName)
                && Objects.equals(result, that.result);
    }

    @Override
    public int hashCode() {
        return Objects.hash(toolCallId, toolName, result, success);
    }
}
