package com.ghatana.platform.http.server.response;

import io.activej.http.HttpHeaders;
import io.activej.http.HttpResponse;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kernel Response Mappers
 *
 * Provides shared response mappers for list, detail, and action responses.
 * Ensures consistent response structure across all HTTP endpoints.
 *
 * <p>Response structures:</p>
 * <ul>
 *   <li>List response: { items: [...], count: N, total: N }</li>
 *   <li>Detail response: { data: {...} }</li>
 *   <li>Action response: { success: true, data: {...} }</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Shared response mappers for HTTP endpoints
 * @doc.layer platform
 * @doc.pattern Mapper, Builder
 */
public final class ResponseMappers {

    private ResponseMappers() {
        // Utility class - prevent instantiation
    }

    /**
     * Create a list response.
     *
     * @param items the list of items
     * @param correlationId request correlation ID
     * @param <T> item type
     * @return HTTP response with list structure
     */
    public static <T> HttpResponse listResponse(List<T> items, String correlationId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("items", items);
        response.put("count", items.size());
        response.put("total", items.size());

        return jsonResponse(200, response, correlationId);
    }

    /**
     * Create a list response with pagination.
     *
     * @param items the list of items
     * @param total total number of items (for pagination)
     * @param correlationId request correlation ID
     * @param <T> item type
     * @return HTTP response with list structure
     */
    public static <T> HttpResponse listResponse(List<T> items, int total, String correlationId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("items", items);
        response.put("count", items.size());
        response.put("total", total);

        return jsonResponse(200, response, correlationId);
    }

    /**
     * Create a detail response.
     *
     * @param data the detail data
     * @param correlationId request correlation ID
     * @return HTTP response with detail structure
     */
    public static HttpResponse detailResponse(Map<String, Object> data, String correlationId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);

        return jsonResponse(200, response, correlationId);
    }

    /**
     * Create a detail response with a single object.
     *
     * @param data the detail data
     * @param correlationId request correlation ID
     * @param <T> data type
     * @return HTTP response with detail structure
     */
    public static <T> HttpResponse detailResponse(T data, String correlationId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);

        return jsonResponse(200, response, correlationId);
    }

    /**
     * Create an action success response.
     *
     * @param data the action result data
     * @param correlationId request correlation ID
     * @return HTTP response with action structure
     */
    public static HttpResponse actionResponse(Map<String, Object> data, String correlationId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("data", data);

        return jsonResponse(200, response, correlationId);
    }

    /**
     * Create an action success response with a single object.
     *
     * @param data the action result data
     * @param correlationId request correlation ID
     * @param <T> data type
     * @return HTTP response with action structure
     */
    public static <T> HttpResponse actionResponse(T data, String correlationId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("data", data);

        return jsonResponse(200, response, correlationId);
    }

    /**
     * Create an action success response with no data.
     *
     * @param correlationId request correlation ID
     * @return HTTP response with action structure
     */
    public static HttpResponse actionResponse(String correlationId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);

        return jsonResponse(200, response, correlationId);
    }

    /**
     * Create a generic JSON response.
     *
     * @param statusCode HTTP status code
     * @param data the response data
     * @param correlationId request correlation ID
     * @return HTTP response
     */
    public static HttpResponse jsonResponse(int statusCode, Map<String, Object> data, String correlationId) {
        String jsonBody = toJsonString(data);
        return HttpResponse.ofCode(statusCode)
            .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
            .withHeader(HttpHeaders.of("X-Correlation-ID"), correlationId)
            .withBody(jsonBody.getBytes(StandardCharsets.UTF_8))
            .build();
    }

    /**
     * Create a 201 Created response.
     *
     * @param data the created resource data
     * @param correlationId request correlation ID
     * @return HTTP response
     */
    public static HttpResponse createdResponse(Map<String, Object> data, String correlationId) {
        return jsonResponse(201, data, correlationId);
    }

    /**
     * Create a 204 No Content response.
     *
     * @param correlationId request correlation ID
     * @return HTTP response
     */
    public static HttpResponse noContentResponse(String correlationId) {
        return HttpResponse.ofCode(204)
            .withHeader(HttpHeaders.of("X-Correlation-ID"), correlationId)
            .build();
    }

    /**
     * Convert a map to JSON string.
     *
     * @param data the data to convert
     * @return JSON string
     */
    private static String toJsonString(Map<String, Object> data) {
        // Simple JSON serialization - in production use Jackson or similar
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append("\"").append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value instanceof String) {
                sb.append("\"").append(escapeJson((String) value)).append("\"");
            } else if (value instanceof Map) {
                sb.append(toJsonString((Map<String, Object>) value));
            } else if (value instanceof List) {
                sb.append(toJsonList((List<?>) value));
            } else if (value == null) {
                sb.append("null");
            } else {
                sb.append(value);
            }
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Convert a list to JSON array string.
     *
     * @param list the list to convert
     * @return JSON array string
     */
    private static String toJsonList(List<?> list) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            Object value = list.get(i);
            if (value instanceof String) {
                sb.append("\"").append(escapeJson((String) value)).append("\"");
            } else if (value instanceof Map) {
                sb.append(toJsonString((Map<String, Object>) value));
            } else if (value instanceof List) {
                sb.append(toJsonList((List<?>) value));
            } else if (value == null) {
                sb.append("null");
            } else {
                sb.append(value);
            }
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Escape special characters in JSON string.
     *
     * @param value the value to escape
     * @return escaped value
     */
    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}
