package com.ghatana.virtualorg.llm;

import com.ghatana.virtualorg.v1.ToolCallProto;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Response from an LLM client containing reasoning and tool calls.
 *
 * <p><b>Purpose</b><br>
 * Value object encapsulating LLM response data including reasoning text,
 * tool call instructions, token usage, and confidence metrics.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * LLMResponse response = new LLMResponse(
 *     "I recommend approving this request because...",
 *     List.of(toolCall),
 *     150,
 *     0.92f
 * );
 * String reasoning = response.reasoning();
 * List<ToolCallProto> tools = response.toolCalls();
 * }</pre>
 *
 * <p><b>Validation</b><br>
 * All fields validated in canonical constructor:
 * - reasoning: non-null
 * - toolCalls: non-null (may be empty)
 * - tokensUsed: >= 0
 * - confidence: 0.0-1.0 range
 *
 * @param reasoning  the reasoning or explanation from LLM
 * @param toolCalls  the tool calls to execute (empty if none)
 * @param tokensUsed the number of tokens consumed (for cost tracking)
 * @param confidence the confidence score (0.0 to 1.0)
 *
 * @doc.type record
 * @doc.purpose LLM response value object with reasoning and tool calls
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record LLMResponse(
        @NotNull String reasoning,
        @NotNull List<ToolCallProto> toolCalls,
        int tokensUsed,
        float confidence
) {
    public LLMResponse {
        if (reasoning == null) {
            throw new IllegalArgumentException("reasoning cannot be null");
        }
        if (toolCalls == null) {
            throw new IllegalArgumentException("toolCalls cannot be null");
        }
        if (tokensUsed < 0) {
            throw new IllegalArgumentException("tokensUsed must be non-negative");
        }
        if (confidence < 0.0f || confidence > 1.0f) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
    }

    /**
     * Gets content (alias for reasoning for compatibility).
     */
    public String getContent() {
        return reasoning;
    }

    /**
     * Gets rationale (alias for reasoning for compatibility).
     */
    public String getRationale() {
        return reasoning;
    }

    /**
     * Gets tool calls.
     */
    public List<ToolCallProto> getToolCalls() {
        return toolCalls;
    }
}
