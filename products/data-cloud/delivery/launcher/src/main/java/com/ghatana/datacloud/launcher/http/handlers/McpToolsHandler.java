package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * @doc.type class
 * @doc.purpose Expose Data Cloud capabilities as MCP-discoverable tools for external AI agents
 * @doc.layer product
 * @doc.pattern Handler
 */
public final class McpToolsHandler {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final String METHOD_TOOLS_CALL = "tools/call";

    private final DataCloudClient client;
    private final HttpHandlerSupport http;
    private final ObjectMapper objectMapper;
    private final CollectionContextDocumentLoader collectionContextDocumentLoader;

    public McpToolsHandler(
        DataCloudClient client,
        HttpHandlerSupport http,
        ObjectMapper objectMapper,
        CollectionContextDocumentLoader collectionContextDocumentLoader
    ) {
        this.client = Objects.requireNonNull(client, "client");
        this.http = Objects.requireNonNull(http, "http");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.collectionContextDocumentLoader = Objects.requireNonNull(
            collectionContextDocumentLoader,
            "collectionContextDocumentLoader");
    }

    public Promise<HttpResponse> handleListTools(HttpRequest request) {
        String tenantId = http.requireTenantIdOrFail(request);
        String requestId = http.resolveCorrelationId(request);
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(http.errorResponse(400, "tenantId is required", requestId));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("tools", toolRegistry());
        response.put("requestId", requestId);
        response.put("tenantId", tenantId);
        response.put("generatedAt", Instant.now().toString());
        return Promise.of(http.jsonResponse(response, requestId));
    }

    public Promise<HttpResponse> handleToolCall(HttpRequest request) {
        String tenantId = http.requireTenantIdOrFail(request);
        String requestId = http.resolveCorrelationId(request);
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(http.errorResponse(400, "tenantId is required", requestId));
        }

        return request.loadBody()
            .then(body -> {
                try {
                    String rawBody = body == null ? "{}" : new String(body.getArray(), StandardCharsets.UTF_8);
                    Map<String, Object> payload = objectMapper.readValue(rawBody, MAP_TYPE);
                    String method = String.valueOf(payload.getOrDefault("method", METHOD_TOOLS_CALL));
                    if (!METHOD_TOOLS_CALL.equals(method)) {
                        return Promise.of(jsonRpcError(requestId, -32601, "Unsupported MCP method: " + method));
                    }

                    @SuppressWarnings("unchecked")
                    Map<String, Object> params = (Map<String, Object>) payload.getOrDefault("params", Map.of());
                    String toolName = String.valueOf(params.getOrDefault("name", ""));
                    @SuppressWarnings("unchecked")
                    Map<String, Object> arguments = (Map<String, Object>) params.getOrDefault("arguments", Map.of());
                    if (toolName.isBlank()) {
                        return Promise.of(jsonRpcError(requestId, -32602, "Tool name is required"));
                    }

                    return invokeTool(tenantId, requestId, toolName, arguments)
                        .map(result -> jsonRpcSuccess(requestId, result))
                        .then(Promise::of, error -> Promise.of(jsonRpcError(
                            requestId,
                            -32603,
                            error.getMessage() != null ? error.getMessage() : "Internal MCP execution failure")));
                } catch (JsonProcessingException error) {
                    return Promise.of(jsonRpcError(requestId, -32700, "Invalid MCP JSON payload"));
                }
            });
    }

    private Promise<Map<String, Object>> invokeTool(String tenantId,
                                                    String requestId,
                                                    String toolName,
                                                    Map<String, Object> arguments) {
        return switch (toolName) {
            case "data_cloud_get_context" -> invokeGetContext(tenantId, requestId, arguments);
            case "data_cloud_query_entities" -> invokeQueryEntities(tenantId, arguments);
            case "data_cloud_append_event" -> invokeAppendEvent(tenantId, arguments);
            case "data_cloud_search" -> invokeSearch(tenantId, arguments);
            default -> Promise.ofException(new IllegalArgumentException("Unknown MCP tool: " + toolName));
        };
    }

    private Promise<Map<String, Object>> invokeGetContext(String tenantId,
                                                          String requestId,
                                                          Map<String, Object> arguments) {
        String collection = stringArgument(arguments, "collection");
        if (collection == null || collection.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("collection is required"));
        }

        return collectionContextDocumentLoader.load(tenantId, collection, requestId)
            .map(result -> result.orElseThrow(() -> new IllegalArgumentException("Collection not found: " + collection)));
    }

    @FunctionalInterface
    public interface CollectionContextDocumentLoader {
        Promise<Optional<Map<String, Object>>> load(String tenantId, String collection, String requestId);
    }

    private Promise<Map<String, Object>> invokeQueryEntities(String tenantId, Map<String, Object> arguments) {
        String collection = stringArgument(arguments, "collection");
        if (collection == null || collection.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("collection is required"));
        }

        int limit = intArgument(arguments, "limit", 25);
        return client.query(tenantId, collection, DataCloudClient.Query.limit(limit))
            .map(entities -> Map.of(
                "collection", collection,
                "count", entities.size(),
                "entities", entities.stream().map(entity -> Map.of(
                    "id", entity.id(),
                    "collection", entity.collection(),
                    "data", entity.data(),
                    "createdAt", entity.createdAt().toString(),
                    "updatedAt", entity.updatedAt().toString()
                )).toList()
            ));
    }

    private Promise<Map<String, Object>> invokeAppendEvent(String tenantId, Map<String, Object> arguments) {
        String eventType = Optional.ofNullable(stringArgument(arguments, "type"))
            .orElse(stringArgument(arguments, "eventType"));
        if (eventType == null || eventType.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("type is required"));
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) arguments.getOrDefault("payload", Map.of());
        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) arguments.getOrDefault("headers", Map.of());
        DataCloudClient.Event event = DataCloudClient.Event.builder()
            .type(eventType)
            .payload(payload)
            .headers(headers)
            .build();
        return client.appendEvent(tenantId, event)
            .map(offset -> Map.of(
                "type", eventType,
                "offset", offset.value(),
                "timestamp", event.timestamp().toString()
            ));
    }

    private Promise<Map<String, Object>> invokeSearch(String tenantId, Map<String, Object> arguments) {
        String collection = stringArgument(arguments, "collection");
        String query = stringArgument(arguments, "query");
        if (collection == null || collection.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("collection is required"));
        }
        if (query == null || query.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("query is required"));
        }

        int limit = intArgument(arguments, "limit", 25);
        String normalized = query.toLowerCase();
        return client.query(tenantId, collection, DataCloudClient.Query.limit(Math.max(limit * 4, limit)))
            .map(entities -> entities.stream()
                .filter(entity -> entity.data().values().stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .anyMatch(value -> value.toLowerCase().contains(normalized)))
                .limit(limit)
                .map(entity -> Map.of(
                    "id", entity.id(),
                    "collection", entity.collection(),
                    "data", entity.data()
                ))
                .toList())
            .map(matches -> Map.of(
                "collection", collection,
                "query", query,
                "count", matches.size(),
                "matches", matches
            ));
    }

    private HttpResponse jsonRpcSuccess(String requestId, Map<String, Object> result) {
        Map<String, Object> response = Map.of(
            "jsonrpc", "2.0",
            "id", requestId,
            "result", Map.of(
                "content", List.of(Map.of(
                    "type", "text",
                    "text", toJson(result)
                ))
            )
        );
        return http.jsonResponse(response, requestId);
    }

    private HttpResponse jsonRpcError(String requestId, int code, String message) {
        Map<String, Object> response = Map.of(
            "jsonrpc", "2.0",
            "id", requestId,
            "error", Map.of(
                "code", code,
                "message", message
            )
        );
        return http.jsonResponse(400, response);
    }

    private String toJson(Map<String, Object> result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception error) {
            throw new IllegalStateException("Failed to serialize MCP tool result", error);
        }
    }

    private List<Map<String, Object>> toolRegistry() {
        return List.of(
            toolDefinition(
                "data_cloud_get_context",
                "Return the unified Data Cloud context document for a collection.",
                Map.of(
                    "type", "object",
                    "required", List.of("collection"),
                    "properties", Map.of(
                        "collection", Map.of("type", "string")
                    )
                )),
            toolDefinition(
                "data_cloud_query_entities",
                "List entities from a tenant collection.",
                Map.of(
                    "type", "object",
                    "required", List.of("collection"),
                    "properties", Map.of(
                        "collection", Map.of("type", "string"),
                        "limit", Map.of("type", "integer", "minimum", 1, "default", 25)
                    )
                )),
            toolDefinition(
                "data_cloud_append_event",
                "Append a domain event into the Data Cloud event log.",
                Map.of(
                    "type", "object",
                    "required", List.of("type", "payload"),
                    "properties", Map.of(
                        "type", Map.of("type", "string"),
                        "payload", Map.of("type", "object", "additionalProperties", true),
                        "headers", Map.of("type", "object", "additionalProperties", Map.of("type", "string"))
                    )
                )),
            toolDefinition(
                "data_cloud_search",
                "Keyword search over entity payload values within a collection.",
                Map.of(
                    "type", "object",
                    "required", List.of("collection", "query"),
                    "properties", Map.of(
                        "collection", Map.of("type", "string"),
                        "query", Map.of("type", "string"),
                        "limit", Map.of("type", "integer", "minimum", 1, "default", 25)
                    )
                ))
        );
    }

    private Map<String, Object> toolDefinition(String name, String description, Map<String, Object> inputSchema) {
        return Map.of(
            "name", name,
            "description", description,
            "inputSchema", inputSchema
        );
    }

    private String stringArgument(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private int intArgument(Map<String, Object> arguments, String key, int defaultValue) {
        Object value = arguments.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}