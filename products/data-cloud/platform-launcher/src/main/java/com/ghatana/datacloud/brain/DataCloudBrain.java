/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.datacloud.brain;

import com.ghatana.datacloud.DataRecord;
import com.ghatana.datacloud.attention.SalienceScore;
import com.ghatana.datacloud.attention.AttentionManager;
import com.ghatana.datacloud.pattern.PatternRecord;
import com.ghatana.datacloud.spi.SimilaritySearchCapability;
import com.ghatana.datacloud.reflex.ReflexOutcome;
import com.ghatana.datacloud.workspace.GlobalWorkspace;
import io.activej.promise.Promise;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Unified API facade for the DataCloud Brain architecture.
 *
 * <p>The DataCloudBrain provides a single entry point for all cognitive
 * operations, orchestrating the various subsystems (attention, memory,
 * patterns, reflexes, etc.) into a cohesive processing pipeline.
 *
 * <h2>Key Operations</h2>
 * <ul>
 *   <li><b>process()</b>: Main entry point for record processing</li>
 *   <li><b>search()</b>: Semantic search across memory</li>
 *   <li><b>query()</b>: Natural language query interface</li>
 *   <li><b>learn()</b>: Explicit learning from feedback</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * DataCloudBrain brain = DataCloudBrain.create(config);
 * 
 * // Process a record
 * ProcessingResult result = brain.process(eventRecord, context)
 *     .getResult();
 * 
 * // Semantic search
 * SearchResults results = brain.search("server errors", context)
 *     .getResult();
 * 
 * // Learn from feedback
 * brain.feedback(FeedbackEvent.positive(recordId, "accurate"))
 *     .getResult();
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Unified brain API
 * @doc.layer core
 * @doc.pattern Facade
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public interface DataCloudBrain {

    // ═══════════════════════════════════════════════════════════════════════════
    // Core Processing
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Processes a single data record through the brain.
     *
     * <p>This is the main entry point for record processing. The record
     * flows through salience scoring, reflex matching, workspace broadcast,
     * pattern matching, and memory storage.
     *
     * @param record the record to process
     * @param context the processing context
     * @return Promise containing the processing result
     */
    Promise<ProcessingResult> process(DataRecord record, BrainContext context);

    /**
     * Processes multiple records in batch.
     *
     * @param records the records to process
     * @param context the processing context
     * @return Promise containing batch results
     */
    Promise<BatchProcessingResult> processBatch(List<DataRecord> records, BrainContext context);

    /**
     * Evaluates a record without side effects (dry run).
     *
     * @param record the record to evaluate
     * @param context the processing context
     * @return Promise containing evaluation result
     */
    Promise<EvaluationResult> evaluate(DataRecord record, BrainContext context);

    // ═══════════════════════════════════════════════════════════════════════════
    // Search & Query
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Performs semantic search across memory.
     *
     * @param query the search query (text)
     * @param context the processing context
     * @return Promise containing search results
     */
    Promise<SimilaritySearchCapability.SearchResults> search(String query, BrainContext context);

    /**
     * Performs structured search with filters.
     *
     * @param request the search request
     * @param context the processing context
     * @return Promise containing search results
     */
    Promise<SimilaritySearchCapability.SearchResults> search(SimilaritySearchCapability.SearchRequest request, BrainContext context);

    /**
     * Finds records similar to a given record.
     *
     * @param recordId the reference record ID
     * @param k number of similar records to find
     * @param context the processing context
     * @return Promise containing similar records
     */
    Promise<SimilaritySearchCapability.SearchResults> findSimilar(String recordId, int k, BrainContext context);

    /**
     * Natural language query interface.
     *
     * @param query the natural language query
     * @param context the processing context
     * @return Promise containing query response
     */
    Promise<QueryResponse> query(String query, BrainContext context);

    // ═══════════════════════════════════════════════════════════════════════════
    // Pattern Operations
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Matches a record against known patterns.
     *
     * @param record the record to match
     * @param context the processing context
     * @return Promise containing matching patterns
     */
    Promise<List<PatternMatch>> matchPatterns(DataRecord record, BrainContext context);

    /**
     * Gets a pattern by ID.
     *
     * @param patternId the pattern ID
     * @param context the processing context
     * @return Promise containing the pattern if found
     */
    Promise<Optional<PatternRecord>> getPattern(String patternId, BrainContext context);

    /**
     * Lists active patterns.
     *
     * @param limit maximum patterns to return
     * @param context the processing context
     * @return Promise containing patterns
     */
    Promise<List<PatternRecord>> listPatterns(int limit, BrainContext context);

    // ═══════════════════════════════════════════════════════════════════════════
    // Learning & Feedback
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Submits feedback for learning.
     *
     * @param feedback the feedback event
     * @param context the processing context
     * @return Promise completing when feedback is processed
     */
    Promise<Void> feedback(com.ghatana.datacloud.client.feedback.FeedbackEvent feedback, BrainContext context);

    /**
     * Explicitly triggers learning on recent data.
     *
     * @param learningConfig learning configuration
     * @param context the processing context
     * @return Promise containing learning result
     */
    Promise<LearningResult> learn(LearningConfig learningConfig, BrainContext context);

    // ═══════════════════════════════════════════════════════════════════════════
    // Lifecycle & Admin
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Gets the brain configuration.
     *
     * @return the configuration
     */
    BrainConfig getConfig();

    /**
     * Gets available capabilities.
     *
     * @return capabilities descriptor
     */
    BrainCapabilities getCapabilities();

    /**
     * Gets brain statistics.
     *
     * @param context the context
     * @return Promise containing statistics
     */
    Promise<BrainStats> getStats(BrainContext context);

    /**
     * Initializes the brain.
     *
     * @return Promise completing when initialized
     */
    Promise<Void> initialize();

    /**
     * Shuts down the brain.
     *
     * @return Promise completing when shutdown
     */
    Promise<Void> shutdown();

    /**
     * Checks if the brain is healthy.
     *
     * @return Promise containing health status
     */
    Promise<HealthStatus> health();

    /**
     * Returns the underlying {@link GlobalWorkspace} when accessible.
     *
     * <p>Implementations that support direct workspace access (e.g. for SSE subscriptions or
     * salience lookup) should override this default. Other implementations may return
     * {@link Optional#empty()}.
     *
     * @return optional workspace reference
     *
     * @doc.type method
     * @doc.purpose Workspace accessor for HTTP / SSE layer
     * @doc.layer core
     * @doc.pattern Accessor
     */
    default Optional<GlobalWorkspace> getWorkspace() {
        return Optional.empty();
    }

    /**
     * Returns the underlying {@link AttentionManager} when accessible.
     *
     * <p>Implementations that support direct attention management (e.g. for threshold inspection
     * or manual elevation) should override this default.
     *
     * @return optional attention-manager reference
     *
     * @doc.type method
     * @doc.purpose AttentionManager accessor for HTTP layer
     * @doc.layer core
     * @doc.pattern Accessor
     */
    default Optional<AttentionManager> getAttentionManager() {
        return Optional.empty();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Types
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Result of processing a record.
     */
    @Value
    @Builder
    class ProcessingResult {
        /**
         * The processed record ID.
         */
        String recordId;

        /**
         * Whether processing was successful.
         */
        boolean success;

        /**
         * Salience score assigned.
         */
        SalienceScore salienceScore;

        /**
         * Memory tier assigned.
         */
        String memoryTier;

        /**
         * Patterns that matched.
         */
        @Builder.Default
        List<PatternMatch> patternMatches = List.of();

        /**
         * Reflex actions executed.
         */
        @Builder.Default
        List<ReflexOutcome> reflexOutcomes = List.of();

        /**
         * Processing time in milliseconds.
         */
        long processingTimeMs;

        /**
         * Additional details.
         */
        @Builder.Default
        Map<String, Object> details = Map.of();

        /**
         * Error message if failed.
         */
        String errorMessage;
    }

    /**
     * Result of batch processing.
     */
    @Value
    @Builder
    class BatchProcessingResult {
        /**
         * Individual results.
         */
        List<ProcessingResult> results;

        /**
         * Total records processed.
         */
        int totalProcessed;

        /**
         * Successful records.
         */
        int successful;

        /**
         * Failed records.
         */
        int failed;

        /**
         * Total processing time.
         */
        long totalProcessingTimeMs;
    }

    /**
     * Result of evaluating a record (dry run).
     */
    @Value
    @Builder
    class EvaluationResult {
        /**
         * Salience score.
         */
        SalienceScore salienceScore;

        /**
         * Would-be memory tier.
         */
        String memoryTier;

        /**
         * Would-match patterns.
         */
        List<PatternMatch> patternMatches;

        /**
         * Would-fire reflexes.
         */
        List<String> wouldTriggerRules;

        /**
         * Recommendations.
         */
        List<String> recommendations;
    }

    /**
     * Pattern match result.
     */
    @Value
    @Builder
    class PatternMatch {
        /**
         * The matched pattern.
         */
        PatternRecord pattern;

        /**
         * Match score.
         */
        float score;

        /**
         * Match confidence.
         */
        float confidence;

        /**
         * Explanation.
         */
        String explanation;
    }

    /**
     * Response to a natural language query.
     */
    @Value
    @Builder
    class QueryResponse {
        /**
         * The query that was asked.
         */
        String query;

        /**
         * The response.
         */
        String response;

        /**
         * Supporting records.
         */
        @Builder.Default
        List<DataRecord> supportingRecords = List.of();

        /**
         * Confidence in the response.
         */
        float confidence;

        /**
         * Sources used.
         */
        @Builder.Default
        List<String> sources = List.of();
    }

    /**
     * Configuration for explicit learning.
     */
    @Value
    @Builder
    class LearningConfig {
        /**
         * Types of patterns to learn.
         */
        @Builder.Default
        List<String> patternTypes = List.of();

        /**
         * Time window for learning.
         */
        String timeWindow;

        /**
         * Minimum samples required.
         */
        @Builder.Default
        int minSamples = 100;

        /**
         * Maximum patterns to learn.
         */
        @Builder.Default
        int maxPatterns = 10;
    }

    /**
     * Result of learning operation.
     */
    @Value
    @Builder
    class LearningResult {
        /**
         * Patterns discovered.
         */
        List<PatternRecord> patternsDiscovered;

        /**
         * Patterns updated.
         */
        List<PatternRecord> patternsUpdated;

        /**
         * Patterns deprecated.
         */
        List<String> patternsDeprecated;

        /**
         * Records analyzed.
         */
        long recordsAnalyzed;

        /**
         * Learning time.
         */
        long learningTimeMs;
    }

    /**
     * Brain statistics.
     */
    @Value
    @Builder
    class BrainStats {
        /**
         * Total records processed.
         */
        long totalRecordsProcessed;

        /**
         * Active patterns.
         */
        int activePatterns;

        /**
         * Active reflex rules.
         */
        int activeRules;

        /**
         * Records in hot tier.
         */
        int hotTierRecords;

        /**
         * Records in warm tier.
         */
        int warmTierRecords;

        /**
         * Average processing time.
         */
        double avgProcessingTimeMs;

        /**
         * Uptime in seconds.
         */
        long uptimeSeconds;
    }

    /**
     * Health status.
     */
    @Value
    @Builder
    class HealthStatus {
        /**
         * Overall health status.
         */
        Status status;

        /**
         * Component health.
         */
        @Builder.Default
        Map<String, Status> components = Map.of();

        /**
         * Status messages.
         */
        @Builder.Default
        List<String> messages = List.of();

        public enum Status {
            HEALTHY, DEGRADED, UNHEALTHY
        }
    }
}
