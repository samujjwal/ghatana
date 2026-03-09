/**
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API - HTTP Response Factory
 * 
 * Factory for creating standardized HTTP responses with proper status codes
 * and JSON payloads. Compatible with ActiveJ framework.
 */

package com.ghatana.yappc.api.security;

import io.activej.http.HttpResponse;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Factory for creating HTTP responses with consistent format.
 * 
 * Provides methods for common response types:
 * - Success responses (200, 201)
 * - Error responses (400, 401, 403, 404, 409, 500)
 * - JSON payload generation
 
 * @doc.type class
 * @doc.purpose Handles http response factory operations
 * @doc.layer product
 * @doc.pattern Factory
*/
public final class HttpResponseFactory {
    
    private HttpResponseFactory() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Creates a 200 OK response with JSON payload.
     */
    @NotNull
    public static HttpResponse ok200(@NotNull Map<String, Object> data) {
        return PlatformCompatibility.createJsonResponse(200, toJson(data));
    }
    
    /**
     * Creates a 201 Created response with JSON payload.
     */
    @NotNull
    public static HttpResponse of201(@NotNull Map<String, Object> data) {
        return PlatformCompatibility.createJsonResponse(201, toJson(data));
    }
    
    /**
     * Creates a 400 Bad Request response.
     */
    @NotNull
    public static HttpResponse of400(@NotNull String message) {
        return errorResponse(400, "Bad Request", message);
    }
    
    /**
     * Creates a 401 Unauthorized response.
     */
    @NotNull
    public static HttpResponse of401(@NotNull String message) {
        return errorResponse(401, "Unauthorized", message);
    }
    
    /**
     * Creates a 403 Forbidden response.
     */
    @NotNull
    public static HttpResponse of403(@NotNull String message) {
        return errorResponse(403, "Forbidden", message);
    }
    
    /**
     * Creates a 404 Not Found response.
     */
    @NotNull
    public static HttpResponse of404(@NotNull String message) {
        return errorResponse(404, "Not Found", message);
    }
    
    /**
     * Creates a 409 Conflict response.
     */
    @NotNull
    public static HttpResponse of409(@NotNull String message) {
        return errorResponse(409, "Conflict", message);
    }
    
    /**
     * Creates a 500 Internal Server Error response.
     */
    @NotNull
    public static HttpResponse of500(@NotNull String message) {
        return errorResponse(500, "Internal Server Error", message);
    }
    
    /**
     * Creates a standardized error response.
     */
    @NotNull
    private static HttpResponse errorResponse(int status, @NotNull String errorType, @NotNull String message) {
        Map<String, Object> errorData = Map.of(
            "error", message,
            "errorType", errorType,
            "timestamp", System.currentTimeMillis()
        );
        
        return PlatformCompatibility.createJsonResponse(status, toJson(errorData));
    }
    
    /**
     * Converts a Map to JSON string (simplified implementation).
     * In production, use a proper JSON library like Jackson.
     */
    @NotNull
    private static String toJson(@NotNull Map<String, Object> data) {
        try {
            StringBuilder json = new StringBuilder();
            json.append("{");
            
            boolean first = true;
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                if (!first) json.append(",");
                first = false;
                
                json.append("\"").append(entry.getKey()).append("\":");
                
                Object value = entry.getValue();
                if (value instanceof String) {
                    json.append("\"").append(value.toString().replace("\"", "\\\"")).append("\"");
                } else if (value instanceof Map) {
                    json.append(toJson((Map<String, Object>) value));
                } else if (value instanceof java.util.List) {
                    json.append(toJsonList((java.util.List<?>) value));
                } else {
                    json.append(value.toString());
                }
            }
            
            json.append("}");
            return json.toString();
            
        } catch (Exception e) {
            // Fallback to simple error JSON
            return "{\"error\":\"Failed to serialize response\"}";
        }
    }
    
    /**
     * Converts a List to JSON array string.
     */
    @NotNull
    private static String toJsonList(@NotNull java.util.List<?> list) {
        StringBuilder json = new StringBuilder();
        json.append("[");
        
        boolean first = true;
        for (Object item : list) {
            if (!first) json.append(",");
            first = false;
            
            if (item instanceof String) {
                json.append("\"").append(item.toString().replace("\"", "\\\"")).append("\"");
            } else if (item instanceof Map) {
                json.append(toJson((Map<String, Object>) item));
            } else {
                json.append(item.toString());
            }
        }
        
        json.append("]");
        return json.toString();
    }
}
