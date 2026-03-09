package com.ghatana.datacloud.client;

import com.ghatana.datacloud.EventRecord;
import com.ghatana.datacloud.RecordQuery;
import com.ghatana.datacloud.spi.StoragePlugin;
import io.activej.promise.Promise;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Default implementation of ContextGateway with semantic search and intelligent selection.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides controlled LLM access to Data-Cloud context with:
 * <ul>
 * <li>Intelligent context selection based on relevance</li>
 * <li>Token budget management</li>
 * <li>Freshness and confidence filtering</li>
 * <li>Multiple selection strategies</li>
 * <li>Tenant isolation enforcement</li>
 * </ul>
 *
 * <p>
 * <b>Selection Algorithm</b><br>
 * The HYBRID strategy (default) combines:
 * <ol>
 * <li>Confidence-based filtering (≥ minConfidence)</li>
 * <li>Determinism-based filtering (≥ minDeterminism)</li>
 * <li>Freshness check (not expired)</li>
 * <li>Type-based inclusion/exclusion</li>
 * <li>Semantic similarity ranking (if query provided)</li>
 * <li>Token budget enforcement (stop when budget exceeded)</li>
 * </ol>
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * StoragePlugin<EventRecord> plugin = pluginRegistry.getPlugin("postgresql");
 * ContextGateway gateway = new DefaultContextGateway(plugin);
 *
 * // Select context for LLM
 * ContextBundle bundle = gateway.selectContext(
 *     ContextRequest.builder()
 *         .tenantId("tenant-123")
 *         .query("Show me recent login patterns")
 *         .maxTokens(4000)
 *         .minConfidence(0.8)
 *         .includeTypes(List.of(ContextType.FACT, ContextType.AGGREGATION))
 *         .build()
 * ).getResult();
 *
 * // Send to LLM
 * String prompt = buildPrompt(userQuery, bundle);
 * }</pre>
 *
 * @see ContextDocument
 * @see ContextGateway
 * @doc.type class
 * @doc.purpose Default context gateway implementation
 * @doc.layer core
 * @doc.pattern Gateway Implementation
 */
@Slf4j
public class DefaultContextGateway implements ContextGateway {

    private static final String COLLECTION_NAME = "context-documents";
    private static final int DEFAULT_TOKEN_ESTIMATE = 250; // Average tokens per document

    private final StoragePlugin<EventRecord> storagePlugin;
    private final EmbeddingProvider embeddingProvider;

    /**
     * Pluggable embedding provider for semantic similarity scoring.
     *
     * <p>When no embedding provider is configured, the gateway falls back
     * to keyword-based TF-IDF scoring as a deterministic alternative.</p>
     *
     * @doc.type interface
     * @doc.purpose Embedding generation for semantic ranking
     * @doc.layer core
     * @doc.pattern Strategy
     */
    public interface EmbeddingProvider {
        /**
         * Generate embedding vector for the given text.
         *
         * @param text input text
         * @return float array representing the embedding vector
         */
        float[] embed(String text);

        /**
         * Get the dimensionality of embeddings produced by this provider.
         *
         * @return embedding vector dimension
         */
        int dimensions();
    }

    /**
     * Create gateway with storage plugin only (keyword fallback for ranking).
     *
     * @param storagePlugin storage plugin to use
     */
    public DefaultContextGateway(StoragePlugin<EventRecord> storagePlugin) {
        this(storagePlugin, null);
    }

    /**
     * Create gateway with storage plugin and embedding provider for full
     * semantic similarity ranking.
     *
     * @param storagePlugin     storage plugin to use
     * @param embeddingProvider embedding provider for semantic scoring (nullable)
     */
    public DefaultContextGateway(StoragePlugin<EventRecord> storagePlugin,
                                 EmbeddingProvider embeddingProvider) {
        this.storagePlugin = Objects.requireNonNull(storagePlugin, "storagePlugin cannot be null");
        this.embeddingProvider = embeddingProvider;
        if (embeddingProvider != null) {
            log.info("DefaultContextGateway initialized with embedding provider (dim={})",
                    embeddingProvider.dimensions());
        } else {
            log.info("DefaultContextGateway initialized without embedding provider; using TF-IDF fallback");
        }
    }

    /**
     * Creates a gateway with the given storage plugin.
     * Ensures the context-documents collection exists.
     *
     * @param storagePlugin Storage plugin to use
     * @return Promise with the created gateway
     */
    public static Promise<DefaultContextGateway> create(StoragePlugin<EventRecord> storagePlugin) {
        DefaultContextGateway gateway = new DefaultContextGateway(storagePlugin);
        return gateway.ensureCollectionExists()
                .map(v -> gateway);
    }

    /**
     * Ensures the context-documents collection exists.
     */
    private Promise<Void> ensureCollectionExists() {
        return storagePlugin.getCollection("_system", COLLECTION_NAME)
                .then(maybeCollection -> {
                    if (maybeCollection != null && maybeCollection.isPresent()) {
                        return Promise.of(null);
                    }

                    // Create collection
                    com.ghatana.datacloud.Collection collection = com.ghatana.datacloud.Collection.builder()
                            .tenantId("_system")
                            .name(COLLECTION_NAME)
                            .recordType(com.ghatana.datacloud.RecordType.EVENT)
                            .description("Context documents for LLM gateway")
                            .build();

                    return storagePlugin.createCollection(collection)
                            .then(c -> Promise.of(null));
                });
    }

    @Override
    public Promise<ContextBundle> selectContext(ContextRequest request) {
        log.debug("Selecting context for query: {} (tenant: {})",
                request.query(), request.tenantId());

        // Build filters
        List<RecordQuery.FilterCondition> filters = new ArrayList<>();

        // Add type filters
        if (request.includeTypes() != null && !request.includeTypes().isEmpty()) {
            for (ContextDocument.ContextType type : request.includeTypes()) {
                filters.add(RecordQuery.FilterCondition.builder()
                        .field("contextType")
                        .operator(RecordQuery.Operator.EQUALS)
                        .value(type.name())
                        .build());
            }
        }

        // Build query for candidate documents
        RecordQuery query = RecordQuery.builder()
                .tenantId(request.tenantId())
                .collectionName(COLLECTION_NAME)
                .filters(filters)
                .limit(100) // Get more candidates than needed
                .build();

        // Execute query
        return storagePlugin.query(query)
                .then(result -> {
                    // Convert to ContextDocuments
                    List<ContextDocument> candidates = result.records().stream()
                            .map(this::fromEventRecord)
                            .collect(Collectors.toList());

                    // Apply selection strategy
                    return Promise.of(selectFromCandidates(candidates, request));
                });
    }

    /**
     * Selects the best context documents from candidates.
     */
    private ContextBundle selectFromCandidates(List<ContextDocument> candidates, ContextRequest request) {
        SelectionStrategy strategy = SelectionStrategy.HYBRID; // Default strategy

        // Step 1: Filter by confidence
        List<ContextDocument> filtered = candidates.stream()
                .filter(doc -> doc.getConfidence() >= request.minConfidence())
                .collect(Collectors.toList());

        // Step 2: Filter by determinism
        filtered = filtered.stream()
                .filter(doc -> isDeterminismSufficient(doc.getDeterminism(), request.minDeterminism()))
                .collect(Collectors.toList());

        // Step 3: Filter by freshness
        filtered = filtered.stream()
                .filter(ContextDocument::isFresh)
                .collect(Collectors.toList());

        // Step 4: Filter by type inclusion (post-query filtering to ensure accuracy)
        if (request.includeTypes() != null && !request.includeTypes().isEmpty()) {
            filtered = filtered.stream()
                    .filter(doc -> request.includeTypes().contains(doc.getContextType()))
                    .collect(Collectors.toList());
        }

        // Step 5: Filter by type exclusion
        if (request.excludeTypes() != null && !request.excludeTypes().isEmpty()) {
            filtered = filtered.stream()
                    .filter(doc -> !request.excludeTypes().contains(doc.getContextType()))
                    .collect(Collectors.toList());
        }

        // Step 6: Rank documents
        List<ContextDocument> ranked = rankDocuments(filtered, request, strategy);

        // Step 7: Enforce token budget
        List<ContextDocument> selected = enforceTokenBudget(ranked, request.maxTokens());

        // Build metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("candidateCount", candidates.size());
        metadata.put("filteredCount", filtered.size());
        metadata.put("selectedCount", selected.size());
        metadata.put("selectionStrategy", strategy.name());

        int totalTokens = selected.stream()
                .mapToInt(doc -> doc.getTokenCount() != null ? doc.getTokenCount() : DEFAULT_TOKEN_ESTIMATE)
                .sum();

        return new ContextBundle(selected, totalTokens, metadata, strategy);
    }

    /**
     * Ranks documents based on selection strategy.
     */
    private List<ContextDocument> rankDocuments(List<ContextDocument> documents,
                                                 ContextRequest request,
                                                 SelectionStrategy strategy) {
        return switch (strategy) {
            case SEMANTIC -> rankBySemantic(documents, request.query());
            case RECENCY -> rankByRecency(documents);
            case CONFIDENCE -> rankByConfidence(documents);
            case HYBRID -> rankByHybrid(documents, request.query());
            default -> documents;
        };
    }

    /**
     * Ranks by semantic similarity using embeddings with TF-IDF fallback.
     *
     * <p>When an {@link EmbeddingProvider} is configured, computes cosine similarity
     * between the query embedding and each document's content embedding. Otherwise,
     * falls back to a deterministic TF-IDF style keyword scoring.</p>
     */
    private List<ContextDocument> rankBySemantic(List<ContextDocument> documents, String query) {
        if (query == null || query.isEmpty()) {
            return documents;
        }

        if (embeddingProvider != null) {
            return rankByEmbeddingSimilarity(documents, query);
        }

        // Deterministic fallback: TF-IDF style scoring
        return rankByTfIdf(documents, query);
    }

    /**
     * Ranks documents by cosine similarity of embedding vectors.
     */
    private List<ContextDocument> rankByEmbeddingSimilarity(List<ContextDocument> documents, String query) {
        float[] queryEmbedding;
        try {
            queryEmbedding = embeddingProvider.embed(query);
        } catch (Exception e) {
            log.warn("Failed to generate query embedding, falling back to TF-IDF: {}", e.getMessage());
            return rankByTfIdf(documents, query);
        }

        // Score each document by cosine similarity
        record ScoredDoc(ContextDocument doc, double score) {}

        List<ScoredDoc> scored = documents.stream()
                .map(doc -> {
                    double score;
                    try {
                        float[] docEmbedding = embeddingProvider.embed(
                                doc.getContent() != null ? doc.getContent() : "");
                        score = cosineSimilarity(queryEmbedding, docEmbedding);
                    } catch (Exception e) {
                        log.debug("Failed to embed document {}, using 0.0 score", doc.getContextId());
                        score = 0.0;
                    }
                    return new ScoredDoc(doc, score);
                })
                .sorted(Comparator.comparingDouble(ScoredDoc::score).reversed())
                .toList();

        return scored.stream().map(ScoredDoc::doc).collect(Collectors.toList());
    }

    /**
     * Deterministic TF-IDF style keyword scoring fallback.
     *
     * <p>Computes a normalized term-frequency based score. Each query term's
     * match contribution is weighted by inverse frequency (rarer terms score higher).
     * This provides a reasonable relevance ranking without requiring embeddings.</p>
     */
    private List<ContextDocument> rankByTfIdf(List<ContextDocument> documents, String query) {
        String[] queryTerms = query.toLowerCase().split("\\s+");

        // Compute inverse document frequency for each term
        Map<String, Double> idf = new HashMap<>();
        for (String term : queryTerms) {
            long docCount = documents.stream()
                    .filter(d -> d.getContent() != null
                            && d.getContent().toLowerCase().contains(term))
                    .count();
            idf.put(term, docCount > 0
                    ? Math.log((double) documents.size() / docCount) + 1.0
                    : 0.0);
        }

        return documents.stream()
                .sorted((d1, d2) -> {
                    double score1 = calculateTfIdfScore(d1.getContent(), queryTerms, idf);
                    double score2 = calculateTfIdfScore(d2.getContent(), queryTerms, idf);
                    return Double.compare(score2, score1);
                })
                .collect(Collectors.toList());
    }

    /**
     * Calculate TF-IDF score for a document against query terms.
     */
    private double calculateTfIdfScore(String content, String[] queryTerms, Map<String, Double> idf) {
        if (content == null || content.isEmpty()) {
            return 0.0;
        }
        String contentLower = content.toLowerCase();
        String[] contentWords = contentLower.split("\\s+");
        int totalWords = Math.max(contentWords.length, 1);

        double score = 0.0;
        for (String term : queryTerms) {
            // Term frequency: count occurrences / total words
            long tf = Arrays.stream(contentWords)
                    .filter(w -> w.contains(term))
                    .count();
            double normalizedTf = (double) tf / totalWords;
            score += normalizedTf * idf.getOrDefault(term, 0.0);
        }
        return score;
    }

    /**
     * Compute cosine similarity between two vectors.
     *
     * @return similarity in [-1, 1], where 1 means identical direction
     */
    private static double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            return 0.0;
        }
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

    /**
     * Ranks by recency (newest first).
     */
    private List<ContextDocument> rankByRecency(List<ContextDocument> documents) {
        return documents.stream()
                .sorted(Comparator.comparing(ContextDocument::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Ranks by confidence (highest first).
     */
    private List<ContextDocument> rankByConfidence(List<ContextDocument> documents) {
        return documents.stream()
                .sorted(Comparator.comparing(ContextDocument::getConfidence).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Hybrid ranking: confidence * recency * semantic.
     */
    private List<ContextDocument> rankByHybrid(List<ContextDocument> documents, String query) {
        // Combine multiple factors
        return documents.stream()
                .sorted((d1, d2) -> {
                    double score1 = calculateHybridScore(d1, query);
                    double score2 = calculateHybridScore(d2, query);
                    return Double.compare(score2, score1); // Higher score first
                })
                .collect(Collectors.toList());
    }

    /**
     * Calculates hybrid score combining confidence, recency, and relevance.
     *
     * <p>Uses embedding-based similarity when available, otherwise TF-IDF.</p>
     */
    private double calculateHybridScore(ContextDocument doc, String query) {
        // Confidence weight
        double confidenceScore = doc.getConfidence();

        // Recency weight (exponential decay)
        long ageSeconds = Duration.between(doc.getCreatedAt(), Instant.now()).getSeconds();
        double recencyScore = Math.exp(-ageSeconds / (86400.0 * 7)); // 7-day half-life

        // Semantic weight
        double semanticScore;
        if (query != null && !query.isEmpty()) {
            if (embeddingProvider != null) {
                try {
                    float[] queryVec = embeddingProvider.embed(query);
                    float[] docVec = embeddingProvider.embed(
                            doc.getContent() != null ? doc.getContent() : "");
                    // Map cosine similarity from [-1,1] to [0,1]
                    semanticScore = (cosineSimilarity(queryVec, docVec) + 1.0) / 2.0;
                } catch (Exception e) {
                    // Fallback to keyword scoring
                    String[] terms = query.toLowerCase().split("\\s+");
                    semanticScore = calculateTfIdfScore(doc.getContent(), terms, Map.of()) / 10.0;
                }
            } else {
                String[] terms = query.toLowerCase().split("\\s+");
                semanticScore = calculateTfIdfScore(doc.getContent(), terms, Map.of()) / 10.0;
            }
        } else {
            semanticScore = 0.5;
        }

        // Combined score (weighted average)
        return 0.5 * confidenceScore + 0.3 * recencyScore + 0.2 * semanticScore;
    }

    /**
     * Enforces token budget by selecting documents until budget is reached.
     */
    private List<ContextDocument> enforceTokenBudget(List<ContextDocument> ranked, int maxTokens) {
        List<ContextDocument> selected = new ArrayList<>();
        int currentTokens = 0;

        for (ContextDocument doc : ranked) {
            int docTokens = doc.getTokenCount() != null ? doc.getTokenCount() : DEFAULT_TOKEN_ESTIMATE;

            if (currentTokens + docTokens <= maxTokens) {
                selected.add(doc);
                currentTokens += docTokens;
            } else {
                break; // Budget exceeded
            }
        }

        return selected;
    }

    /**
     * Checks if determinism level is sufficient.
     */
    private boolean isDeterminismSufficient(
            com.ghatana.datacloud.spi.ai.PredictionCapability.DeterminismLevel actual,
            com.ghatana.datacloud.spi.ai.PredictionCapability.DeterminismLevel required) {

        if (actual == null || required == null) return true;

        return switch (required) {
            case HIGH -> actual == com.ghatana.datacloud.spi.ai.PredictionCapability.DeterminismLevel.HIGH;
            case MEDIUM -> actual == com.ghatana.datacloud.spi.ai.PredictionCapability.DeterminismLevel.HIGH ||
                          actual == com.ghatana.datacloud.spi.ai.PredictionCapability.DeterminismLevel.MEDIUM;
            case LOW -> true; // All levels acceptable
        };
    }

    @Override
    public Promise<ContextDocument> store(ContextDocument document) {
        EventRecord record = toEventRecord(document);
        return storagePlugin.insert(record)
                .then(inserted -> Promise.of(document));
    }

    @Override
    public Promise<List<ContextDocument>> semanticSearch(String tenantId, String query, int limit) {
        // For now, use the selectContext method with appropriate request
        ContextRequest request = ContextRequest.builder()
                .tenantId(tenantId)
                .query(query)
                .maxTokens(Integer.MAX_VALUE)
                .minConfidence(0.0)
                .build();

        return selectContext(request)
                .map(bundle -> bundle.documents().stream()
                        .limit(limit)
                        .collect(Collectors.toList()));
    }

    @Override
    public Promise<Long> purgeExpiredContext(String tenantId) {
        Instant now = Instant.now();

        log.info("Purging expired context for tenant {}", tenantId);

        // Build filters
        List<RecordQuery.FilterCondition> filters = List.of(
                RecordQuery.FilterCondition.builder()
                        .field("expiresAt")
                        .operator(RecordQuery.Operator.LESS_THAN)
                        .value(now)
                        .build()
        );

        // Query expired documents
        RecordQuery query = RecordQuery.builder()
                .tenantId(tenantId)
                .collectionName(COLLECTION_NAME)
                .filters(filters)
                .build();

        return storagePlugin.query(query)
                .then(result -> {
                    List<UUID> ids = result.records().stream()
                            .map(EventRecord::getId)
                            .collect(Collectors.toList());

                    if (ids.isEmpty()) {
                        return Promise.of(0L);
                    }

                    return storagePlugin.deleteBatch(tenantId, COLLECTION_NAME, ids)
                            .then(batchResult -> Promise.of((long) batchResult.successCount()));
                });
    }

    @Override
    public Promise<ContextStatistics> getStatistics(String tenantId, Duration window) {
        Instant startTime = Instant.now().minus(window);

        // Query documents in window
        RecordQuery query = RecordQuery.builder()
                .tenantId(tenantId)
                .collectionName(COLLECTION_NAME)
                .startTime(startTime)
                .endTime(Instant.now())
                .limit(10000)
                .build();

        return storagePlugin.query(query)
                .then(result -> {
                    List<ContextDocument> documents = result.records().stream()
                            .map(this::fromEventRecord)
                            .collect(Collectors.toList());

                    // Calculate statistics
                    long totalDocs = documents.size();
                    long freshDocs = documents.stream().filter(ContextDocument::isFresh).count();
                    long staleDocs = totalDocs - freshDocs;

                    Map<ContextDocument.ContextType, Long> countsByType = documents.stream()
                            .collect(Collectors.groupingBy(
                                    ContextDocument::getContextType,
                                    Collectors.counting()));

                    double avgConfidence = documents.stream()
                            .mapToDouble(ContextDocument::getConfidence)
                            .average()
                            .orElse(0.0);

                    int avgTokenCount = (int) documents.stream()
                            .mapToInt(doc -> doc.getTokenCount() != null ? doc.getTokenCount() : DEFAULT_TOKEN_ESTIMATE)
                            .average()
                            .orElse(0.0);

                    return Promise.of(new ContextStatistics(
                            totalDocs,
                            freshDocs,
                            staleDocs,
                            countsByType,
                            avgConfidence,
                            avgTokenCount,
                            0L, // totalQueries - would need separate tracking
                            0.0  // averageContextHitRate - would need separate tracking
                    ));
                });
    }

    /**
     * Converts ContextDocument to EventRecord for storage.
     */
    private EventRecord toEventRecord(ContextDocument document) {
        return EventRecord.builder()
                .id(document.getContextId())
                .tenantId(document.getTenantId())
                .collectionName(COLLECTION_NAME)
                .streamName("context-documents")
                .partitionId(0)
                .occurrenceTime(document.getCreatedAt())
                .data(document.toMap())
                .build();
    }

    /**
     * Converts EventRecord back to ContextDocument.
     */
    @SuppressWarnings("unchecked")
    private ContextDocument fromEventRecord(EventRecord record) {
        Map<String, Object> data = record.getData();

        return ContextDocument.builder()
                .contextId(record.getId())
                .createdAt(record.getOccurrenceTime())
                .tenantId(record.getTenantId())
                .contextType(ContextDocument.ContextType.valueOf(
                        (String) data.getOrDefault("contextType", "CUSTOM")))
                .source(extractSource(data))
                .content((String) data.get("content"))
                .structuredData((Map<String, Object>) data.get("structuredData"))
                .confidence(((Number) data.getOrDefault("confidence", 0.0)).doubleValue())
                .determinism(com.ghatana.datacloud.spi.ai.PredictionCapability.DeterminismLevel.valueOf(
                        (String) data.getOrDefault("determinism", "MEDIUM")))
                .ttl(Duration.parse((String) data.getOrDefault("ttl", "P30D")))
                .expiresAt(Instant.parse((String) data.get("expiresAt")))
                .tags((Map<String, String>) data.get("tags"))
                .tokenCount(((Number) data.getOrDefault("tokenCount", DEFAULT_TOKEN_ESTIMATE)).intValue())
                .version(((Number) data.getOrDefault("version", 1)).intValue())
                .build();
    }

    /**
     * Extracts source information from payload.
     */
    @SuppressWarnings("unchecked")
    private ContextDocument.ContextSource extractSource(Map<String, Object> payload) {
        Map<String, Object> sourceMap = (Map<String, Object>) payload.get("source");
        if (sourceMap == null) {
            return ContextDocument.ContextSource.builder().build();
        }

        return ContextDocument.ContextSource.builder()
                .collection((String) sourceMap.get("collection"))
                .plugin((String) sourceMap.get("plugin"))
                .queryId((String) sourceMap.get("queryId"))
                .operation((String) sourceMap.get("operation"))
                .actor((String) sourceMap.get("actor"))
                .provenance((Map<String, String>) sourceMap.get("provenance"))
                .build();
    }
}

