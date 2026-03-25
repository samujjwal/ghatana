package com.ghatana.products.yappc.domain.vector.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.http.server.response.ResponseBuilder;
import com.ghatana.products.yappc.domain.vector.RagService;
import com.ghatana.products.yappc.domain.vector.SemanticSearchService;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * HTTP Controller for vector search and RAG operations.
 * <p>
 * Provides REST endpoints for semantic search, document indexing,
 * and retrieval-augmented generation.
 *
 * @doc.type class
 * @doc.purpose HTTP API for vector operations
 * @doc.layer product
 * @doc.pattern Controller
 */
public class VectorController {

    private static final Logger LOG = LoggerFactory.getLogger(VectorController.class);

    private final SemanticSearchService searchService;
    private final RagService ragService;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new VectorController.
     *
     * @param searchService The semantic search service
     * @param ragService The RAG service
     * @param objectMapper JSON mapper
     */
    public VectorController(
        @NotNull SemanticSearchService searchService,
        @NotNull RagService ragService,
        @NotNull ObjectMapper objectMapper
    ) {
        this.searchService = Objects.requireNonNull(searchService);
        this.ragService = Objects.requireNonNull(ragService);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    // ==================== SEMANTIC SEARCH ====================

    /**
     * Performs semantic search.
     * POST /api/v1/vector/search
     */
    @NotNull
    public Promise<HttpResponse> search(@NotNull HttpRequest request) {
        return request.loadBody()
            .then(body -> {
                try {
                    SearchDto dto = objectMapper.readValue(body.getString(StandardCharsets.UTF_8), SearchDto.class);

                    SemanticSearchService.SemanticSearchRequest searchRequest =
                        new SemanticSearchService.SemanticSearchRequest(
                            dto.query(),
                            dto.limit() > 0 ? dto.limit() : 10,
                            dto.threshold() > 0 ? dto.threshold() : 0.7,
                            dto.filters()
                        );

                    return searchService.search(searchRequest)
                        .map(result -> ResponseBuilder.ok().json(result).build());

                } catch (Exception e) {
                    LOG.error("Search request failed", e);
                    return Promise.of(ResponseBuilder.badRequest()
                        .json(Map.of("error", "Invalid request: " + e.getMessage()))
                        .build());
                }
            });
    }

    /**
     * Performs hybrid search (semantic + keyword).
     * POST /api/v1/vector/search/hybrid
     */
    @NotNull
    public Promise<HttpResponse> hybridSearch(@NotNull HttpRequest request) {
        return request.loadBody()
            .then(body -> {
                try {
                    HybridSearchDto dto = objectMapper.readValue(
                        body.getString(StandardCharsets.UTF_8),
                        HybridSearchDto.class
                    );

                    SemanticSearchService.HybridSearchRequest hybridRequest =
                        new SemanticSearchService.HybridSearchRequest(
                            dto.query(),
                            dto.limit() > 0 ? dto.limit() : 10,
                            dto.threshold() > 0 ? dto.threshold() : 0.7,
                            dto.filters(),
                            dto.keywords(),
                            dto.keywordBoost() > 0 ? dto.keywordBoost() : 0.2
                        );

                    return searchService.hybridSearch(hybridRequest)
                        .map(result -> ResponseBuilder.ok().json(result).build());

                } catch (Exception e) {
                    LOG.error("Hybrid search request failed", e);
                    return Promise.of(ResponseBuilder.badRequest()
                        .json(Map.of("error", "Invalid request: " + e.getMessage()))
                        .build());
                }
            });
    }

    /**
     * Finds similar items by ID.
     * GET /api/v1/vector/similar/:id
     */
    @NotNull
    public Promise<HttpResponse> findSimilar(@NotNull HttpRequest request, @NotNull String id) {
        int limit = getIntParam(request, "limit", 10);
        double threshold = getDoubleParam(request, "threshold", 0.7);

        return searchService.findSimilar(id, limit, threshold)
            .map(hits -> ResponseBuilder.ok()
                .json(Map.of(
                    "sourceId", id,
                    "similar", hits,
                    "count", hits.size()
                ))
                .build());
    }

    // ==================== INDEXING ====================

    /**
     * Indexes a document.
     * POST /api/v1/vector/index
     */
    @NotNull
    public Promise<HttpResponse> indexDocument(@NotNull HttpRequest request) {
        return request.loadBody()
            .then(body -> {
                try {
                    IndexDocumentDto dto = objectMapper.readValue(
                        body.getString(StandardCharsets.UTF_8),
                        IndexDocumentDto.class
                    );

                    SemanticSearchService.IndexRequest indexRequest =
                        new SemanticSearchService.IndexRequest(
                            dto.id(),
                            dto.content(),
                            dto.metadata()
                        );

                    return searchService.index(indexRequest)
                        .map(result -> result.success()
                            ? ResponseBuilder.ok().json(result).build()
                            : ResponseBuilder.internalServerError()
                                .json(Map.of("error", result.error()))
                                .build());

                } catch (Exception e) {
                    LOG.error("Index request failed", e);
                    return Promise.of(ResponseBuilder.badRequest()
                        .json(Map.of("error", "Invalid request: " + e.getMessage()))
                        .build());
                }
            });
    }

    /**
     * Batch indexes multiple documents.
     * POST /api/v1/vector/index/batch
     */
    @NotNull
    public Promise<HttpResponse> batchIndex(@NotNull HttpRequest request) {
        return request.loadBody()
            .then(body -> {
                try {
                    BatchIndexDto dto = objectMapper.readValue(
                        body.getString(StandardCharsets.UTF_8),
                        BatchIndexDto.class
                    );

                    List<SemanticSearchService.IndexRequest> requests = dto.documents().stream()
                        .map(doc -> new SemanticSearchService.IndexRequest(
                            doc.id(),
                            doc.content(),
                            doc.metadata()
                        ))
                        .toList();

                    return searchService.batchIndex(requests)
                        .map(results -> {
                            long successCount = results.stream()
                                .filter(SemanticSearchService.IndexResult::success)
                                .count();
                            long failCount = results.size() - successCount;

                            return ResponseBuilder.ok()
                                .json(Map.of(
                                    "results", results,
                                    "total", results.size(),
                                    "success", successCount,
                                    "failed", failCount
                                ))
                                .build();
                        });

                } catch (Exception e) {
                    LOG.error("Batch index request failed", e);
                    return Promise.of(ResponseBuilder.badRequest()
                        .json(Map.of("error", "Invalid request: " + e.getMessage()))
                        .build());
                }
            });
    }

    /**
     * Deletes a document from the index.
     * DELETE /api/v1/vector/index/:id
     */
    @NotNull
    public Promise<HttpResponse> deleteDocument(@NotNull HttpRequest request, @NotNull String id) {
        return searchService.delete(id)
            .map(deleted -> deleted
                ? ResponseBuilder.noContent().build()
                : ResponseBuilder.notFound()
                    .json(Map.of("error", "Document not found: " + id))
                    .build());
    }

    // ==================== RAG ====================

    /**
     * Generates a RAG response.
     * POST /api/v1/vector/rag
     */
    @NotNull
    public Promise<HttpResponse> rag(@NotNull HttpRequest request) {
        return request.loadBody()
            .then(body -> {
                try {
                    RagDto dto = objectMapper.readValue(body.getString(StandardCharsets.UTF_8), RagDto.class);

                    RagService.RagRequest ragRequest = new RagService.RagRequest(
                        dto.query(),
                        dto.systemPrompt(),
                        dto.contextLimit() > 0 ? dto.contextLimit() : 5,
                        dto.relevanceThreshold() > 0 ? dto.relevanceThreshold() : 0.7,
                        dto.maxTokens() > 0 ? dto.maxTokens() : 1000,
                        dto.temperature() > 0 ? dto.temperature() : 0.7,
                        dto.filters()
                    );

                    return ragService.generate(ragRequest)
                        .map(result -> result.success()
                            ? ResponseBuilder.ok().json(result).build()
                            : ResponseBuilder.internalServerError()
                                .json(Map.of("warning", result.warning()))
                                .build());

                } catch (Exception e) {
                    LOG.error("RAG request failed", e);
                    return Promise.of(ResponseBuilder.badRequest()
                        .json(Map.of("error", "Invalid request: " + e.getMessage()))
                        .build());
                }
            });
    }

    /**
     * Generates a conversational RAG response.
     * POST /api/v1/vector/rag/chat
     */
    @NotNull
    public Promise<HttpResponse> ragChat(@NotNull HttpRequest request) {
        return request.loadBody()
            .then(body -> {
                try {
                    ConversationalRagDto dto = objectMapper.readValue(
                        body.getString(StandardCharsets.UTF_8),
                        ConversationalRagDto.class
                    );

                    List<RagService.ConversationTurn> history = dto.history() != null
                        ? dto.history().stream()
                            .map(turn -> new RagService.ConversationTurn(
                                turn.userMessage(),
                                turn.assistantMessage()
                            ))
                            .toList()
                        : List.of();

                    RagService.ConversationalRagRequest chatRequest =
                        new RagService.ConversationalRagRequest(
                            dto.query(),
                            history,
                            dto.systemPrompt(),
                            dto.contextLimit() > 0 ? dto.contextLimit() : 5,
                            dto.relevanceThreshold() > 0 ? dto.relevanceThreshold() : 0.7,
                            dto.maxTokens() > 0 ? dto.maxTokens() : 1000,
                            dto.temperature() > 0 ? dto.temperature() : 0.7,
                            dto.filters()
                        );

                    return ragService.chat(chatRequest)
                        .map(result -> result.success()
                            ? ResponseBuilder.ok().json(result).build()
                            : ResponseBuilder.internalServerError()
                                .json(Map.of("warning", result.warning()))
                                .build());

                } catch (Exception e) {
                    LOG.error("Conversational RAG request failed", e);
                    return Promise.of(ResponseBuilder.badRequest()
                        .json(Map.of("error", "Invalid request: " + e.getMessage()))
                        .build());
                }
            });
    }

    // ==================== HELPER METHODS ====================

    private int getIntParam(HttpRequest request, String name, int defaultValue) {
        String value = request.getQueryParameter(name);
        if (value != null && !value.isEmpty()) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private double getDoubleParam(HttpRequest request, String name, double defaultValue) {
        String value = request.getQueryParameter(name);
        if (value != null && !value.isEmpty()) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    // ==================== DTOs ====================

    /**
     * Search request DTO
     */
    public record SearchDto(
        @NotNull String query,
        int limit,
        double threshold,
        @Nullable Map<String, String> filters
    ) {}

    /**
     * Hybrid search request DTO
     */
    public record HybridSearchDto(
        @NotNull String query,
        int limit,
        double threshold,
        @Nullable Map<String, String> filters,
        @Nullable List<String> keywords,
        double keywordBoost
    ) {}

    /**
     * Index document DTO
     */
    public record IndexDocumentDto(
        @NotNull String id,
        @NotNull String content,
        @Nullable Map<String, String> metadata
    ) {}

    /**
     * Batch index DTO
     */
    public record BatchIndexDto(
        @NotNull List<IndexDocumentDto> documents
    ) {}

    /**
     * RAG request DTO
     */
    public record RagDto(
        @NotNull String query,
        @Nullable String systemPrompt,
        int contextLimit,
        double relevanceThreshold,
        int maxTokens,
        double temperature,
        @Nullable Map<String, String> filters
    ) {}

    /**
     * Conversational RAG DTO
     */
    public record ConversationalRagDto(
        @NotNull String query,
        @Nullable List<ConversationTurnDto> history,
        @Nullable String systemPrompt,
        int contextLimit,
        double relevanceThreshold,
        int maxTokens,
        double temperature,
        @Nullable Map<String, String> filters
    ) {}

    /**
     * Conversation turn DTO
     */
    public record ConversationTurnDto(
        @NotNull String userMessage,
        @Nullable String assistantMessage
    ) {}
}
