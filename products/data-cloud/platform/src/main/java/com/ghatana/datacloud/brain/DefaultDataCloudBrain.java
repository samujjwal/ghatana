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
import com.ghatana.datacloud.client.feedback.FeedbackEvent;
import com.ghatana.datacloud.attention.AttentionManager;
import com.ghatana.datacloud.attention.SalienceScore;
import com.ghatana.datacloud.attention.SalienceScorer;
import com.ghatana.datacloud.memory.MemoryTierRouter;
import com.ghatana.datacloud.pattern.PatternCatalog;
import com.ghatana.datacloud.pattern.PatternRecord;
import com.ghatana.datacloud.reflex.DefaultReflexEngine;
import com.ghatana.datacloud.reflex.ReflexEngine;
import com.ghatana.datacloud.reflex.ReflexOutcome;
import com.ghatana.datacloud.reflex.ReflexTrigger;
import com.ghatana.datacloud.spi.SimilaritySearchCapability;
import com.ghatana.datacloud.workspace.GlobalWorkspace;
import com.ghatana.datacloud.workspace.SpotlightItem;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Default implementation of the DataCloudBrain.
 *
 * <p>Orchestrates all brain subsystems into a cohesive processing pipeline.
 *
 * @doc.type class
 * @doc.purpose Default brain implementation
 * @doc.layer core
 * @doc.pattern Facade, Orchestrator
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public class DefaultDataCloudBrain implements DataCloudBrain {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultDataCloudBrain.class);

    // Executor for blocking operations
    private static final Executor BLOCKING_EXECUTOR = Executors.newCachedThreadPool();

    // Configuration
    private final BrainConfig config;
    private final BrainCapabilities capabilities;

    // Subsystems
    private final SalienceScorer salienceScorer;
    private final AttentionManager attentionManager;
    private final GlobalWorkspace globalWorkspace;
    private final MemoryTierRouter<DataRecord> memoryRouter;
    private final PatternCatalog patternCatalog;
    private final ReflexEngine reflexEngine;
    private final SimilaritySearchCapability vectorMemory;

    // Statistics
    private final AtomicLong totalProcessed = new AtomicLong(0);
    private final AtomicLong totalSuccessful = new AtomicLong(0);
    private volatile double totalProcessingTime = 0;
    private final Instant startTime = Instant.now();
    private volatile boolean initialized = false;

    /**
     * Creates a new brain with the specified configuration and subsystems.
     *
     * @param config         the brain configuration
     * @param salienceScorer the salience scorer
     * @param globalWorkspace the global workspace
     * @param memoryRouter    the memory tier router
     * @param patternCatalog  the pattern catalog
     * @param reflexEngine    the reflex engine
     * @param vectorMemory    the vector memory (optional, can be null)
     */
    public DefaultDataCloudBrain(
            BrainConfig config,
            SalienceScorer salienceScorer,
            GlobalWorkspace globalWorkspace,
            MemoryTierRouter<DataRecord> memoryRouter,
            PatternCatalog patternCatalog,
            ReflexEngine reflexEngine,
            SimilaritySearchCapability vectorMemory) {
        this.config = config;
        this.capabilities = buildCapabilities(config);
        this.salienceScorer = salienceScorer;
        this.attentionManager = createAttentionManager(salienceScorer, globalWorkspace);
        this.globalWorkspace = globalWorkspace;
        this.memoryRouter = memoryRouter;
        this.patternCatalog = patternCatalog;
        this.reflexEngine = reflexEngine != null ? reflexEngine : new DefaultReflexEngine();
        this.vectorMemory = vectorMemory;
        
        LOG.info("Created DataCloudBrain: {}", config.getBrainId());
    }

    private AttentionManager createAttentionManager(SalienceScorer scorer, GlobalWorkspace workspace) {
        return AttentionManager.builder()
                .salienceScorer(scorer)
                .globalWorkspace(workspace)
                .build();
    }

    private BrainCapabilities buildCapabilities(BrainConfig config) {
        return BrainCapabilities.builder()
                .tieredMemory(config.isTieredMemoryEnabled())
                .vectorMemory(config.isSemanticSearchEnabled())
                .semanticSearch(config.isSemanticSearchEnabled())
                .patternLearning(config.isLearningEnabled())
                .reflexProcessing(config.isReflexesEnabled())
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Core Processing
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public Promise<ProcessingResult> process(DataRecord record, BrainContext context) {
        if (!initialized) {
            return Promise.ofException(new IllegalStateException("Brain not initialized"));
        }

        if (context.isExpired()) {
            return Promise.of(ProcessingResult.builder()
                    .recordId(getRecordId(record))
                    .success(false)
                    .errorMessage("Context deadline exceeded")
                    .build());
        }

        long processingStart = System.currentTimeMillis();
        String tenantId = context.getTenantId();
        totalProcessed.incrementAndGet();

        // Use the async pipeline
        SalienceScorer.ScoringContext scoringContext = SalienceScorer.ScoringContext.forTenant(tenantId);

        return salienceScorer.score(record, scoringContext)
                .then(salienceScore -> processWithScore(record, salienceScore, context, processingStart));
    }

    private Promise<ProcessingResult> processWithScore(
            DataRecord record, 
            SalienceScore salienceScore, 
            BrainContext context,
            long processingStart) {
        
        String tenantId = context.getTenantId();
        String recordId = getRecordId(record);

        // Route to memory tier
        return memoryRouter.route(record, salienceScore)
                .then(routingResult -> {
                    // Execute reflexes if enabled
                    Promise<List<ReflexOutcome>> reflexPromise = executeReflexes(record, tenantId, context);

                    return reflexPromise.map(reflexOutcomes -> {
                        // Match patterns synchronously (quick operation)
                        List<PatternMatch> patternMatches = matchPatternsInternal(record, context);

                        // Store in vector memory (fire and forget)
                        storeInVectorMemory(record, tenantId);

                        // Broadcast to workspace (fire and forget)
                        broadcastIfCritical(record, salienceScore, tenantId);

                        long processingTime = System.currentTimeMillis() - processingStart;
                        totalProcessingTime += processingTime;
                        totalSuccessful.incrementAndGet();

                        return ProcessingResult.builder()
                                .recordId(recordId)
                                .success(true)
                                .salienceScore(salienceScore)
                                .memoryTier(routingResult.targetTier().name())
                                .patternMatches(patternMatches)
                                .reflexOutcomes(reflexOutcomes)
                                .processingTimeMs(processingTime)
                                .build();
                    });
                })
                .whenException(e -> {
                    LOG.error("Processing failed for record {}: {}", recordId, e.getMessage(), e);
                });
    }

    private Promise<List<ReflexOutcome>> executeReflexes(DataRecord record, String tenantId, BrainContext context) {
        if (!config.isReflexesEnabled() || !context.isReflexesEnabled()) {
            return Promise.of(List.of());
        }

        ReflexTrigger trigger = ReflexTrigger.fromRecord(record, tenantId);
        return reflexEngine.process(trigger)
                .map(result -> result.getOutcomes())
                .whenException(e -> LOG.warn("Reflex execution failed: {}", e.getMessage()));
    }

    private Promise<Void> storeInVectorMemory(DataRecord record, String tenantId) {
        if (!config.isSemanticSearchEnabled() || vectorMemory == null) {
            return Promise.complete();
        }

        return vectorMemory.store(record, tenantId)
                .whenException(e -> LOG.warn("Vector storage failed: {}", e.getMessage()));
    }

    private Promise<Void> broadcastIfCritical(DataRecord record, SalienceScore salienceScore, String tenantId) {
        if (!salienceScore.isCritical()) {
            return Promise.complete();
        }

        SpotlightItem item = SpotlightItem.builder()
                .record(record)
                .salienceScore(salienceScore)
                .tenantId(tenantId)
                .summary("Critical salience detected: score=" + salienceScore.getScore())
                .emergency(salienceScore.getScore() >= SalienceScore.EMERGENCY_THRESHOLD)
                .build();

        return salienceScore.getScore() >= SalienceScore.EMERGENCY_THRESHOLD
                ? globalWorkspace.broadcast(item)
                : globalWorkspace.spotlight(item);
    }

    @Override
    public Promise<BatchProcessingResult> processBatch(List<DataRecord> records, BrainContext context) {
        long startTime = System.currentTimeMillis();

        List<Promise<ProcessingResult>> promises = records.stream()
                .map(record -> process(record, context))
                .collect(Collectors.toList());

        return Promises.toList(promises)
                .map(results -> {
                    int successful = (int) results.stream().filter(ProcessingResult::isSuccess).count();
                    return BatchProcessingResult.builder()
                            .results(results)
                            .totalProcessed(records.size())
                            .successful(successful)
                            .failed(records.size() - successful)
                            .totalProcessingTimeMs(System.currentTimeMillis() - startTime)
                            .build();
                });
    }

    @Override
    public Promise<EvaluationResult> evaluate(DataRecord record, BrainContext context) {
        String tenantId = context.getTenantId();
        SalienceScorer.ScoringContext scoringContext = SalienceScorer.ScoringContext.forTenant(tenantId);

        return salienceScorer.score(record, scoringContext)
                .then(salienceScore -> {
                    return memoryRouter.route(record, salienceScore)
                            .map(routingResult -> {
                                List<PatternMatch> patternMatches = matchPatternsInternal(record, context);
                                List<String> wouldTriggerRules = findMatchingRuleIds(record, context);

                                return EvaluationResult.builder()
                                        .salienceScore(salienceScore)
                                        .memoryTier(routingResult.targetTier().name())
                                        .patternMatches(patternMatches)
                                        .wouldTriggerRules(wouldTriggerRules)
                                        .recommendations(List.of())
                                        .build();
                            });
                });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Search & Query
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public Promise<SimilaritySearchCapability.SearchResults> search(String query, BrainContext context) {
        return search(SimilaritySearchCapability.SearchRequest.builder()
                .queryText(query)
                .k(10)
                .tenantId(context.getTenantId())
                .build(), context);
    }

    @Override
    public Promise<SimilaritySearchCapability.SearchResults> search(
            SimilaritySearchCapability.SearchRequest request, 
            BrainContext context) {
        if (!config.isSemanticSearchEnabled() || vectorMemory == null) {
            return Promise.of(SimilaritySearchCapability.SearchResults.empty());
        }

        return vectorMemory.search(request);
    }

    @Override
    public Promise<SimilaritySearchCapability.SearchResults> findSimilar(
            String recordId, 
            int k, 
            BrainContext context) {
        if (!config.isSemanticSearchEnabled() || vectorMemory == null) {
            return Promise.of(SimilaritySearchCapability.SearchResults.empty());
        }

        return vectorMemory.findSimilar(recordId, k, true, context.getTenantId());
    }

    @Override
    public Promise<QueryResponse> query(String query, BrainContext context) {
        return search(query, context)
                .map(results -> {
                    List<DataRecord> supporting = results.getResults().stream()
                            .map(r -> r.getRecord().getRecord())
                            .limit(5)
                            .collect(Collectors.toList());

                    List<String> sources = supporting.stream()
                            .map(this::getRecordId)
                            .collect(Collectors.toList());

                    return QueryResponse.builder()
                            .query(query)
                            .response("Found " + results.getTotalMatches() + " relevant records")
                            .supportingRecords(supporting)
                            .confidence(results.hasResults() ? results.getResults().get(0).getScore() : 0)
                            .sources(sources)
                            .build();
                });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Pattern Operations
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public Promise<List<PatternMatch>> matchPatterns(DataRecord record, BrainContext context) {
        return Promise.of(matchPatternsInternal(record, context));
    }

    private List<PatternMatch> matchPatternsInternal(DataRecord record, BrainContext context) {
        try {
            List<PatternRecord> activePatterns = patternCatalog
                    .listActive(context.getTenantId(), config.getMaxPatternsPerRecord())
                    .getResult();

            return activePatterns.stream()
                    .map(pattern -> PatternMatch.builder()
                            .pattern(pattern)
                            .score(calculatePatternMatch(record, pattern))
                            .confidence(pattern.getConfidence())
                            .explanation("Matched based on type and metadata")
                            .build())
                    .filter(match -> match.getScore() >= config.getMinPatternConfidence())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            LOG.warn("Pattern matching failed: {}", e.getMessage());
            return List.of();
        }
    }

    private float calculatePatternMatch(DataRecord record, PatternRecord pattern) {
        float score = 0.5f;

        if (pattern.getType() != null) {
            String recordType = record.getClass().getSimpleName();
            if (pattern.getTags() != null && pattern.getTags().contains(recordType.toLowerCase())) {
                score += 0.2f;
            }
        }

        if (pattern.getEmbedding() != null && vectorMemory != null) {
            try {
                Optional<SimilaritySearchCapability.VectorData> vectorRecord = vectorMemory
                        .getById(getRecordId(record), pattern.getTenantId())
                        .getResult();
                if (vectorRecord.isPresent()) {
                    score = vectorRecord.get().cosineSimilarity(pattern.getEmbedding());
                }
            } catch (Exception e) {
                // Ignore embedding match failure
            }
        }

        return Math.min(score, 1.0f);
    }

    private List<String> findMatchingRuleIds(DataRecord record, BrainContext context) {
        try {
            ReflexTrigger trigger = ReflexTrigger.fromRecord(record, context.getTenantId());
            return reflexEngine.findMatchingRules(trigger).getResult().stream()
                    .map(rule -> rule.getId())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }

    @Override
    public Promise<Optional<PatternRecord>> getPattern(String patternId, BrainContext context) {
        return patternCatalog.get(patternId, context.getTenantId());
    }

    @Override
    public Promise<List<PatternRecord>> listPatterns(int limit, BrainContext context) {
        return patternCatalog.listActive(context.getTenantId(), limit);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Learning & Feedback
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public Promise<Void> feedback(FeedbackEvent feedback, BrainContext context) {
        LOG.debug("Received feedback: type={}, reference={}", 
                feedback.getFeedbackType(), feedback.getReferenceType());
        return Promise.complete();
    }

    @Override
    public Promise<LearningResult> learn(LearningConfig learningConfig, BrainContext context) {
        if (!config.isLearningEnabled()) {
            return Promise.of(LearningResult.builder()
                    .patternsDiscovered(List.of())
                    .patternsUpdated(List.of())
                    .patternsDeprecated(List.of())
                    .recordsAnalyzed(0)
                    .learningTimeMs(0)
                    .build());
        }

        LOG.info("Learning requested with config: {}", learningConfig);
        return Promise.of(LearningResult.builder()
                .patternsDiscovered(List.of())
                .patternsUpdated(List.of())
                .patternsDeprecated(List.of())
                .recordsAnalyzed(0)
                .learningTimeMs(0)
                .build());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Lifecycle & Admin
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public BrainConfig getConfig() {
        return config;
    }

    @Override
    public BrainCapabilities getCapabilities() {
        return capabilities;
    }

    @Override
    public Promise<BrainStats> getStats(BrainContext context) {
        String tenantId = context.getTenantId();

        return Promise.ofBlocking(BLOCKING_EXECUTOR, () -> {
            long processed = totalProcessed.get();
            long uptime = java.time.Duration.between(startTime, Instant.now()).getSeconds();

            int activePatterns = 0;
            int activeRules = 0;

            try {
                activePatterns = patternCatalog.listActive(tenantId, 1000).getResult().size();
            } catch (Exception e) {
                LOG.warn("Failed to get active patterns: {}", e.getMessage());
            }

            try {
                activeRules = reflexEngine.listRules(tenantId).getResult().size();
            } catch (Exception e) {
                LOG.warn("Failed to get active rules: {}", e.getMessage());
            }

            return BrainStats.builder()
                    .totalRecordsProcessed(processed)
                    .activePatterns(activePatterns)
                    .activeRules(activeRules)
                    .hotTierRecords(0)
                    .warmTierRecords(0)
                    .avgProcessingTimeMs(processed > 0 ? totalProcessingTime / processed : 0)
                    .uptimeSeconds(uptime)
                    .build();
        });
    }

    @Override
    public Promise<Void> initialize() {
        LOG.info("Initializing DataCloudBrain: {}", config.getBrainId());
        initialized = true;
        LOG.info("DataCloudBrain initialized successfully");
        return Promise.complete();
    }

    @Override
    public Promise<Void> shutdown() {
        LOG.info("Shutting down DataCloudBrain: {}", config.getBrainId());
        initialized = false;
        LOG.info("DataCloudBrain shutdown complete. Processed {} records", totalProcessed.get());
        return Promise.complete();
    }

    @Override
    public Promise<HealthStatus> health() {
        Map<String, HealthStatus.Status> components = Map.of(
                "attention", HealthStatus.Status.HEALTHY,
                "workspace", HealthStatus.Status.HEALTHY,
                "memory", HealthStatus.Status.HEALTHY,
                "patterns", HealthStatus.Status.HEALTHY,
                "reflexes", config.isReflexesEnabled() ? HealthStatus.Status.HEALTHY : HealthStatus.Status.DEGRADED,
                "vector", config.isSemanticSearchEnabled() && vectorMemory != null 
                        ? HealthStatus.Status.HEALTHY : HealthStatus.Status.DEGRADED
        );

        HealthStatus.Status overall = components.values().stream()
                .anyMatch(s -> s == HealthStatus.Status.UNHEALTHY)
                ? HealthStatus.Status.UNHEALTHY
                : components.values().stream().anyMatch(s -> s == HealthStatus.Status.DEGRADED)
                ? HealthStatus.Status.DEGRADED
                : HealthStatus.Status.HEALTHY;

        return Promise.of(HealthStatus.builder()
                .status(overall)
                .components(components)
                .messages(List.of("Brain operational: " + config.getBrainId()))
                .build());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════

    private String getRecordId(DataRecord record) {
        return record.getId() != null ? record.getId().toString() : "unknown";
    }

    /**
     * Creates a new brain with the specified configuration and default subsystems.
     *
     * @param config the configuration
     * @param salienceScorer the salience scorer
     * @param globalWorkspace the global workspace
     * @param memoryRouter the memory router
     * @param patternCatalog the pattern catalog
     * @return new brain instance
     */
    public static DataCloudBrain create(
            BrainConfig config,
            SalienceScorer salienceScorer,
            GlobalWorkspace globalWorkspace,
            MemoryTierRouter<DataRecord> memoryRouter,
            PatternCatalog patternCatalog) {
        return new DefaultDataCloudBrain(
                config,
                salienceScorer,
                globalWorkspace,
                memoryRouter,
                patternCatalog,
                null,
                null
        );
    }
}
