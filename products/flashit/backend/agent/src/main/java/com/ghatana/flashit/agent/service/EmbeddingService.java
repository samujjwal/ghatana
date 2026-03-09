package com.ghatana.flashit.agent.service;

import com.ghatana.flashit.agent.config.AgentConfig;
import com.ghatana.flashit.agent.dto.*;
import com.openai.client.OpenAIClient;
import com.openai.models.embeddings.EmbeddingCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Embedding generation and semantic search service.
 *
 * <p>Uses OpenAI embeddings API to generate vectors for moments and perform
 * cosine-similarity-based semantic search across stored embeddings.
 *
 * @doc.type class
 * @doc.purpose Generates text embeddings and performs semantic search
 * @doc.layer product
 * @doc.pattern Service
 */
public class EmbeddingService {
    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final OpenAIClient client;
    private final String embeddingModel;

    // In-memory embedding store (production would use pgvector or similar)
    private final Map<String, StoredEmbedding> embeddingStore = new ConcurrentHashMap<>();

    public EmbeddingService(OpenAIClient client, AgentConfig config) {
        this.client = client;
        this.embeddingModel = config.getEmbeddingModel();
    }

    /**
     * Generate an embedding vector for the given text.
     *
     * @param request embedding request with text and metadata
     * @return embedding response with vector and metadata
     */
    public EmbeddingResponse generateEmbedding(EmbeddingRequest request) {
        long start = System.currentTimeMillis();
        log.info("Generating embedding for momentId={}, user={}", request.momentId(), request.userId());

        try {
            var response = client.embeddings().create(
                    EmbeddingCreateParams.builder()
                            .model(embeddingModel)
                            .input(request.text())
                            .build()
            );

            var embeddingData = response.data().getFirst();
            double[] vector = embeddingData.embedding().stream()
                    .mapToDouble(Float::doubleValue)
                    .toArray();

            int tokenCount = (int) response.usage().totalTokens();
            long elapsed = System.currentTimeMillis() - start;
            boolean stored = false;

            // Store if requested
            if (request.store() && request.momentId() != null) {
                embeddingStore.put(request.momentId(), new StoredEmbedding(
                        request.momentId(), request.userId(), request.contentType(),
                        vector, request.text()));
                stored = true;
                log.debug("Stored embedding for momentId={}", request.momentId());
            }

            log.info("Embedding generated in {}ms, dims={}, tokens={}",
                    elapsed, vector.length, tokenCount);

            return new EmbeddingResponse(request.momentId(), vector, vector.length,
                    tokenCount, embeddingModel, elapsed, stored);

        } catch (Exception e) {
            log.error("Embedding generation failed for momentId={}", request.momentId(), e);
            long elapsed = System.currentTimeMillis() - start;
            return new EmbeddingResponse(request.momentId(), new double[0], 0, 0,
                    embeddingModel, elapsed, false);
        }
    }

    /**
     * Generate embeddings for multiple texts in batch.
     *
     * @param requests list of embedding requests
     * @return list of embedding responses
     */
    public List<EmbeddingResponse> generateBatchEmbeddings(List<EmbeddingRequest> requests) {
        log.info("Generating batch embeddings, count={}", requests.size());
        return requests.stream()
                .map(this::generateEmbedding)
                .toList();
    }

    /**
     * Perform semantic search across stored embeddings.
     *
     * @param request search request with query and filters
     * @return search response with ranked results
     */
    public SemanticSearchResponse semanticSearch(SemanticSearchRequest request) {
        long start = System.currentTimeMillis();
        log.info("Semantic search for user={}, query='{}', spheres={}",
                request.userId(), request.query(), request.sphereIds());

        // Generate query embedding
        EmbeddingResponse queryEmb = generateEmbedding(new EmbeddingRequest(
                null, request.query(), request.userId(), "query", false));

        if (queryEmb.embedding().length == 0) {
            long elapsed = System.currentTimeMillis() - start;
            return new SemanticSearchResponse(List.of(), 0, request.query(), elapsed);
        }

        // Search stored embeddings
        List<SearchResult> results = embeddingStore.values().stream()
                .filter(stored -> request.userId().equals(stored.userId()))
                .filter(stored -> request.sphereIds() == null || request.sphereIds().isEmpty()
                        || request.sphereIds().contains(stored.contentType()))
                .map(stored -> {
                    double similarity = cosineSimilarity(queryEmb.embedding(), stored.vector());
                    return new SearchResult(stored.momentId(), stored.text(), stored.contentType(), similarity);
                })
                .filter(r -> r.similarity() >= request.similarityThreshold())
                .sorted(Comparator.comparingDouble(SearchResult::similarity).reversed())
                .limit(request.limit())
                .toList();

        long elapsed = System.currentTimeMillis() - start;
        log.info("Semantic search completed in {}ms, results={}", elapsed, results.size());
        return new SemanticSearchResponse(results, results.size(), request.query(), elapsed);
    }

    private static double cosineSimilarity(double[] a, double[] b) {
        if (a.length != b.length || a.length == 0) return 0.0;

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denominator = Math.sqrt(normA) * Math.sqrt(normB);
        return denominator == 0.0 ? 0.0 : dotProduct / denominator;
    }

    private record StoredEmbedding(String momentId, String userId, String contentType,
                                   double[] vector, String text) {
    }
}
