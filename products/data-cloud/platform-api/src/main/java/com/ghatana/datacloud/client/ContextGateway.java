package com.ghatana.datacloud.client;

import io.activej.promise.Promise;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Gateway interface for LLM context selection and management.
 *
 * <p>
 * <b>Purpose</b><br>
 * Manages the flow of context from Data-Cloud to LLMs:
 * <ul>
 * <li>Context selection based on relevance</li>
 * <li>Token budget management</li>
 * <li>Freshness and confidence filtering</li>
 * <li>Semantic search over context documents</li>
 * <li>Context ranking and prioritization</li>
 * </ul>
 *
 * <p>
 * <b>Context Selection Rules</b><br>
 * <ol>
 * <li>Tier-1 facts preferred over summaries</li>
 * <li>Higher determinism preferred</li>
 * <li>Fresh context preferred over stale</li>
 * <li>Tenant isolation strictly enforced</li>
 * <li>Policy-compliant data only</li>
 * <li>AI may rank but never fabricate</li>
 * </ol>
 *
 * <p>
 * <b>LLM Contract</b><br>
 * LLMs receive:
 * <ul>
 * <li>Explicit task/prompt</li>
 * <li>Context bundle with metadata (confidence, determinism, freshness)</li>
 * <li><b>NO write access</b> to Tier-1 storage</li>
 * <li>Clear boundaries on what can be modified</li>
 * </ul>
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * // Select context for an LLM query
 * ContextBundle bundle = contextGateway.selectContext(
 *     ContextRequest.builder()
 *         .tenantId("tenant-123")
 *         .query("Show me login patterns for last week")
 *         .maxTokens(4000)
 *         .minConfidence(0.8)
 *         .minDeterminism(DeterminismLevel.MEDIUM)
 *         .includeTypes(List.of(ContextType.FACT, ContextType.AGGREGATION))
 *         .build()
 * ).getResult();
 *
 * // Send to LLM
 * String prompt = buildPrompt(userQuery, bundle);
 * String response = llmClient.complete(prompt);
 * }</pre>
 *
 * @see ContextDocument
 * @see LearningSignal
 * @doc.type interface
 * @doc.purpose LLM context gateway
 * @doc.layer core
 * @doc.pattern Gateway
 */
public interface ContextGateway {

    /**
     * Selects relevant context documents for an LLM query.
     *
     * @param request Context selection request
     * @return Promise with context bundle
     */
    Promise<ContextBundle> selectContext(ContextRequest request);

    /**
     * Stores a context document for later retrieval.
     *
     * @param document Context document
     * @return Promise with stored document
     */
    Promise<ContextDocument> store(ContextDocument document);

    /**
     * Searches context documents using semantic similarity.
     *
     * @param tenantId Tenant ID
     * @param query Query text
     * @param limit Maximum results
     * @return Promise with matching documents
     */
    Promise<List<ContextDocument>> semanticSearch(String tenantId, String query, int limit);

    /**
     * Purges expired context documents.
     *
     * @param tenantId Tenant ID
     * @return Promise with deletion count
     */
    Promise<Long> purgeExpiredContext(String tenantId);

    /**
     * Gets statistics about context usage.
     *
     * @param tenantId Tenant ID
     * @param window Time window for stats
     * @return Promise with usage statistics
     */
    Promise<ContextStatistics> getStatistics(String tenantId, Duration window);

    /**
     * Request for context selection.
     */
    record ContextRequest(
            String tenantId,
            String query,
            Integer maxTokens,
            Double minConfidence,
            com.ghatana.datacloud.spi.ai.PredictionCapability.DeterminismLevel minDeterminism,
            List<ContextDocument.ContextType> includeTypes,
            List<ContextDocument.ContextType> excludeTypes,
            Map<String, String> filters,
            String correlationId
    ) {

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String tenantId;
            private String query;
            private Integer maxTokens = 4000;
            private Double minConfidence = 0.7;
            private com.ghatana.datacloud.spi.ai.PredictionCapability.DeterminismLevel minDeterminism =
                    com.ghatana.datacloud.spi.ai.PredictionCapability.DeterminismLevel.MEDIUM;
            private List<ContextDocument.ContextType> includeTypes;
            private List<ContextDocument.ContextType> excludeTypes;
            private Map<String, String> filters;
            private String correlationId;

            public Builder tenantId(String tenantId) {
                this.tenantId = tenantId;
                return this;
            }

            public Builder query(String query) {
                this.query = query;
                return this;
            }

            public Builder maxTokens(Integer maxTokens) {
                this.maxTokens = maxTokens;
                return this;
            }

            public Builder minConfidence(Double minConfidence) {
                this.minConfidence = minConfidence;
                return this;
            }

            public Builder minDeterminism(com.ghatana.datacloud.spi.ai.PredictionCapability.DeterminismLevel minDeterminism) {
                this.minDeterminism = minDeterminism;
                return this;
            }

            public Builder includeTypes(List<ContextDocument.ContextType> includeTypes) {
                this.includeTypes = includeTypes;
                return this;
            }

            public Builder excludeTypes(List<ContextDocument.ContextType> excludeTypes) {
                this.excludeTypes = excludeTypes;
                return this;
            }

            public Builder filters(Map<String, String> filters) {
                this.filters = filters;
                return this;
            }

            public Builder correlationId(String correlationId) {
                this.correlationId = correlationId;
                return this;
            }

            public ContextRequest build() {
                return new ContextRequest(
                        tenantId,
                        query,
                        maxTokens,
                        minConfidence,
                        minDeterminism,
                        includeTypes,
                        excludeTypes,
                        filters,
                        correlationId
                );
            }
        }
    }

    /**
     * Bundle of context documents selected for LLM.
     */
    record ContextBundle(
            List<ContextDocument> documents,
            int totalTokens,
            Map<String, Object> metadata,
            SelectionStrategy strategyUsed
    ) {

        /**
         * Checks if bundle fits within token budget.
         */
        public boolean fitsWithinBudget(int maxTokens) {
            return totalTokens <= maxTokens;
        }

        /**
         * Gets documents by type.
         */
        public List<ContextDocument> getByType(ContextDocument.ContextType type) {
            return documents.stream()
                    .filter(doc -> doc.getContextType() == type)
                    .toList();
        }

        /**
         * Gets only high-confidence documents.
         */
        public List<ContextDocument> getHighConfidence(double threshold) {
            return documents.stream()
                    .filter(doc -> doc.getConfidence() >= threshold)
                    .toList();
        }
    }

    /**
     * Context selection strategies.
     */
    enum SelectionStrategy {
        /**
         * Semantic similarity ranking.
         */
        SEMANTIC,

        /**
         * Recency-based selection.
         */
        RECENCY,

        /**
         * Confidence-weighted ranking.
         */
        CONFIDENCE,

        /**
         * Hybrid approach (semantic + confidence + recency).
         */
        HYBRID,

        /**
         * Custom strategy.
         */
        CUSTOM
    }

    /**
     * Statistics about context usage.
     */
    record ContextStatistics(
            long totalDocuments,
            long freshDocuments,
            long staleDocuments,
            Map<ContextDocument.ContextType, Long> countsByType,
            double averageConfidence,
            int averageTokenCount,
            long totalQueries,
            double averageContextHitRate
    ) {
    }
}

