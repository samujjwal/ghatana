package com.ghatana.aiplatform.serving;

import com.ghatana.aiplatform.featurestore.FeatureStoreService;
import com.ghatana.aiplatform.gateway.LLMGatewayService;
import com.ghatana.aiplatform.registry.ModelDeploymentService;
import com.ghatana.aiplatform.registry.ModelTarget;
import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.core.utils.Pair;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Online inference service for real-time model predictions with feature
 * fetching and routing.
 *
 * <p>
 * <b>Purpose</b><br>
 * Serves low-latency predictions for production use cases:
 * <ul>
 * <li>Fetches features from feature store</li>
 * <li>Routes to appropriate model target (A/B tested)</li>
 * <li>Calls LLMGatewayService or task-specific adapters</li>
 * <li>Records prediction outcomes for monitoring</li>
 * <li>Enforces tenant isolation and rate limits</li>
 * </ul>
 *
 * <p>
 * <b>Performance Targets</b><br>
 * - p95 end-to-end latency: <50ms (excluding external LLM calls) - Cache hit
 * rate: ≥70% on features - Throughput: 1k+ RPS per instance
 *
 * <p>
 * <b>Usage</b><br>
 * 
 * <pre>{@code
 * OnlineInferenceService service = new OnlineInferenceService(
 *         gateway, deploymentService, featureStore, metrics);
 *
 * // Inference request
 * InferenceRequest request = new InferenceRequest("tenant-123", "fraud-detection",
 *         Map.of("transaction_id", "tx-456", "user_id", "usr-789"));
 * Promise<InferenceResult> result = service.infer(request);
 *
 * result.then(prediction -> {
 *     System.out.println("Score: " + prediction.getScore());
 *     System.out.println("Latency: " + prediction.getLatencyMs() + "ms");
 * });
 * }</pre>
 *
 * <p>
 * <b>Architecture Role</b><br>
 * Core serving component orchestrating feature fetching, model selection, and
 * prediction. Exposed via InferenceHttpRoutes for REST/RPC access. Integrates
 * with ModelDeploymentService for targeting decisions and LLMGatewayService for
 * provider routing.
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Thread-safe - all operations are asynchronous via ActiveJ Promise.
 *
 * @doc.type class
 * @doc.purpose Real-time model inference with feature fetching and routing
 * @doc.layer platform
 * @doc.pattern Service Orchestrator
 */
public class OnlineInferenceService {

    private static final Logger logger = LoggerFactory.getLogger(OnlineInferenceService.class);

    private final LLMGatewayService gateway;
    private final ModelDeploymentService deploymentService;
    private final FeatureStoreService featureStore;
    private final MetricsCollector metrics;

    /**
     * Constructs online inference service.
     *
     * @param gateway           LLM gateway for provider routing
     * @param deploymentService model deployment and targeting
     * @param featureStore      feature fetching
     * @param metrics           metrics collector
     */
    public OnlineInferenceService(
            LLMGatewayService gateway,
            ModelDeploymentService deploymentService,
            FeatureStoreService featureStore,
            MetricsCollector metrics) {
        this.gateway = Objects.requireNonNull(gateway, "gateway must not be null");
        this.deploymentService = Objects.requireNonNull(deploymentService, "deploymentService must not be null");
        this.featureStore = Objects.requireNonNull(featureStore, "featureStore must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    /**
     * Performs inference for request.
     *
     * <p>
     * GIVEN: Valid inference request with tenant, task, entity
     * <p>
     * WHEN: infer() is called
     * <p>
     * THEN: Returns prediction with model target, score, latency metadata
     *
     * @param request inference request
     * @return Promise of inference result
     */
    public Promise<InferenceResult> infer(InferenceRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        long startTime = System.nanoTime();
        String tenantId = request.getTenantId();
        String taskId = request.getTaskId();

        return Promise.complete()
                // Step 1: Get model target for task
                .then(ignored -> {
                    Promise<ModelTarget> targetPromise = deploymentService.selectTargetForTask(
                            tenantId, taskId);
                    return targetPromise.then(target -> {
                        // Emit targeting decision
                        metrics.incrementCounter("ai.serving.target.selected",
                                "tenant", tenantId, "task", taskId, "model", target.getModelName());
                        return Promise.of(target);
                    });
                })
                // Step 2: Fetch features for entity
                .then(target -> {
                    Map<String, Double> features = featureStore.getFeatures(
                            tenantId, request.getEntityId(), request.getFeatureNames());
                    return Promise.of(new Pair<>(target, features));
                })
                // Step 3: Build completion request
                .then(pair -> {
                    ModelTarget target = pair.getFirst();
                    Map<String, Double> features = pair.getSecond();

                    String prompt = buildPrompt(request, target, features);
                    // Note: CompletionRequest.Builder doesn't have temperature() method
                    // Temperature is set via constructor field with default 1.0
                    CompletionRequest completionRequest = CompletionRequest.builder()
                            .prompt(prompt)
                            .maxTokens(target.getMaxTokens())
                            .build();

                    return Promise.of(new Triple<>(target, features, completionRequest));
                })
                // Step 4: Call gateway for inference
                .then(triple -> {
                    ModelTarget target = triple.getFirst();
                    Map<String, Double> features = triple.getSecond();
                    CompletionRequest completionRequest = triple.getThird();

                    return gateway.generateCompletion(tenantId, completionRequest)
                            .then(result -> Promise.of(new Triple<>(target, features, result)));
                })
                // Step 5: Parse and record result
                .then(triple -> {
                    ModelTarget target = triple.getFirst();
                    Map<String, Double> features = triple.getSecond();
                    CompletionResult completionResult = triple.getThird();

                    long duration = (System.nanoTime() - startTime) / 1_000_000;
                    double score = parseScore(completionResult);

                    // Record metrics
                    metrics.incrementCounter("ai.serving.inference.count",
                            "tenant", tenantId, "task", taskId, "model", target.getModelName());
                    metrics.recordTimer("ai.serving.latency", duration,
                            "tenant", tenantId, "task", taskId);

                    InferenceResult inferenceResult = new InferenceResult(
                            tenantId, taskId, target.getModelName(),
                            score, duration, completionResult.getTokensUsed(),
                            features);

                    logger.info("Inference completed: tenant={}, task={}, model={}, latency={}ms",
                            tenantId, taskId, target.getModelName(), duration);

                    return Promise.of(inferenceResult);
                })
                .whenException(error -> {
                    long duration = (System.nanoTime() - startTime) / 1_000_000;
                    metrics.incrementCounter("ai.serving.errors",
                            "tenant", tenantId, "task", taskId);
                    logger.error("Inference failed: tenant={}, task={}, duration={}ms, error={}",
                            tenantId, taskId, duration, error.getMessage());
                });
    }

    /**
     * Builds prompt from request, target config, and features.
     */
    private String buildPrompt(InferenceRequest request, ModelTarget target, Map<String, Double> features) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Task: ").append(request.getTaskId()).append("\n");
        prompt.append("Entity: ").append(request.getEntityId()).append("\n");
        prompt.append("Features: ").append(features).append("\n");

        if (request.getContext() != null) {
            prompt.append("Context: ").append(request.getContext()).append("\n");
        }

        prompt.append("Prediction template: ").append(target.getPromptTemplate());

        return prompt.toString();
    }

    /**
     * Parses score from completion result.
     */
    private double parseScore(CompletionResult result) {
        String output = result.getText();
        try {
            // Simple extraction - in production, use structured parsing
            if (output.contains("score:")) {
                String scoreStr = output.split("score:")[1].trim().split("[^0-9.]")[0];
                return Double.parseDouble(scoreStr);
            }
            // Default to 0.5 if unable to parse
            return 0.5;
        } catch (Exception e) {
            logger.warn("Unable to parse score from output: {}", output);
            return 0.5;
        }
    }

    /**
     * Inference request with task and entity.
     */
    public static class InferenceRequest {

        private final String tenantId;
        private final String taskId;
        private final String entityId;
        private final List<String> featureNames;
        private final String context;

        public InferenceRequest(String tenantId, String taskId, String entityId) {
            this(tenantId, taskId, entityId, Collections.emptyList(), null);
        }

        public InferenceRequest(String tenantId, String taskId, String entityId,
                List<String> featureNames, String context) {
            this.tenantId = Objects.requireNonNull(tenantId);
            this.taskId = Objects.requireNonNull(taskId);
            this.entityId = Objects.requireNonNull(entityId);
            this.featureNames = featureNames != null ? featureNames : Collections.emptyList();
            this.context = context;
        }

        public String getTenantId() {
            return tenantId;
        }

        public String getTaskId() {
            return taskId;
        }

        public String getEntityId() {
            return entityId;
        }

        public List<String> getFeatureNames() {
            return featureNames;
        }

        public String getContext() {
            return context;
        }
    }

    /**
     * Inference result with prediction and metadata.
     */
    public static class InferenceResult {

        private final String tenantId;
        private final String taskId;
        private final String modelName;
        private final double score;
        private final long latencyMs;
        private final int tokensUsed;
        private final Map<String, Double> features;

        public InferenceResult(String tenantId, String taskId, String modelName,
                double score, long latencyMs, int tokensUsed,
                Map<String, Double> features) {
            this.tenantId = tenantId;
            this.taskId = taskId;
            this.modelName = modelName;
            this.score = score;
            this.latencyMs = latencyMs;
            this.tokensUsed = tokensUsed;
            this.features = features;
        }

        public String getTenantId() {
            return tenantId;
        }

        public String getTaskId() {
            return taskId;
        }

        public String getModelName() {
            return modelName;
        }

        public double getScore() {
            return score;
        }

        public long getLatencyMs() {
            return latencyMs;
        }

        public int getTokensUsed() {
            return tokensUsed;
        }

        public Map<String, Double> getFeatures() {
            return features;
        }
    }

    /**
     * Helper for triple in Promise chain.
     */
    private static class Triple<A, B, C> {

        final A first;
        final B second;
        final C third;

        Triple(A first, B second, C third) {
            this.first = first;
            this.second = second;
            this.third = third;
        }

        A getFirst() {
            return first;
        }

        B getSecond() {
            return second;
        }

        C getThird() {
            return third;
        }
    }
}
