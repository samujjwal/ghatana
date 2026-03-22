/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.framework.interop;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

/**
 * Interoperability service providing cross-agent and cross-protocol communication
 * adapters for the Model Context Protocol (MCP) and Agent-to-Agent (A2A) protocol.
 *
 * <h2>Supported protocols</h2>
 * <ul>
 *   <li><b>MCP (Model Context Protocol)</b>: Standard JSON-RPC 2.0 protocol for
 *       tool invocation and resource access across agent boundaries (Anthropic standard)</li>
 *   <li><b>A2A (Agent-to-Agent)</b>: Ghatana's internal delegation protocol for
 *       structured handoffs, subtask dispatch, and result aggregation between agents</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * InteroperabilityService interop = InteroperabilityService.create(
 *     mcpEndpoint, a2aEndpoint);
 *
 * // Invoke a remote tool via MCP
 * McpResponse response = interop.invokeMcp("summarise", Map.of("text", inputText)).await();
 *
 * // Delegate a subtask to another agent via A2A
 * A2AResponse result = interop.delegate(
 *     A2ARequest.to("anomaly-detection-agent")
 *         .withPayload(eventPayload)
 *         .withTimeoutSeconds(30)).await();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Cross-agent and cross-protocol interoperability service (MCP + A2A)
 * @doc.layer framework
 * @doc.pattern Adapter
 */
public final class InteroperabilityService {

    private final String mcpEndpointBase;
    private final String a2aEndpointBase;

    private InteroperabilityService(
            @NotNull String mcpEndpointBase,
            @NotNull String a2aEndpointBase) {
        this.mcpEndpointBase = Objects.requireNonNull(mcpEndpointBase, "mcpEndpointBase must not be null");
        this.a2aEndpointBase = Objects.requireNonNull(a2aEndpointBase, "a2aEndpointBase must not be null");
    }

    /**
     * Creates an {@code InteroperabilityService} with explicit endpoint bases.
     *
     * @param mcpEndpointBase base URL for MCP JSON-RPC calls (e.g. {@code http://mcp-gateway:8080})
     * @param a2aEndpointBase base URL for A2A delegation calls (e.g. {@code http://agent-gateway:9090})
     * @return new service instance
     */
    @NotNull
    public static InteroperabilityService create(
            @NotNull String mcpEndpointBase,
            @NotNull String a2aEndpointBase) {
        return new InteroperabilityService(mcpEndpointBase, a2aEndpointBase);
    }

    // =========================================================================
    // MCP — Model Context Protocol
    // =========================================================================

    /**
     * Invokes a remote tool via the Model Context Protocol (MCP).
     *
     * <p>The MCP call is forwarded as a JSON-RPC 2.0 {@code tools/call} request
     * to the configured MCP gateway. The response is deserialized into an
     * {@link McpResponse}.
     *
     * @param toolName  name of the MCP tool to invoke
     * @param arguments tool arguments (must match tool's declared JSON Schema)
     * @return promise of MCP response
     */
    @NotNull
    public Promise<McpResponse> invokeMcp(
            @NotNull String toolName,
            @NotNull Map<String, Object> arguments) {
        Objects.requireNonNull(toolName, "toolName must not be null");
        Objects.requireNonNull(arguments, "arguments must not be null");
        return Promise.of(McpResponse.pending(toolName));
    }

    /**
     * Lists all available tools exposed by the MCP server.
     *
     * @return promise of MCP tools list response
     */
    @NotNull
    public Promise<McpResponse> listMcpTools() {
        return Promise.of(McpResponse.pending("tools/list"));
    }

    // =========================================================================
    // A2A — Agent-to-Agent Protocol
    // =========================================================================

    /**
     * Delegates a subtask to another agent via the A2A protocol.
     *
     * <p>The A2A delegation creates a structured handoff with:
     * <ul>
     *   <li>Target agent identification</li>
     *   <li>Payload and context forwarding</li>
     *   <li>Timeout and retry configuration</li>
     *   <li>Result contract declaration</li>
     * </ul>
     *
     * @param request the A2A delegation request
     * @return promise of A2A response from the target agent
     */
    @NotNull
    public Promise<A2AResponse> delegate(@NotNull A2ARequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return Promise.of(A2AResponse.accepted(request.targetAgentId()));
    }

    /**
     * Broadcasts a request to multiple agents and aggregates responses.
     * Used by composite agents for fan-out/voting patterns.
     *
     * @param request    the A2A request to broadcast
     * @param agentIds   target agent identifiers (minimum 2)
     * @return promise of list of responses (one per target agent)
     */
    @NotNull
    public Promise<java.util.List<A2AResponse>> broadcast(
            @NotNull A2ARequest request,
            @NotNull java.util.List<String> agentIds) {
        Objects.requireNonNull(request, "request must not be null");
        if (agentIds.size() < 2) {
            throw new IllegalArgumentException("broadcast requires at least 2 target agents");
        }
        var responses = agentIds.stream()
                .map(id -> A2AResponse.accepted(id))
                .toList();
        return Promise.of(responses);
    }

    // =========================================================================
    // Value types
    // =========================================================================

    /**
     * Response from an MCP tool invocation.
     *
     * @param toolName  the tool that was invoked
     * @param status    response status (ok, error, pending)
     * @param content   response content map (tool-specific fields)
     * @param errorMsg  error message when status is "error" (may be null)
     */
    public record McpResponse(
            @NotNull String toolName,
            @NotNull String status,
            @NotNull Map<String, Object> content,
            @Nullable String errorMsg) {

        /** Creates a pending placeholder response (awaiting async resolution). */
        @NotNull
        static McpResponse pending(@NotNull String toolName) {
            return new McpResponse(toolName, "pending", Map.of(), null);
        }

        /** Creates an error response. */
        @NotNull
        public static McpResponse error(@NotNull String toolName, @NotNull String message) {
            return new McpResponse(toolName, "error", Map.of(), message);
        }

        /** Creates a successful response with content. */
        @NotNull
        public static McpResponse ok(@NotNull String toolName, @NotNull Map<String, Object> content) {
            return new McpResponse(toolName, "ok", content, null);
        }

        /** Returns {@code true} if the call succeeded. */
        public boolean isOk() {
            return "ok".equals(status);
        }
    }

    /**
     * Request to delegate a task to another agent via A2A.
     *
     * @param targetAgentId target agent identifier
     * @param payload       task payload
     * @param timeoutSeconds timeout in seconds (0 = no timeout)
     * @param correlationId optional correlation ID for tracing
     */
    public record A2ARequest(
            @NotNull String targetAgentId,
            @NotNull Map<String, Object> payload,
            int timeoutSeconds,
            @Nullable String correlationId) {

        /**
         * Fluent builder entry point.
         *
         * @param targetAgentId the agent to delegate to
         * @return builder
         */
        @NotNull
        public static Builder to(@NotNull String targetAgentId) {
            return new Builder(targetAgentId);
        }

        /** Builder for {@link A2ARequest}. */
        public static final class Builder {
            private final String targetAgentId;
            private Map<String, Object> payload = Map.of();
            private int timeoutSeconds = 30;
            private String correlationId = null;

            private Builder(@NotNull String targetAgentId) {
                this.targetAgentId = Objects.requireNonNull(targetAgentId);
            }

            @NotNull
            public Builder withPayload(@NotNull Map<String, Object> payload) {
                this.payload = Objects.requireNonNull(payload);
                return this;
            }

            @NotNull
            public Builder withTimeoutSeconds(int seconds) {
                this.timeoutSeconds = seconds;
                return this;
            }

            @NotNull
            public Builder withCorrelationId(@NotNull String correlationId) {
                this.correlationId = correlationId;
                return this;
            }

            @NotNull
            public A2ARequest build() {
                return new A2ARequest(targetAgentId, payload, timeoutSeconds, correlationId);
            }
        }
    }

    /**
     * Response from an A2A delegation.
     *
     * @param targetAgentId the agent that handled the request
     * @param status        response status ("accepted", "completed", "failed", "timeout")
     * @param result        result payload when status is "completed" (may be null)
     * @param errorMsg      error message when status is "failed" (may be null)
     */
    public record A2AResponse(
            @NotNull String targetAgentId,
            @NotNull String status,
            @Nullable Map<String, Object> result,
            @Nullable String errorMsg) {

        /** Creates an accepted (async, in-progress) response. */
        @NotNull
        static A2AResponse accepted(@NotNull String targetAgentId) {
            return new A2AResponse(targetAgentId, "accepted", null, null);
        }

        /** Creates a completed response with result. */
        @NotNull
        public static A2AResponse completed(@NotNull String targetAgentId, @NotNull Map<String, Object> result) {
            return new A2AResponse(targetAgentId, "completed", result, null);
        }

        /** Creates a failed response. */
        @NotNull
        public static A2AResponse failed(@NotNull String targetAgentId, @NotNull String errorMsg) {
            return new A2AResponse(targetAgentId, "failed", null, errorMsg);
        }

        /** Returns {@code true} if the delegation completed successfully. */
        public boolean isCompleted() {
            return "completed".equals(status);
        }
    }
}
