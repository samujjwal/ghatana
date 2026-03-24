package com.ghatana.products.yappc.domain.vector;

import com.ghatana.ai.embedding.EmbeddingResult;
import com.ghatana.ai.embedding.EmbeddingService;
import com.ghatana.ai.vectorstore.VectorSearchResult;
import com.ghatana.ai.vectorstore.VectorStore;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for semantic search operations in the YAPPC product.
 * <p>
 * Provides high-level semantic search capabilities by coordinating
 * embedding generation and vector similarity search.
 *
 * @doc.type class
 * @doc.purpose Semantic search orchestration
 * @doc.layer product
 * @doc.pattern Service
 */
public class SemanticSearchService {

    private static final Logger LOG = LoggerFactory.getLogger(SemanticSearchService.class);

    private final VectorStore vectorStore;
    private final EmbeddingService embeddingService;

    /**
     * Creates a new SemanticSearchService.
     *
     * @param vectorStore The vector store for similarity search
     * @param embeddingService The embedding service for vector generation
     */
    public SemanticSearchService(
        @NotNull VectorStore vectorStore,
        @NotNull EmbeddingService embeddingService
    ) {
        this.vectorStore = Objects.requireNonNull(vectorStore);
        this.embeddingService = Objects.requireNonNull(embeddingService);
    }

    /**
     * Performs semantic search for a text query.
     *
     * @param request The search request
     * @return Promise resolving to search results
     */
    @NotNull
    public Promise<SemanticSearchResult> search(@NotNull SemanticSearchRequest request) {
        LOG.debug("Performing semantic search for: {}", request.query());
        long startTime = System.currentTimeMillis();

        return embeddingService.createEmbedding(request.query())
            .then(embedding -> {
                Map<String, String> filters = request.filters() != null
                    ? request.filters()
                    : Map.of();

                return vectorStore.search(
                    embedding.getVector(),
                    request.limit(),
                    request.threshold(),
                    filters
                );
            })
            .map(vectorResults -> {
                List<SearchHit> hits = vectorResults.stream()
                    .map(this::toSearchHit)
                    .collect(Collectors.toList());

                long duration = System.currentTimeMillis() - startTime;
                LOG.debug("Semantic search completed in {} ms with {} results",
                    duration, hits.size());

                return new SemanticSearchResult(
                    request.query(),
                    hits,
                    hits.size(),
                    duration,
                    null
                );
            })
            .then(Promise::of, e -> {
                LOG.error("Semantic search failed for query: {}", request.query(), e);
                return Promise.of(new SemanticSearchResult(
                    request.query(),
                    List.of(),
                    0,
                    System.currentTimeMillis() - startTime,
                    e.getMessage()
                ));
            });
    }

    /**
     * Performs hybrid search combining semantic and keyword search.
     *
     * @param request The hybrid search request
     * @return Promise resolving to combined search results
     */
    @NotNull
    public Promise<SemanticSearchResult> hybridSearch(@NotNull HybridSearchRequest request) {
        LOG.debug("Performing hybrid search for: {}", request.query());
        long startTime = System.currentTimeMillis();

        // Perform semantic search
        Promise<List<SearchHit>> semanticPromise = embeddingService.createEmbedding(request.query())
            .then(embedding -> vectorStore.search(
                embedding.getVector(),
                request.limit() * 2, // Get more for re-ranking
                request.threshold(),
                request.filters() != null ? request.filters() : Map.of()
            ))
            .map(results -> results.stream()
                .map(this::toSearchHit)
                .collect(Collectors.toList()));

        return semanticPromise.map(semanticHits -> {
            // Apply keyword boost if provided
            List<SearchHit> boostedHits = semanticHits;
            if (request.keywords() != null && !request.keywords().isEmpty()) {
                boostedHits = boostByKeywords(semanticHits, request.keywords(), request.keywordBoost());
            }

            // Sort by final score and limit
            List<SearchHit> finalHits = boostedHits.stream()
                .sorted(Comparator.comparing(SearchHit::score).reversed())
                .limit(request.limit())
                .collect(Collectors.toList());

            long duration = System.currentTimeMillis() - startTime;

            return new SemanticSearchResult(
                request.query(),
                finalHits,
                finalHits.size(),
                duration,
                null
            );
        });
    }

    /**
     * Finds similar items by ID.
     *
     * @param id The source item ID
     * @param limit Maximum results
     * @param threshold Minimum similarity
     * @return Promise resolving to similar items
     */
    @NotNull
    public Promise<List<SearchHit>> findSimilar(
        @NotNull String id,
        int limit,
        double threshold
    ) {
        LOG.debug("Finding similar items for: {}", id);

        return vectorStore.searchById(id, limit, threshold)
            .map(results -> results.stream()
                .filter(r -> !r.getId().equals(id)) // Exclude self
                .map(this::toSearchHit)
                .collect(Collectors.toList()));
    }

    /**
     * Indexes a document for semantic search.
     *
     * @param request The indexing request
     * @return Promise resolving when indexing is complete
     */
    @NotNull
    public Promise<IndexResult> index(@NotNull IndexRequest request) {
        LOG.debug("Indexing document: {}", request.id());
        long startTime = System.currentTimeMillis();

        return embeddingService.createEmbedding(request.content())
            .then(embedding -> vectorStore.store(
                request.id(),
                request.content(),
                embedding.getVector(),
                request.metadata() != null ? request.metadata() : Map.of()
            ).map(v -> embedding))
            .map(embedding -> {
                long duration = System.currentTimeMillis() - startTime;
                LOG.debug("Indexed document {} in {} ms", request.id(), duration);

                return new IndexResult(
                    request.id(),
                    true,
                    duration,
                    embedding.getVector().length,
                    null
                );
            })
            .then(Promise::of, e -> {
                LOG.error("Failed to index document: {}", request.id(), e);
                return Promise.of(new IndexResult(
                    request.id(),
                    false,
                    System.currentTimeMillis() - startTime,
                    0,
                    e.getMessage()
                ));
            });
    }

    /**
     * Batch indexes multiple documents.
     *
     * @param requests The indexing requests
     * @return Promise resolving to indexing results
     */
    @NotNull
    public Promise<List<IndexResult>> batchIndex(@NotNull List<IndexRequest> requests) {
        LOG.debug("Batch indexing {} documents", requests.size());

        if (requests.isEmpty()) {
            return Promise.of(List.of());
        }

        // Process in batches to avoid overwhelming the embedding service
        int batchSize = 10;
        List<List<IndexRequest>> batches = new ArrayList<>();
        for (int i = 0; i < requests.size(); i += batchSize) {
            batches.add(requests.subList(i, Math.min(i + batchSize, requests.size())));
        }

        List<Promise<List<IndexResult>>> batchPromises = batches.stream()
            .map(this::processBatch)
            .collect(Collectors.toList());

        return Promises.reduce(
            new ArrayList<IndexResult>(),
            (acc, results) -> acc.addAll(results),
            acc -> acc,
            batchPromises
        );
    }

    private Promise<List<IndexResult>> processBatch(List<IndexRequest> batch) {
        List<String> contents = batch.stream()
            .map(IndexRequest::content)
            .collect(Collectors.toList());

        return embeddingService.createEmbeddings(contents)
            .then(embeddings -> {
                List<Promise<IndexResult>> storePromises = new ArrayList<>();

                for (int i = 0; i < batch.size(); i++) {
                    IndexRequest request = batch.get(i);
                    EmbeddingResult embedding = embeddings.get(i);

                    Promise<IndexResult> storePromise = vectorStore.store(
                        request.id(),
                        request.content(),
                        embedding.getVector(),
                        request.metadata() != null ? request.metadata() : Map.of()
                    ).map(v -> new IndexResult(
                        request.id(),
                        true,
                        0,
                        embedding.getVector().length,
                        null
                    )).then(Promise::of, e -> Promise.of(new IndexResult(
                        request.id(),
                        false,
                        0,
                        0,
                        e.getMessage()
                    )));

                    storePromises.add(storePromise);
                }

                return Promises.toList(storePromises);
            });
    }

    /**
     * Deletes a document from the index.
     *
     * @param id The document ID
     * @return Promise resolving when deletion is complete
     */
    @NotNull
    public Promise<Boolean> delete(@NotNull String id) {
        LOG.debug("Deleting document from index: {}", id);
        return vectorStore.delete(id)
            .map(v -> true)
            .then(Promise::of, e -> {
                LOG.error("Failed to delete document: {}", id, e);
                return Promise.of(false);
            });
    }

    // ==================== HELPER METHODS ====================

    private SearchHit toSearchHit(VectorSearchResult result) {
        return new SearchHit(
            result.getId(),
            result.getContent(),
            result.getSimilarity(),
            result.getMetadata()
        );
    }

    private List<SearchHit> boostByKeywords(
        List<SearchHit> hits,
        List<String> keywords,
        double boostFactor
    ) {
        return hits.stream()
            .map(hit -> {
                String contentLower = hit.content().toLowerCase();
                long matchCount = keywords.stream()
                    .filter(kw -> contentLower.contains(kw.toLowerCase()))
                    .count();

                if (matchCount > 0) {
                    double boost = 1 + (boostFactor * matchCount / keywords.size());
                    return new SearchHit(
                        hit.id(),
                        hit.content(),
                        Math.min(1.0, hit.score() * boost),
                        hit.metadata()
                    );
                }
                return hit;
            })
            .collect(Collectors.toList());
    }

    // ==================== REQUEST/RESPONSE TYPES ====================

    /**
     * Semantic search request
     */
    public record SemanticSearchRequest(
        @NotNull String query,
        int limit,
        double threshold,
        @Nullable Map<String, String> filters
    ) {
        public SemanticSearchRequest {
            if (limit <= 0) limit = 10;
            if (threshold <= 0) threshold = 0.7;
        }

        public static SemanticSearchRequest of(String query) {
            return new SemanticSearchRequest(query, 10, 0.7, null);
        }
    }

    /**
     * Hybrid search request
     */
    public record HybridSearchRequest(
        @NotNull String query,
        int limit,
        double threshold,
        @Nullable Map<String, String> filters,
        @Nullable List<String> keywords,
        double keywordBoost
    ) {
        public HybridSearchRequest {
            if (limit <= 0) limit = 10;
            if (threshold <= 0) threshold = 0.7;
            if (keywordBoost <= 0) keywordBoost = 0.2;
        }
    }

    /**
     * Semantic search result
     */
    public record SemanticSearchResult(
        @NotNull String query,
        @NotNull List<SearchHit> hits,
        int totalHits,
        long durationMs,
        @Nullable String error
    ) {
        public boolean isSuccess() {
            return error == null;
        }
    }

    /**
     * Individual search hit
     */
    public record SearchHit(
        @NotNull String id,
        @NotNull String content,
        double score,
        @Nullable Map<String, String> metadata
    ) {}

    /**
     * Index request
     */
    public record IndexRequest(
        @NotNull String id,
        @NotNull String content,
        @Nullable Map<String, String> metadata
    ) {}

    /**
     * Index result
     */
    public record IndexResult(
        @NotNull String id,
        boolean success,
        long durationMs,
        int vectorDimension,
        @Nullable String error
    ) {}
}
