/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.toolruntime.mcp;

import java.util.Map;
import java.util.Objects;

/**
 * Normalized MCP tool call request, serialized as a JSON-RPC 2.0 payload.
 *
 * <p>Per MCP revision 2025-03-26, tool calls use the method {@code "tools/call"}
 * with a {@code params} object containing the tool name and arguments.
 *
 * @param jsonrpc  always {@code "2.0"}
 * @param id       caller-assigned request ID (correlates with response)
 * @param method   always {@code "tools/call"}
 * @param toolName the name of the tool to invoke (MCP tool registry name)
 * @param params   key-value arguments for the tool
 *
 * @doc.type record
 * @doc.purpose MCP tool call request payload (JSON-RPC 2.0)
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record McpToolRequest(
        String jsonrpc,
        String id,
        String method,
        String toolName,
        Map<String, Object> params) {

    public McpToolRequest {
        Objects.requireNonNull(id,       "id must not be null");
        Objects.requireNonNull(toolName, "toolName must not be null");

        if (toolName.isBlank()) {
            throw new IllegalArgumentException("toolName must not be blank");
        }

        jsonrpc = "2.0";
        method  = "tools/call";
        params  = params != null ? Map.copyOf(params) : Map.of();
    }

    /**
     * Factory for the common case.
     *
     * @param id       request correlator
     * @param toolName tool to invoke
     * @param params   tool arguments
     * @return a new request
     */
    public static McpToolRequest of(String id, String toolName, Map<String, Object> params) {
        return new McpToolRequest("2.0", id, "tools/call", toolName, params);
    }

    /**
     * Serialize to the JSON-RPC 2.0 wire format expected by MCP servers.
     *
     * <p>Produces:
     * <pre>
     * {"jsonrpc":"2.0","id":"&lt;id&gt;","method":"tools/call","params":{"name":"&lt;tool&gt;","arguments":{...}}}
     * </pre>
     *
     * @return JSON string
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"jsonrpc\":\"2.0\",\"id\":\"").append(id).append("\",")
          .append("\"method\":\"tools/call\",")
          .append("\"params\":{\"name\":\"").append(toolName).append("\",")
          .append("\"arguments\":{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            appendJsonValue(sb, entry.getValue());
            first = false;
        }
        sb.append("}}}");
        return sb.toString();
    }

    private static void appendJsonValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            sb.append("\"").append(s.replace("\"", "\\\"")).append("\"");
        } else if (value instanceof Boolean || value instanceof Number) {
            sb.append(value);
        } else {
            // Fallback: quoted toString for complex types
            sb.append("\"").append(value.toString().replace("\"", "\\\"")).append("\"");
        }
    }
}
