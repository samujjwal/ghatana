/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.toolruntime.mcp;

import java.util.Objects;

/**
 * Normalized MCP tool call response, deserialized from a JSON-RPC 2.0 response payload.
 *
 * <p>A successful MCP response carries the tool output under {@code result.content[0].text}.
 * An error response carries an {@code error} object with a numeric {@code code} and string
 * {@code message}.
 *
 * @param id          correlates with the originating {@link McpToolRequest#id()}
 * @param success     {@code true} if the server returned a {@code result}; {@code false} if {@code error}
 * @param content     the tool output string when successful; null on error
 * @param errorCode   the MCP/JSON-RPC error code when not successful; null on success
 * @param errorMessage the human-readable error message when not successful; null on success
 *
 * @doc.type record
 * @doc.purpose MCP tool call response payload (JSON-RPC 2.0)
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record McpToolResponse(
        String id,
        boolean success,
        String content,
        Integer errorCode,
        String errorMessage) {

    public McpToolResponse {
        Objects.requireNonNull(id, "id must not be null");
    }

    /**
     * Returns a successful response.
     *
     * @param id      matching request ID
     * @param content output text from the tool
     * @return a success response
     */
    public static McpToolResponse succeeded(String id, String content) {
        return new McpToolResponse(id, true, content, null, null);
    }

    /**
     * Returns an error response.
     *
     * @param id           matching request ID
     * @param errorCode    JSON-RPC error code
     * @param errorMessage error description
     * @return an error response
     */
    public static McpToolResponse error(String id, int errorCode, String errorMessage) {
        return new McpToolResponse(id, false, null, errorCode, errorMessage);
    }

    /**
     * Parses a minimal JSON-RPC 2.0 MCP response string into a {@code McpToolResponse}.
     *
     * <p>This implementation handles the known MCP response shapes:
     * <ul>
     *   <li>Success: {@code {"id":"...","result":{"content":[{"text":"..."}]}}}</li>
     *   <li>Error: {@code {"id":"...","error":{"code":-32601,"message":"..."}}}}</li>
     * </ul>
     *
     * @param id      the request ID (used as fallback if not extractable from JSON)
     * @param rawJson the raw JSON-RPC response body
     * @return parsed response
     */
    public static McpToolResponse parse(String id, String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return error(id, -32700, "Empty response from MCP server");
        }
        // Check for error object
        if (rawJson.contains("\"error\"")) {
            int codeStart = rawJson.indexOf("\"code\":");
            int msgStart  = rawJson.indexOf("\"message\":");
            int code = -32600;
            String msg = "Unknown MCP error";
            if (codeStart >= 0) {
                // Extract number after "code":
                int numStart = codeStart + 7;
                int numEnd   = numStart;
                while (numEnd < rawJson.length() && (Character.isDigit(rawJson.charAt(numEnd)) || rawJson.charAt(numEnd) == '-')) {
                    numEnd++;
                }
                code = Integer.parseInt(rawJson.substring(numStart, numEnd).trim());
            }
            if (msgStart >= 0) {
                int valStart = rawJson.indexOf('"', msgStart + 10) + 1;
                int valEnd   = rawJson.indexOf('"', valStart);
                msg = rawJson.substring(valStart, valEnd);
            }
            return error(id, code, msg);
        }
        // Check for result / content
        int textIdx = rawJson.indexOf("\"text\":");
        if (textIdx >= 0) {
            int valStart = rawJson.indexOf('"', textIdx + 7) + 1;
            int valEnd   = rawJson.indexOf('"', valStart);
            return succeeded(id, rawJson.substring(valStart, valEnd));
        }
        return error(id, -32600, "Unrecognized MCP response format");
    }
}
