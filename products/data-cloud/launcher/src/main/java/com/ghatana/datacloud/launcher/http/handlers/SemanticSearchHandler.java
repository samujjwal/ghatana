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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
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
    
    public enum EmbeddingMode {
        DETERMINISTIC_HASH,
        REAL_EMBEDDING
    }

    private final VectorMemoryPlugin vectorPlugin;
    private final HttpHandlerSupport http;
    private final ObjectMapper objectMapper;
    private final EmbeddingMode embeddingMode;
    private final String aiInferenceServiceUrl;
    private final String internalApiKey;

    public SemanticSearchHandler(VectorMemoryPlugin vectorPlugin,
                                 DataCloudClient client,
                                 HttpHandlerSupport http,
                                 ObjectMapper objectMapper) {
        this(vectorPlugin, client, http, objectMapper, 
             EmbeddingMode.valueOf(System.getenv().getOrDefault("EMBEDDING_MODE", "DETERMINISTIC_HASH")),
             System.getenv().getOrDefault("AI_INFERENCE_SERVICE_URL", "http://localhost:8083"),
             System.getenv().getOrDefault("INTERNAL_API_KEY", ""));
    }

    public SemanticSearchHandler(VectorMemoryPlugin vectorPlugin,
                                 DataCloudClient client,
                                 HttpHandlerSupport http,
                                 ObjectMapper objectMapper,
                                 EmbeddingMode embeddingMode,
                                 String aiInferenceServiceUrl,
                                 String internalApiKey) {
        this.vectorPlugin = Objects.requireNonNull(vectorPlugin, "vectorPlugin");
        Objects.requireNonNull(client, "client");
        this.http = Objects.requireNonNull(http, "http");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.embeddingMode = Objects.requireNonNull(embeddingMode, "embeddingMode");
        this.aiInferenceServiceUrl = Objects.requireNonNull(aiInferenceServiceUrl, "aiInferenceServiceUrl");
        this.internalApiKey = internalApiKey != null ? internalApiKey : "";
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
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
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
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
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
        return embedTextHash(content);
    }

    public float[] embedTextWithMode(String content, String tenantId) {
        if (embeddingMode == EmbeddingMode.REAL_EMBEDDING) {
            try {
                return embedTextViaAI(content, tenantId);
            } catch (Exception e) {
                // Fallback to hash mode if AI service is unavailable
                return embedTextHash(content);
            }
        }
        return embedTextHash(content);
    }

    private float[] embedTextViaAI(String content, String tenantId) {
        try {
            HttpClient httpClient = HttpClient.newHttpClient();
            
            Map<String, Object> requestBody = Map.of(
                "tenant", tenantId,
                "text", content
            );
            
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            
            java.net.http.HttpRequest.Builder requestBuilder = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create(aiInferenceServiceUrl + "/v1/embeddings"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + internalApiKey)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jsonBody));

            java.net.http.HttpRequest request = requestBuilder.build();

            java.net.http.HttpResponse<String> response = httpClient.send(
                request,
                java.net.http.HttpResponse.BodyHandlers.ofString()
            );
            
            if (response.statusCode() != 200) {
                throw new RuntimeException("AI inference service returned status " + response.statusCode());
            }
            
            Map<String, Object> responseBody = objectMapper.readValue(response.body(), MAP_TYPE);
            List<Number> vectorList = (List<Number>) responseBody.get("vector");
            
            float[] vector = new float[vectorList.size()];
            for (int i = 0; i < vectorList.size(); i++) {
                vector[i] = vectorList.get(i).floatValue();
            }
            
            return vector;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to call AI inference service", e);
        }
    }

    private static float[] embedTextHash(String content) {
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

    public EmbeddingMode getEmbeddingMode() {
        return embeddingMode;
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