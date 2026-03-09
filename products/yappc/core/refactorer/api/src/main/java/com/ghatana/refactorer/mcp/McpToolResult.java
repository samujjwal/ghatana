package com.ghatana.refactorer.mcp;

import java.util.Map;

/** Result of an MCP tool execution. Contains success status, result data, and optional message. 
 * @doc.type record
 * @doc.purpose Immutable data carrier for mcp tool result
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public record McpToolResult(boolean success, Map<String, Object> data, String message) {

    /** Creates a successful result with data. */
    public static McpToolResult success(Map<String, Object> data) {
        return new McpToolResult(true, data, null);
    }

    /** Creates a successful result with data and message. */
    public static McpToolResult success(Map<String, Object> data, String message) {
        return new McpToolResult(true, data, message);
    }

    /** Creates a failure result with error message. */
    public static McpToolResult failure(String message) {
        return new McpToolResult(false, null, message);
    }

    /** Creates a failure result with error message and details. */
    public static McpToolResult failure(String message, Map<String, Object> errorDetails) {
        return new McpToolResult(false, errorDetails, message);
    }
}
