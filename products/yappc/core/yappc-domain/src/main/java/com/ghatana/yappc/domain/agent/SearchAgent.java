package com.ghatana.products.yappc.domain.agent;

import com.ghatana.ai.vectorstore.VectorSearchResult;
import com.ghatana.ai.vectorstore.VectorStore;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Search Agent - provides semantic and hybrid search capabilities.
 * <p>
 * Combines vector similarity search with traditional text search
 * for comprehensive results.
 *
 * @doc.type class
 * @doc.purpose AI-powered semantic search
 * @doc.layer product
 * @doc.pattern Strategy
 */
public class SearchAgent extends AbstractAIAgent<SearchAgent.SearchInput, SearchAgent.SearchOutput> {

    private static final Logger LOG = LoggerFactory.getLogger(SearchAgent.class);

        private static final String VERSION = "1.0.0";
        private static final String DESCRIPTION = "Semantic and hybrid search across items, workflows, and knowledge base";
        private static final List<String> CAPABILITIES = List.of(
            "semantic-search",
            "vector-search",
            "hybrid-search",
            "rag"
        );
        private static final List<String> SUPPORTED_MODELS = List.of(
            "text-embedding-3-small",
            "text-embedding-3-large"
        );

    private final VectorStore vectorStore;
    private final EmbeddingService embeddingService;
    private final TextSearchService textSearchService;

    public SearchAgent(
            @NotNull MetricsCollector metricsCollector,
            @NotNull VectorStore vectorStore,
            @NotNull EmbeddingService embeddingService,
            @NotNull TextSearchService textSearchService
    ) {
        super(
                AgentName.SEARCH_AGENT,
                VERSION,
                DESCRIPTION,
                CAPABILITIES,
                SUPPORTED_MODELS,
                metricsCollector
        );
        this.vectorStore = vectorStore;
        this.embeddingService = embeddingService;
        this.textSearchService = textSearchService;
    }

    @Override
    public void validateInput(@NotNull SearchInput input) {
        if (input.query() == null || input.query().isBlank()) {
            throw new IllegalArgumentException("query is required");
        }
    }

    @Override
    protected Promise<Map<String, AgentHealth.DependencyStatus>> doHealthCheck() {
        return Promise.of(Map.of(
                "vectorStore", AgentHealth.DependencyStatus.HEALTHY,
                "embeddingService", AgentHealth.DependencyStatus.HEALTHY,
                "textSearchService", AgentHealth.DependencyStatus.HEALTHY
        ));
    }

    @Override
    protected @NotNull Promise<ProcessResult<SearchOutput>> processRequest(
            @NotNull SearchInput input,
            @NotNull AIAgentContext context
    ) {
        LOG.debug("Searching for: {} with mode {}", input.query(), input.searchMode());
        long startTime = System.currentTimeMillis();

        return switch (input.searchMode()) {
            case SEMANTIC -> semanticSearch(input, context, startTime);
            case TEXT -> textSearch(input, context, startTime);
            case HYBRID -> hybridSearch(input, context, startTime);
        };
    }

    private Promise<ProcessResult<SearchOutput>> semanticSearch(
            SearchInput input,
            AIAgentContext context,
            long startTime
    ) {
        // Generate embedding for query
        return embeddingService.embed(input.query())
                .then(queryEmbedding -> {
                    // Search vector store (core abstraction does not model collections; treat collection as metadata)
                    Map<String, String> filterMetadata = new HashMap<>();
                    if (input.workspaceId() != null) {
                        filterMetadata.put("workspaceId", input.workspaceId());
                    }
                    if (input.collections() != null && !input.collections().isEmpty()) {
                        filterMetadata.put("collection", input.collections().get(0));
                    }
                    if (input.filters() != null) {
                        for (Map.Entry<String, Object> entry : input.filters().entrySet()) {
                            if (entry.getValue() != null) {
                                filterMetadata.put(entry.getKey(), entry.getValue().toString());
                            }
                        }
                    }

                    int effectiveLimit = Math.max(0, input.limit() + Math.max(0, input.offset()));
                    double threshold = 0.0;
                    return filterMetadata.isEmpty()
                            ? vectorStore.search(queryEmbedding, effectiveLimit, threshold)
                            : vectorStore.search(queryEmbedding, effectiveLimit, threshold, filterMetadata);
                })
                .map(results -> {
                    List<SearchResult> searchResults = new ArrayList<>();

                    List<VectorSearchResult> pagedResults = results;
                    int offset = Math.max(0, input.offset());
                    if (offset > 0 && offset < results.size()) {
                        pagedResults = results.subList(offset, results.size());
                    } else if (offset >= results.size()) {
                        pagedResults = List.of();
                    }

                    for (VectorSearchResult result : pagedResults) {
                        Map<String, Object> metadata = new HashMap<>();
                        metadata.putAll(result.getMetadata());
                        searchResults.add(new SearchResult(
                                result.getId(),
                                result.getMetadata().getOrDefault("title", ""),
                                result.getContent(),
                                result.getSimilarity(),
                                result.getMetadata().getOrDefault("type", "item"),
                                result.getMetadata().getOrDefault("collection", "items"),
                            metadata,
                                null // No highlights for semantic search
                        ));
                    }

                    return buildResult(searchResults, SearchMode.SEMANTIC, startTime, searchResults.size());
                });
    }

    private Promise<ProcessResult<SearchOutput>> textSearch(
            SearchInput input,
            AIAgentContext context,
            long startTime
    ) {
        return textSearchService.search(
                input.query(),
                input.workspaceId(),
                input.collections(),
                input.filters(),
                input.limit()
        ).map(results -> {
            List<SearchResult> searchResults = new ArrayList<>();

            for (TextSearchResult result : results) {
                searchResults.add(new SearchResult(
                        result.id(),
                        result.title(),
                        result.snippet(),
                        result.score(),
                        result.type(),
                        result.collection(),
                        result.metadata(),
                        result.highlights()
                ));
            }

            return buildResult(searchResults, SearchMode.TEXT, startTime, results.size());
        });
    }

    private Promise<ProcessResult<SearchOutput>> hybridSearch(
            SearchInput input,
            AIAgentContext context,
            long startTime
    ) {
        // Run both searches in parallel and combine results
        Promise<ProcessResult<SearchOutput>> semanticPromise = semanticSearch(input, context, startTime);
        Promise<ProcessResult<SearchOutput>> textPromise = textSearch(input, context, startTime);

        return semanticPromise.combine(textPromise, (semantic, text) -> {
            Map<String, SearchResult> combinedResults = new LinkedHashMap<>();

            // Weight: 60% semantic, 40% text
            double semanticWeight = 0.6;
            double textWeight = 0.4;

            // Add semantic results
            for (SearchResult result : semantic.data().results()) {
                combinedResults.put(result.id(), new SearchResult(
                        result.id(),
                        result.title(),
                        result.snippet(),
                        result.score() * semanticWeight,
                        result.type(),
                        result.collection(),
                        result.metadata(),
                        result.highlights()
                ));
            }

            // Merge text results
            for (SearchResult result : text.data().results()) {
                if (combinedResults.containsKey(result.id())) {
                    // Combine scores
                    SearchResult existing = combinedResults.get(result.id());
                    combinedResults.put(result.id(), new SearchResult(
                            result.id(),
                            result.title(),
                            result.snippet(),
                            existing.score() + (result.score() * textWeight),
                            result.type(),
                            result.collection(),
                            result.metadata(),
                            result.highlights() // Use text highlights
                    ));
                } else {
                    combinedResults.put(result.id(), new SearchResult(
                            result.id(),
                            result.title(),
                            result.snippet(),
                            result.score() * textWeight,
                            result.type(),
                            result.collection(),
                            result.metadata(),
                            result.highlights()
                    ));
                }
            }

            // Sort by combined score and limit
            List<SearchResult> sortedResults = combinedResults.values().stream()
                    .sorted((a, b) -> Double.compare(b.score(), a.score()))
                    .limit(input.limit())
                    .toList();

            return buildResult(sortedResults, SearchMode.HYBRID, startTime, combinedResults.size());
        });
    }

    private ProcessResult<SearchOutput> buildResult(
            List<SearchResult> results,
            SearchMode mode,
            long startTime,
            int totalCandidates
    ) {
        // Generate facets from results
        Map<String, List<Facet>> facets = generateFacets(results);

        SearchOutput output = new SearchOutput(
                results,
                results.size(),
                totalCandidates,
                facets,
                new SearchMetadata(
                        mode,
                        System.currentTimeMillis() - startTime,
                        mode == SearchMode.SEMANTIC || mode == SearchMode.HYBRID
                )
        );

        return ProcessResult.of(output);
    }

    private Map<String, List<Facet>> generateFacets(List<SearchResult> results) {
        Map<String, List<Facet>> facets = new HashMap<>();

        // Type facets
        Map<String, Integer> typeCounts = new HashMap<>();
        Map<String, Integer> collectionCounts = new HashMap<>();

        for (SearchResult result : results) {
            typeCounts.merge(result.type(), 1, Integer::sum);
            collectionCounts.merge(result.collection(), 1, Integer::sum);
        }

        facets.put("type", typeCounts.entrySet().stream()
                .map(e -> new Facet(e.getKey(), e.getValue()))
                .toList());

        facets.put("collection", collectionCounts.entrySet().stream()
                .map(e -> new Facet(e.getKey(), e.getValue()))
                .toList());

        return facets;
    }

    // Input/Output types

    /**
     * Search input.
     */
    public record SearchInput(
            @NotNull String query,
            @Nullable String workspaceId,
            @Nullable List<String> collections,
            @Nullable Map<String, Object> filters,
            @NotNull SearchMode searchMode,
            int limit,
            int offset
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String query;
            private String workspaceId;
            private List<String> collections;
            private Map<String, Object> filters;
            private SearchMode searchMode = SearchMode.HYBRID;
            private int limit = 20;
            private int offset = 0;

            public Builder query(String query) {
                this.query = query;
                return this;
            }

            public Builder workspaceId(String workspaceId) {
                this.workspaceId = workspaceId;
                return this;
            }

            public Builder collections(List<String> collections) {
                this.collections = collections;
                return this;
            }

            public Builder filters(Map<String, Object> filters) {
                this.filters = filters;
                return this;
            }

            public Builder searchMode(SearchMode searchMode) {
                this.searchMode = searchMode;
                return this;
            }

            public Builder limit(int limit) {
                this.limit = limit;
                return this;
            }

            public Builder offset(int offset) {
                this.offset = offset;
                return this;
            }

            public SearchInput build() {
                if (query == null || query.isBlank()) {
                    throw new IllegalStateException("query is required");
                }
                return new SearchInput(query, workspaceId, collections, filters, searchMode, limit, offset);
            }
        }
    }

    /**
     * Search output.
     */
    public record SearchOutput(
            @NotNull List<SearchResult> results,
            int count,
            int totalCandidates,
            @NotNull Map<String, List<Facet>> facets,
            @NotNull SearchMetadata metadata
    ) {}

    /**
     * A single search result.
     */
    public record SearchResult(
            @NotNull String id,
            @NotNull String title,
            @Nullable String snippet,
            double score,
            @NotNull String type,
            @NotNull String collection,
            @Nullable Map<String, Object> metadata,
            @Nullable List<String> highlights
    ) {}

    /**
     * Search mode.
     */
    public enum SearchMode {
        SEMANTIC,   // Vector similarity only
        TEXT,       // Full-text search only
        HYBRID      // Combined semantic + text
    }

    /**
     * Facet for filtering.
     */
    public record Facet(
            @NotNull String value,
            int count
    ) {}

    /**
     * Search metadata.
     */
    public record SearchMetadata(
            @NotNull SearchMode mode,
            long searchTimeMs,
            boolean usedEmbeddings
    ) {}

    // Service interfaces

    public interface EmbeddingService {
        Promise<float[]> embed(String text);
        Promise<List<float[]>> embedBatch(List<String> texts);
    }

    public interface TextSearchService {
        Promise<List<TextSearchResult>> search(
                String query,
                String workspaceId,
                List<String> collections,
                Map<String, Object> filters,
                int limit
        );
    }

    public record TextSearchResult(
            String id,
            String title,
            String snippet,
            double score,
            String type,
            String collection,
            Map<String, Object> metadata,
            List<String> highlights
    ) {}
}
