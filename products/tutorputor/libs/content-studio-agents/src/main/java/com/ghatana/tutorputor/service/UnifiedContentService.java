package com.ghatana.tutorputor.service;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.tutorputor.agent.ContentGenerationAgent;
import com.ghatana.tutorputor.agent.ContentGenerationRequest;
import com.ghatana.tutorputor.agent.ContentGenerationResponse;
import com.ghatana.tutorputor.agent.LearnerInteractionAgent;
import com.ghatana.tutorputor.agent.LearnerAction;
import com.ghatana.tutorputor.agent.TutoringResponse;
import com.ghatana.tutorputor.contentstudio.knowledge.KnowledgeBaseService;
import com.ghatana.tutorputor.experiment.ContentStrategySelector;
import com.ghatana.tutorputor.experiment.ExperimentManager;
import com.ghatana.tutorputor.experiment.ExperimentMetricsCollector;
import com.ghatana.tutorputor.worker.ContentJob;
import com.ghatana.tutorputor.worker.ContentJobQueue;
import com.ghatana.tutorputor.worker.JobProgressTracker;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Unified content service that consolidates all content generation capabilities.
 * 
 * <p>This service facade provides a single entry point for:
 * <ul>
 *   <li>Synchronous content generation (claims, examples, simulations)</li>
 *   <li>Asynchronous batch content generation via job queue</li>
 *   <li>Learner interaction and adaptive tutoring</li>
 *   <li>A/B experiment-aware content strategy selection</li>
 *   <li>Progress tracking and metrics collection</li>
 * </ul>
 *
 * <p>This replaces the need for separate:
 * <ul>
 *   <li>content-generation-worker</li>
 *   <li>content-validator</li>
 *   <li>content-enhancement</li>
 *   <li>tutoring-agent</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Unified content service facade
 * @doc.layer product
 * @doc.pattern Facade
 */
public class UnifiedContentService {

    private static final Logger LOG = LoggerFactory.getLogger(UnifiedContentService.class);
    private static final Duration DEFAULT_ASYNC_STEP_TIMEOUT = Duration.ofSeconds(30);

    private final ContentGenerationAgent contentAgent;
    private final LearnerInteractionAgent tutoringAgent;
    private final KnowledgeBaseService knowledgeService;
    private final ContentStrategySelector strategySelector;
    private final ExperimentManager experimentManager;
    private final ExperimentMetricsCollector metricsCollector;
    private final ContentJobQueue jobQueue;
    private final JobProgressTracker progressTracker;
    private final MeterRegistry meterRegistry;
    private final String tenantId;
    private final MemoryStore memoryStore;

    // Metrics
    private final Timer syncGenerationTimer;
    private final Timer asyncGenerationTimer;
    private final Timer tutoringTimer;

    /**
     * Creates a new unified content service.
     *
     * @param config the service configuration
     */
    public UnifiedContentService(@NotNull ServiceConfig config) {
        this.contentAgent = config.contentAgent();
        this.tutoringAgent = config.tutoringAgent();
        this.knowledgeService = config.knowledgeService();
        this.strategySelector = config.strategySelector();
        this.experimentManager = config.experimentManager();
        this.metricsCollector = config.metricsCollector();
        this.jobQueue = config.jobQueue();
        this.progressTracker = config.progressTracker();
        this.meterRegistry = config.meterRegistry();
        this.tenantId = config.tenantId();
        this.memoryStore = config.memoryStore();

        // Initialize metrics
        this.syncGenerationTimer = Timer.builder("tutorputor.content.sync_generation")
            .description("Synchronous content generation time")
            .register(meterRegistry);
        this.asyncGenerationTimer = Timer.builder("tutorputor.content.async_generation")
            .description("Asynchronous content generation time")
            .register(meterRegistry);
        this.tutoringTimer = Timer.builder("tutorputor.tutoring.response_time")
            .description("Tutoring response time")
            .register(meterRegistry);

        configureAsyncJobHandling();

        LOG.info("UnifiedContentService initialized");
    }

    @NotNull
    private AgentContext createAgentContext(
            @NotNull String agentId,
            String userId,
            String sessionId,
            @NotNull Map<String, Object> contextConfig) {
        return AgentContext.builder()
            .turnId(UUID.randomUUID().toString())
            .agentId(agentId)
            .tenantId(tenantId)
            .userId(userId)
            .sessionId(sessionId)
            .startTime(Instant.now())
            .memoryStore(memoryStore)
            .logger(LOG)
            .config(contextConfig)
            .remainingBudget(null)
            .build();
    }

    private void configureAsyncJobHandling() {
        jobQueue.registerHandler(ContentJob.JobType.BATCH_GENERATION, this::processBatchGenerationJob);

        jobQueue.onJobCompleted(job -> {
            String summary = "Completed " + job.type() + " job";
            if (job.result() instanceof Map<?, ?> resultMap) {
                Object generatedCount = resultMap.get("generatedCount");
                if (generatedCount != null) {
                    summary = "Generated " + generatedCount + " item(s)";
                }
            }
            progressTracker.completeJob(job.id(), summary);
        });

        jobQueue.onJobFailed(job -> progressTracker.failJob(
            job.id(),
            job.error() != null ? job.error() : "Job failed without error details"
        ));
    }

    private Object processBatchGenerationJob(@NotNull ContentJob job) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            List<String> topics = asStringList(job.payload().get("topics"), "topics");
            List<String> rawContentTypes = asStringList(job.payload().get("contentTypes"), "contentTypes");
            int gradeLevel = asInt(job.payload().getOrDefault("gradeLevel", 5), "gradeLevel");
            String domain = normalizeDomain(String.valueOf(job.payload().getOrDefault("domain", "GENERAL")));

            if (topics.isEmpty()) {
                throw new IllegalArgumentException("Batch generation requires at least one topic");
            }
            if (rawContentTypes.isEmpty()) {
                throw new IllegalArgumentException("Batch generation requires at least one content type");
            }

            List<Map<String, Object>> generatedItems = new ArrayList<>();
            int totalSteps = topics.size() * rawContentTypes.size();
            int completedSteps = 0;

            for (String topic : topics) {
                for (String rawType : rawContentTypes) {
                    ContentGenerationRequest.ContentType contentType = parseContentType(rawType);
                    ContentGenerationResponse response = awaitPromise(
                        generateContent(new ContentGenerationRequest(
                            topic,
                            domain,
                            String.valueOf(gradeLevel),
                            contentType,
                            job.requesterId(),
                            null,
                            null,
                            null,
                            Map.of("tenantId", job.tenantId(), "jobId", job.id())
                        )),
                        DEFAULT_ASYNC_STEP_TIMEOUT
                    );

                    String content = response != null && response.content() != null
                        ? response.content()
                        : "";

                    generatedItems.add(Map.of(
                        "topic", topic,
                        "contentType", contentType.name(),
                        "content", content
                    ));

                    completedSteps++;
                    progressTracker.updateProgress(
                        job.id(),
                        completedSteps,
                        "Generated " + contentType.name() + " for \"" + topic + "\""
                    );
                }
            }

            return Map.of(
                "status", "completed",
                "generatedCount", generatedItems.size(),
                "requestedCount", totalSteps,
                "items", generatedItems
            );
        } finally {
            sample.stop(asyncGenerationTimer);
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> asStringList(Object raw, String fieldName) {
        if (!(raw instanceof List<?> rawList)) {
            throw new IllegalArgumentException("Expected list for payload field: " + fieldName);
        }

        List<String> result = new ArrayList<>(rawList.size());
        for (Object item : rawList) {
            if (!(item instanceof String text)) {
                throw new IllegalArgumentException(
                    "Expected string entries for payload field: " + fieldName
                );
            }
            result.add(text);
        }
        return result;
    }

    private int asInt(Object raw, String fieldName) {
        if (raw instanceof Number number) {
            return number.intValue();
        }

        if (raw instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                    "Expected integer value for payload field: " + fieldName, e
                );
            }
        }

        throw new IllegalArgumentException("Expected integer value for payload field: " + fieldName);
    }

    private String normalizeDomain(String rawDomain) {
        String normalized = rawDomain == null ? "" : rawDomain.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "GENERAL";
        }
        return normalized;
    }

    private ContentGenerationRequest.ContentType parseContentType(String rawType) {
        String normalized = rawType == null ? "" : rawType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "CLAIM", "CLAIMS" -> ContentGenerationRequest.ContentType.CLAIM;
            case "EXAMPLE", "EXAMPLES" -> ContentGenerationRequest.ContentType.EXAMPLE;
            case "SIMULATION", "SIMULATIONS" -> ContentGenerationRequest.ContentType.SIMULATION;
            case "ANIMATION", "ANIMATIONS" -> ContentGenerationRequest.ContentType.ANIMATION;
            case "EXERCISE", "EXERCISES" -> ContentGenerationRequest.ContentType.EXERCISE;
            case "ASSESSMENT", "ASSESSMENTS" -> ContentGenerationRequest.ContentType.ASSESSMENT;
            case "LESSON", "LESSONS" -> ContentGenerationRequest.ContentType.LESSON;
            default -> throw new IllegalArgumentException("Unsupported content type: " + rawType);
        };
    }

    private <T> T awaitPromise(Promise<T> promise, Duration timeout) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> resultRef = new AtomicReference<>();
        AtomicReference<Exception> errorRef = new AtomicReference<>();

        promise.whenComplete((result, error) -> {
            resultRef.set(result);
            if (error != null) {
                errorRef.set(error);
            }
            latch.countDown();
        });

        try {
            if (!latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Timed out waiting for async content generation");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for async content generation", e);
        }

        Exception error = errorRef.get();
        if (error != null) {
            throw new RuntimeException("Async content generation failed", error);
        }

        return resultRef.get();
    }

    // =========================================================================
    // Synchronous Content Generation
    // =========================================================================

    /**
     * Generates content synchronously for immediate use.
     *
     * @param request the generation request
     * @return the generation response
     */
    public Promise<ContentGenerationResponse> generateContent(
            @NotNull ContentGenerationRequest request) {

        // Select strategy based on experiment
        ContentStrategySelector.ContentStrategy strategy =
            strategySelector.selectStrategy(
                "content-generation-strategy",
                request.learnerId()
            );

        LOG.debug("Using strategy {} for user {}",
            strategy.id(), request.learnerId());

        Timer.Sample sample = Timer.start(meterRegistry);
        AgentContext agentContext = createAgentContext(
            contentAgent.getAgentId(),
            request.learnerId(),
            null,
            Map.of("strategyId", strategy.id())
        );

        return contentAgent.executeTurn(request, agentContext)
            .map(response -> {
                if (response != null && request.learnerId() != null) {
                    double quality100 = response.qualityScore() * 100.0;
                    metricsCollector.recordContentQuality(
                        "content-generation-strategy",
                        request.learnerId(),
                        request.topic(),
                        quality100,
                        quality100,
                        quality100
                    );
                }
                return response;
            })
            .whenComplete((r, e) -> sample.stop(syncGenerationTimer));
    }

    /**
     * Generates claims for a topic.
     *
     * @param tenantId the tenant ID
     * @param topic the topic
     * @param gradeLevel the grade level
     * @param learnerId the learner ID
     * @return list of generated claims
     */
    public Promise<List<String>> generateClaims(
            @NotNull String tenantId,
            @NotNull String topic,
            int gradeLevel,
            @NotNull String learnerId) {

        ContentGenerationRequest request = new ContentGenerationRequest(
            topic,
            "GENERAL",
            String.valueOf(gradeLevel),
            ContentGenerationRequest.ContentType.CLAIM,
            learnerId,
            null,
            null,
            null,
            Map.of("tenantId", tenantId)
        );
        
        return generateContent(request)
            .map(response -> response != null ? 
                List.of(response.content()) : List.of());
    }

    /**
     * Generates examples for a claim.
     *
     * @param tenantId the tenant ID
     * @param claim the claim to illustrate
     * @param gradeLevel the grade level
     * @param learnerId the learner ID
     * @return list of generated examples
     */
    public Promise<List<String>> generateExamples(
            @NotNull String tenantId,
            @NotNull String claim,
            int gradeLevel,
            @NotNull String learnerId) {

        ContentGenerationRequest request = new ContentGenerationRequest(
            claim,
            "GENERAL",
            String.valueOf(gradeLevel),
            ContentGenerationRequest.ContentType.EXAMPLE,
            learnerId,
            null,
            null,
            null,
            Map.of("tenantId", tenantId)
        );
        
        return generateContent(request)
            .map(response -> response != null ? 
                List.of(response.content()) : List.of());
    }

    // =========================================================================
    // Asynchronous Batch Generation
    // =========================================================================

    /**
     * Submits a batch content generation job.
     *
     * @param request the batch request
     * @return the job ID for tracking
     */
    public String submitBatchGeneration(@NotNull BatchGenerationRequest request) {
        ContentJob job = ContentJob.builder()
            .type(ContentJob.JobType.BATCH_GENERATION)
            .tenantId(request.tenantId())
            .requesterId(request.requesterId())
            .payload(Map.of(
                "topics", request.topics(),
                "gradeLevel", request.gradeLevel(),
                "contentTypes", request.contentTypes()
            ))
            .priority(request.priority())
            .build();
        
        String jobId = jobQueue.submit(job);
        
        progressTracker.initializeJob(
            jobId,
            request.topics().size() * request.contentTypes().size(),
            "Batch generation for " + request.topics().size() + " topics"
        );
        
        LOG.info("Submitted batch generation job: {} ({} topics)", 
            jobId, request.topics().size());
        
        return jobId;
    }

    /**
     * Gets the progress of a batch job.
     *
     * @param jobId the job ID
     * @return the progress, or null if not found
     */
    public JobProgressTracker.JobProgress getJobProgress(@NotNull String jobId) {
        return progressTracker.getProgress(jobId);
    }

    /**
     * Cancels a batch job if possible.
     *
     * @param jobId the job ID
     * @return true if cancelled, false if not found or already completed
     */
    public boolean cancelJob(@NotNull String jobId) {
        ContentJobQueue.JobStatus status = jobQueue.getStatus(jobId);
        if (status == ContentJobQueue.JobStatus.QUEUED) {
            // Note: actual cancellation logic would need to be added to jobQueue
            LOG.info("Job {} cancellation requested", jobId);
            return true;
        }
        return false;
    }

    // =========================================================================
    // Tutoring & Learner Interaction
    // =========================================================================

    /**
     * Processes a learner action and generates an adaptive response.
     *
     * @param action the learner action
     * @return the tutoring response
     */
    public Promise<TutoringResponse> processLearnerAction(@NotNull LearnerAction action) {
        Timer.Sample sample = Timer.start(meterRegistry);
        AgentContext agentContext = createAgentContext(
            tutoringAgent.getAgentId(),
            action.learnerId(),
            null,
            Map.of()
        );

        return tutoringAgent.executeTurn(action, agentContext)
            .map(response -> {
                if (response != null) {
                    Duration timeSpent = Duration.ofMillis(action.timeSpentMs() != null ? action.timeSpentMs() : 0L);
                    boolean completed = response.responseType() == TutoringResponse.ResponseType.MASTERY_ACHIEVED
                        || response.nextAction() == TutoringResponse.NextAction.NEXT_TOPIC;
                    metricsCollector.recordEngagement(
                        "tutoring-strategy",
                        action.learnerId(),
                        action.topicId(),
                        timeSpent,
                        completed
                    );
                }
                return response;
            })
            .whenComplete((r, e) -> sample.stop(tutoringTimer));
    }

    /**
     * Generates a hint for a problem.
     *
     * @param tenantId the tenant ID
     * @param learnerId the learner ID
     * @param problemId the problem ID
     * @param hintLevel the hint level (1=subtle, 3=explicit)
     * @return the hint text
     */
    public Promise<String> generateHint(
            @NotNull String tenantId,
            @NotNull String learnerId,
            @NotNull String problemId,
            int hintLevel) {

        LearnerAction action = new LearnerAction(
            learnerId,
            problemId,
            LearnerAction.ActionType.HINT_REQUESTED,
            null,
            null,
            null,
            hintLevel,
            null,
            null,
            Map.of("tenantId", tenantId)
        );
        
        return processLearnerAction(action)
            .map(response -> response != null ? response.message() : "");
    }

    /**
     * Submits an answer for evaluation.
     *
     * @param tenantId the tenant ID
     * @param learnerId the learner ID
     * @param problemId the problem ID
     * @param answer the learner's answer
     * @return evaluation result with feedback
     */
    public Promise<AnswerEvaluation> evaluateAnswer(
            @NotNull String tenantId,
            @NotNull String learnerId,
            @NotNull String problemId,
            @NotNull String answer) {

        LearnerAction action = new LearnerAction(
            learnerId,
            problemId,
            LearnerAction.ActionType.ANSWER_SUBMITTED,
            answer,
            null,
            null,
            null,
            null,
            null,
            Map.of("tenantId", tenantId)
        );
        
        return processLearnerAction(action)
            .map(response -> {
                if (response == null) {
                    return new AnswerEvaluation(false, "Unable to evaluate answer", 0.0);
                }

                boolean correct = response.responseType() == TutoringResponse.ResponseType.CORRECT_FEEDBACK
                    || response.responseType() == TutoringResponse.ResponseType.MASTERY_ACHIEVED;
                
                return new AnswerEvaluation(
                    correct,
                    response.message(),
                    response.masteryLevel()
                );
            });
    }

    // =========================================================================
    // Knowledge Base Integration
    // =========================================================================

    /**
     * Enriches content with knowledge base information.
     *
     * @param topic the topic
     * @param content the content to enrich
     * @return enriched content with sources
     */
    public Promise<EnrichedContent> enrichContent(
            @NotNull String topic,
            @NotNull String content) {

        Promise<KnowledgeBaseService.UnifiedSearchResult> search =
            knowledgeService.search(topic, null, 3);

        Promise<KnowledgeBaseService.FactVerificationResult> verification =
            knowledgeService.verifyLearningClaim(content, null, null);

        return search.combine(verification, (searchResult, verificationResult) -> {
            List<String> sources = searchResult.results().stream()
                .map(r -> r.source() + ": " + r.title())
                .distinct()
                .toList();
            return new EnrichedContent(content, sources, verificationResult.overallConfidence());
        });
    }

    // =========================================================================
    // Experiment Management
    // =========================================================================

    /**
     * Creates a content generation experiment.
     *
     * @param experimentId the experiment ID
     * @param name the experiment name
     * @param controlStrategy the control strategy ID
     * @param treatmentStrategy the treatment strategy ID
     * @param trafficPercent percentage of traffic to include
     */
    public void createExperiment(
            @NotNull String experimentId,
            @NotNull String name,
            @NotNull String controlStrategy,
            @NotNull String treatmentStrategy,
            double trafficPercent) {
        
        ExperimentManager.Experiment experiment = ExperimentManager.Experiment.builder()
            .id(experimentId)
            .name(name)
            .trafficAllocation(trafficPercent)
            .addVariant(ExperimentManager.Variant.builder()
                .id("control")
                .name("Control")
                .weight(50.0)
                .config(Map.of("strategyId", controlStrategy))
                .build())
            .addVariant(ExperimentManager.Variant.builder()
                .id("treatment")
                .name("Treatment")
                .weight(50.0)
                .config(Map.of("strategyId", treatmentStrategy))
                .build())
            .build();
        
        experimentManager.createExperiment(experiment);
        
        LOG.info("Created experiment: {} (control={}, treatment={})", 
            experimentId, controlStrategy, treatmentStrategy);
    }

    /**
     * Gets experiment results.
     *
     * @param experimentId the experiment ID
     * @return experiment summary
     */
    public ExperimentMetricsCollector.ExperimentSummary getExperimentResults(
            @NotNull String experimentId) {
        return metricsCollector.getExperimentSummary(experimentId);
    }

    // =========================================================================
    // Service Lifecycle
    // =========================================================================

    /**
     * Starts the service and all background workers.
     */
    public void start() {
        jobQueue.start();
        LOG.info("UnifiedContentService started");
    }

    /**
     * Stops the service gracefully.
     *
     * @param timeout maximum time to wait for pending jobs
     */
    public void stop(@NotNull Duration timeout) {
        jobQueue.stop(timeout);
        progressTracker.shutdown();
        LOG.info("UnifiedContentService stopped");
    }

    /**
     * Gets service statistics.
     *
     * @return service statistics
     */
    public ServiceStats getStats() {
        ContentJobQueue.QueueStats queueStats = jobQueue.getStats();
        return new ServiceStats(
            queueStats.queuedJobs(),
            queueStats.activeJobs(),
            queueStats.completedJobs(),
            queueStats.deadLetterJobs(),
            experimentManager.listActiveExperiments().size()
        );
    }

    // =========================================================================
    // Types
    // =========================================================================

    /**
     * Service configuration.
     */
    public record ServiceConfig(
        String tenantId,
        MemoryStore memoryStore,
        ContentGenerationAgent contentAgent,
        LearnerInteractionAgent tutoringAgent,
        KnowledgeBaseService knowledgeService,
        ContentStrategySelector strategySelector,
        ExperimentManager experimentManager,
        ExperimentMetricsCollector metricsCollector,
        ContentJobQueue jobQueue,
        JobProgressTracker progressTracker,
        MeterRegistry meterRegistry
    ) {}

    /**
     * Batch generation request.
     */
    public record BatchGenerationRequest(
        String tenantId,
        String requesterId,
        List<String> topics,
        int gradeLevel,
        List<String> contentTypes,
        int priority
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String tenantId;
            private String requesterId;
            private List<String> topics = new ArrayList<>();
            private int gradeLevel = 5;
            private List<String> contentTypes = List.of("CLAIM", "EXAMPLE");
            private int priority = 5;

            public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
            public Builder requesterId(String requesterId) { this.requesterId = requesterId; return this; }
            public Builder topics(List<String> topics) { this.topics = topics; return this; }
            public Builder gradeLevel(int gradeLevel) { this.gradeLevel = gradeLevel; return this; }
            public Builder contentTypes(List<String> contentTypes) { this.contentTypes = contentTypes; return this; }
            public Builder priority(int priority) { this.priority = priority; return this; }

            public BatchGenerationRequest build() {
                return new BatchGenerationRequest(tenantId, requesterId, topics, gradeLevel, contentTypes, priority);
            }
        }
    }

    /**
     * Answer evaluation result.
     */
    public record AnswerEvaluation(
        boolean correct,
        String feedback,
        double newMasteryLevel
    ) {}

    /**
     * Enriched content with sources.
     */
    public record EnrichedContent(
        String content,
        List<String> sources,
        double verificationConfidence
    ) {}

    /**
     * Service statistics.
     */
    public record ServiceStats(
        int queuedJobs,
        int activeJobs,
        int completedJobs,
        int failedJobs,
        int activeExperiments
    ) {}
}
