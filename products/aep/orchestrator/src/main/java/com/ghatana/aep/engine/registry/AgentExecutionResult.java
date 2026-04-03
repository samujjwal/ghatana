package com.ghatana.aep.engine.registry;

import java.util.Map;
import java.util.Objects;

/**
 * Result of executing an agent via AEP Central Registry.
 *
 * @doc.type class
 * @doc.purpose Value object encapsulating agent execution outcome (output, status, error)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public final class AgentExecutionResult {

    private final String agentId;
    private final Object output;
    private final boolean success;
    private final String errorMessage;

    private AgentExecutionResult(String agentId, Object output, boolean success, String errorMessage) {
        this.agentId = Objects.requireNonNull(agentId, "agentId");
        this.output = output;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public static AgentExecutionResult success(String agentId, Object output) {
        return new AgentExecutionResult(agentId, output, true, null);
    }

    public static AgentExecutionResult failure(String agentId, String errorMessage) {
        return new AgentExecutionResult(agentId, null, false, errorMessage);
    }

    public String getAgentId() {
        return agentId;
    }

    public Object getOutput() {
        return output;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage != null ? errorMessage : "";
    }

    public Map<String, Object> toMap() {
        return Map.of(
                "agentId",
                agentId,
                "success",
                success,
                "output",
                output != null ? output : "",
                "error",
                errorMessage != null ? errorMessage : "");
    }
}
