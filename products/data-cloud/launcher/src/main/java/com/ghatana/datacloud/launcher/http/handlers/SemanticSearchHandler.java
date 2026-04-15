package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.EntityRecord;
import com.ghatana.datacloud.plugins.vector.SimilaritySearch;
import com.ghatana.datacloud.plugins.vector.VectorMemoryPlugin;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * @doc.type class
 * @doc.purpose Auto-embed entities and expose semantic search and RAG endpoints
 * @doc.layer product
 * @doc.pattern Handler
 */
public final class SemanticSearchHandler {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final VectorMemoryPlugin vectorPlugin;
    private final DataCloudClient client;
    private final HttpHandlerSupport http;
    private final ObjectMapper objectMapper;

    public SemanticSearchHandler(VectorMemoryPlugin vectorPlugin,
                                 DataCloudClient client,
                                 HttpHandlerSupport http,
                                 ObjectMapper objectMapper) {
        this.vectorPlugin = Objects.requireNonNull(vectorPlugin, "vectorPlugin");
        this.client = Objects.requireNonNull(client, "client");
        this.http = Objects.requireNonNull(http, "http");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public Promise<Void> indexEntity(String tenantId, String collection, DataCloudClient.Entity entity) {
        try {
            UUID entityUuid = parseUuid(entity.id());
            EntityRecord record = EntityRecord.builder()
                .id(entityUuid)
                .tenantId(tenantId)
                .collectionName(collection)
                .data(entity.data())
                .metadata(Map.of(
                    "source", "entity-crud",
                    "sourceEntityId", entity.id(),
                    "indexedAt", Instant.now().toString(),
                    "collection", collection
                ))
                .createdAt(entity.createdAt())
                .updatedAt(entity.updatedAt())
                .version((int) entity.version())
                .active(true)
                .build();
            return vectorPlugin.store(record, tenantId);
        } catch (RuntimeException exception) {
            return Promise.ofException(exception);
        }
    }

    public Promise<Void> deleteEntity(String tenantId, String entityId) {
        return vectorPlugin.delete(parseUuid(entityId).toString(), tenantId).map(ignored -> null);
    }

    public Promise<HttpResponse> handleSimilarEntities(HttpRequest request) {
        String tenantId = http.resolveTenantId(request);
        String collection = request.getPathParameter("collection");
        String entityId = request.getQueryParameter("id");
        int count = Math.max(1, HttpHandlerSupport.parseIntParam(request.getQueryParameter("k"), 5));
        String requestId = http.resolveCorrelationId(request);

        if (entityId == null || entityId.isBlank()) {
            return Promise.of(http.errorResponse(400, "Query parameter 'id' is required"));
        }

        return vectorPlugin.findSimilar(parseUuid(entityId).toString(), count, true, tenantId)
            .map(results -> http.jsonResponse(Map.of(
                "collection", collection,
                "entityId", entityId,
                "matches", results.getResults().stream().map(match -> Map.of(
                    "id", sourceEntityId(match),
                    "collection", match.getRecord().getRecord().getCollectionName(),
                    "score", match.getScore(),
                    "data", match.getRecord().getRecord().getData()
                )).toList(),
                "count", results.getResults().size(),
                "requestId", requestId
            ), requestId));
    }

    public Promise<HttpResponse> handleCollectionRag(HttpRequest request) {
        String tenantId = http.resolveTenantId(request);
        String collection = request.getPathParameter("collection");
        String requestId = http.resolveCorrelationId(request);

        return request.loadBody().then(body -> {
            Map<String, Object> payload = parseBody(body.getString(StandardCharsets.UTF_8));
            String question = stringValue(payload.get("question"));
            if (question == null || question.isBlank()) {
                return Promise.of(http.errorResponse(400, "'question' is required"));
            }

            int count = Math.max(1, numericValue(payload.get("k"), 5));
            return vectorPlugin.search(SimilaritySearch.SearchRequest.builder()
                    .tenantId(tenantId)
                    .queryText(question)
                    .k(count)
                    .build())
                .map(results -> {
                    List<Map<String, Object>> matches = results.getResults().stream()
                        .filter(result -> collection.equals(result.getRecord().getRecord().getCollectionName()))
                        .map(result -> Map.of(
                            "id", sourceEntityId(result),
                            "score", result.getScore(),
                            "data", result.getRecord().getRecord().getData(),
                            "excerpt", excerpt(result.getRecord().getEmbeddedContent())
                        ))
                        .toList();
                    return http.jsonResponse(Map.of(
                        "collection", collection,
                        "question", question,
                        "answer", buildGroundedAnswer(question, matches),
                        "context", matches,
                        "requestId", requestId
                    ), requestId);
                });
        });
    }

    public static float[] embedText(String content) {
        int dimensions = 128;
        float[] vector = new float[dimensions];
        String normalized = content == null ? "" : content.toLowerCase();
        String[] tokens = normalized.split("[^a-z0-9]+");
        int populated = 0;
        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            int index = Math.floorMod(token.hashCode(), dimensions);
            vector[index] += 1.0f;
            populated++;
        }
        if (populated == 0) {
            vector[0] = 1.0f;
        }

        float norm = 0.0f;
        for (float value : vector) {
            norm += value * value;
        }
        norm = (float) Math.sqrt(norm);
        for (int index = 0; index < vector.length; index++) {
            vector[index] = vector[index] / norm;
        }
        return vector;
    }

    private Map<String, Object> parseBody(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private String buildGroundedAnswer(String question, List<Map<String, Object>> matches) {
        if (matches.isEmpty()) {
            return "No grounded context was found for the requested question.";
        }

        StringBuilder builder = new StringBuilder("Grounded answer for '")
            .append(question)
            .append("': ");
        for (int index = 0; index < Math.min(3, matches.size()); index++) {
            Map<String, Object> match = matches.get(index);
            builder.append("match ").append(index + 1)
                .append(" (id=").append(match.get("id")).append(") ")
                .append(excerpt(stringValue(match.get("excerpt"))));
            if (index + 1 < Math.min(3, matches.size())) {
                builder.append(' ');
            }
        }
        return builder.toString();
    }

    private String excerpt(String text) {
        if (text == null || text.isBlank()) {
            return "contains structured entity data relevant to the query.";
        }
        return text.length() > 180 ? text.substring(0, 180) + "..." : text;
    }

    private int numericValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String rawValue) {
            try {
                return Integer.parseInt(rawValue);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private UUID parseUuid(String entityId) {
        try {
            return UUID.fromString(entityId);
        } catch (IllegalArgumentException ignored) {
            return UUID.nameUUIDFromBytes(entityId.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String sourceEntityId(SimilaritySearch.ScoredResult result) {
        Object sourceEntityId = result.getRecord().getRecord().getMetadata().get("sourceEntityId");
        return sourceEntityId == null ? result.getRecord().getRecord().getId().toString() : String.valueOf(sourceEntityId);
    }
}