/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.toolruntime.mcp;

import com.ghatana.agent.framework.tools.ToolContract;
import com.ghatana.agent.framework.tools.ToolExecutionEnvelope;
import com.ghatana.agent.framework.tools.ToolExecutionResult;
import com.ghatana.agent.framework.tools.ToolExecutionStatus;
import com.ghatana.platform.toolruntime.ToolHandler;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * A {@link ToolHandler} that invokes tools hosted on a Model Context Protocol (MCP) server
 * via JSON-RPC 2.0 over HTTP.
 *
 * <p>The adapter translates a {@link ToolExecutionEnvelope} into an {@link McpToolRequest},
 * calls the MCP server endpoint using the provided {@link McpHttpTransport}, and parses the
 * {@link McpToolResponse} into a {@link ToolExecutionResult}.
 *
 * <p>Error mapping:
 * <ul>
 *   <li>HTTP-level failure (transport exception) → {@code FAILED}</li>
 *   <li>MCP error response → {@code FAILED} with error code and message</li>
 *   <li>Successful MCP response → {@code SUCCESS}</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose MCP-protocol ToolHandler using JSON-RPC 2.0 over HTTP
 * @doc.layer platform
 * @doc.pattern Adapter
 */
public final class McpToolAdapter implements ToolHandler {

    private static final Logger LOG = LoggerFactory.getLogger(McpToolAdapter.class);

    /** JSON-RPC Content-Type header. */
    private static final Map<String, String> JSON_HEADERS = Map.of("Content-Type", "application/json");

    private final McpHttpTransport transport;

    /**
     * Construct the adapter with a backing HTTP transport.
     *
     * @param transport the HTTP transport to use; must not be null
     */
    public McpToolAdapter(McpHttpTransport transport) {
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
    }

    @Override
    public Promise<ToolExecutionResult> handle(ToolExecutionEnvelope envelope, ToolContract contract) {
        String remoteEndpoint = contract.remoteEndpoint();
        if (remoteEndpoint == null || remoteEndpoint.isBlank()) {
            return Promise.of(ToolExecutionResult.denied(
                    envelope.invocationId(),
                    "McpToolAdapter requires a non-blank remoteEndpoint in ToolContract",
                    Instant.now()));
        }

        McpToolRequest request = McpToolRequest.of(
                envelope.invocationId(),
                contract.name(),
                envelope.input());

        Instant start = Instant.now();
        String requestJson = request.toJson();

        LOG.debug("MCP tool call: endpoint={} toolName={} invocationId={}",
                remoteEndpoint, contract.name(), envelope.invocationId());

        return transport.post(remoteEndpoint, requestJson, JSON_HEADERS)
                .then(
                        responseBody -> {
                            Instant end = Instant.now();
                            Duration elapsed = Duration.between(start, end);
                            McpToolResponse response = McpToolResponse.parse(envelope.invocationId(), responseBody);
                            if (response.success()) {
                                LOG.debug("MCP tool succeeded: toolName={} invocationId={}",
                                        contract.name(), envelope.invocationId());
                                return Promise.of(ToolExecutionResult.succeeded(
                                        envelope.invocationId(),
                                        response.content(),
                                        Map.of(),
                                        envelope.invocationId(),
                                        end,
                                        elapsed));
                            } else {
                                LOG.warn("MCP tool error: toolName={} errorCode={} message={}",
                                        contract.name(), response.errorCode(), response.errorMessage());
                                return Promise.of(ToolExecutionResult.failed(
                                        envelope.invocationId(),
                                        "MCP error [" + response.errorCode() + "]: " + response.errorMessage(),
                                        envelope.invocationId(),
                                        end,
                                        elapsed));
                            }
                        },
                        ex -> {
                            Instant end = Instant.now();
                            String msg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
                            LOG.error("MCP transport failure: toolName={} invocationId={} error={}",
                                    contract.name(), envelope.invocationId(), msg);
                            // Map timeout-like exceptions to TIMEOUT status
                            if (msg.toLowerCase().contains("timeout") || msg.toLowerCase().contains("timed out")) {
                                return Promise.of(new ToolExecutionResult(
                                        envelope.invocationId(),
                                        ToolExecutionStatus.TIMEOUT,
                                        null, "ALLOW", "N/A",
                                        Map.of(), msg, null,
                                        end, Duration.between(start, end)));
                            }
                            return Promise.of(ToolExecutionResult.failed(
                                    envelope.invocationId(),
                                    msg,
                                    envelope.invocationId(),
                                    end,
                                    Duration.between(start, end)));
                        });
    }
}
