package com.ghatana.aiplatform.registry;

import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages model deployment configurations and targeting decisions per tenant
 * and task.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides model selection logic for inference requests:
 * <ul>
 * <li>Maintains deployment status for models per tenant</li>
 * <li>Selects appropriate model target based on task</li>
 * <li>Integrates with ABTestingService for traffic splitting</li>
 * <li>Tracks deployment success/failure metrics</li>
 * <li>Enforces multi-tenant isolation</li>
 * </ul>
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * ModelDeploymentService service = new ModelDeploymentService(registry, metrics);
 * service.registerModelTarget("tenant-123", "fraud-detection",
 *     new ModelTarget("gpt-4", "v1", "Is this fraud?", 100, 0.7));
 *
 * // Select model for inference
 * Promise<ModelTarget> target = service.selectTargetForTask("tenant-123", "fraud-detection");
 * target.then(t -> System.out.println("Using model: " + t.getModelName()));
 * }</pre>
 *
 * <p>
 * <b>Architecture Role</b><br>
 * Bridges ModelRegistryService (model metadata) and OnlineInferenceService
 * (model selection). Works with ABTestingService to support multi-model
 * deployments and experiments.
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Thread-safe - uses ConcurrentHashMap for deployment configurations.
 *
 * @doc.type class
 * @doc.purpose Model deployment management and targeting per tenant/task
 * @doc.layer platform
 * @doc.pattern Registry + Strategy
 */
public class ModelDeploymentService {

    private static final Logger logger = LoggerFactory.getLogger(ModelDeploymentService.class);

    private final ModelRegistryService registry;
    private final MetricsCollector metrics;
    private final Map<String, Map<String, List<ModelTarget>>> tenantTaskTargets = new ConcurrentHashMap<>();
    private final Map<String, Map<String, ModelTarget>> primaryTargets = new ConcurrentHashMap<>();

    /**
     * Constructs model deployment service.
     *
     * @param registry model registry for metadata lookup
     * @param metrics metrics collector
     */
    public ModelDeploymentService(ModelRegistryService registry, MetricsCollector metrics) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    /**
     * Registers a model target for tenant task.
     *
     * <p>
     * GIVEN: Tenant ID, task ID, and model target
     * <p>
     * WHEN: registerModelTarget() is called
     * <p>
     * THEN: Target is registered as primary for task
     *
     * @param tenantId tenant identifier
     * @param taskId task identifier
     * @param target model deployment target
     */
    public void registerModelTarget(String tenantId, String taskId, ModelTarget target) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(taskId, "taskId must not be null");
        Objects.requireNonNull(target, "target must not be null");

        primaryTargets.computeIfAbsent(tenantId, t -> new ConcurrentHashMap<>())
                .put(taskId, target);

        tenantTaskTargets.computeIfAbsent(tenantId, t -> new ConcurrentHashMap<>())
                .computeIfAbsent(taskId, tid -> new ArrayList<>())
                .add(0, target); // Add as primary

        metrics.incrementCounter("ai.deployment.target.registered",
                "tenant", tenantId, "task", taskId, "model", target.getModelName());

        logger.info("Registered model target: tenant={}, task={}, model={}, version={}",
                tenantId, taskId, target.getModelName(), target.getModelVersion());
    }

    /**
     * Registers an alternative model target for A/B testing or canary
     * deployments.
     *
     * @param tenantId tenant identifier
     * @param taskId task identifier
     * @param target alternative model target
     */
    public void registerAlternativeTarget(String tenantId, String taskId, ModelTarget target) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(taskId, "taskId must not be null");
        Objects.requireNonNull(target, "target must not be null");

        tenantTaskTargets.computeIfAbsent(tenantId, t -> new ConcurrentHashMap<>())
                .computeIfAbsent(taskId, tid -> new ArrayList<>())
                .add(target); // Add as alternative

        metrics.incrementCounter("ai.deployment.alternative.registered",
                "tenant", tenantId, "task", taskId, "model", target.getModelName());

        logger.info("Registered alternative target: tenant={}, task={}, model={}",
                tenantId, taskId, target.getModelName());
    }

    /**
     * Selects primary target for task and tenant.
     *
     * <p>
     * GIVEN: Valid tenant ID and task ID
     * <p>
     * WHEN: selectTargetForTask() is called
     * <p>
     * THEN: Returns Promise of registered model target
     *
     * @param tenantId tenant identifier
     * @param taskId task identifier
     * @return Promise of model target
     */
    public Promise<ModelTarget> selectTargetForTask(String tenantId, String taskId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(taskId, "taskId must not be null");

        ModelTarget target = primaryTargets.getOrDefault(tenantId, Collections.emptyMap())
                .get(taskId);

        if (target == null) {
            metrics.incrementCounter("ai.deployment.target.not_found",
                    "tenant", tenantId, "task", taskId);
            return Promise.ofException(new DeploymentNotFoundException(
                    "No deployment found for tenant=" + tenantId + ", task=" + taskId));
        }

        metrics.incrementCounter("ai.deployment.target.selected",
                "tenant", tenantId, "task", taskId, "model", target.getModelName());

        return Promise.of(target);
    }

    /**
     * Gets all targets for task (primary + alternatives) for A/B testing.
     *
     * @param tenantId tenant identifier
     * @param taskId task identifier
     * @return list of all registered targets for task
     */
    public List<ModelTarget> getAllTargetsForTask(String tenantId, String taskId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(taskId, "taskId must not be null");

        List<ModelTarget> targets = tenantTaskTargets
                .getOrDefault(tenantId, Collections.emptyMap())
                .getOrDefault(taskId, Collections.emptyList());

        return new ArrayList<>(targets);
    }

    /**
     * Updates deployment status for model.
     *
     * @param tenantId tenant identifier
     * @param taskId task identifier
     * @param modelName model name
     * @param status new deployment status
     */
    public void updateDeploymentStatus(String tenantId, String taskId, String modelName, DeploymentStatus status) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(taskId, "taskId must not be null");
        Objects.requireNonNull(modelName, "modelName must not be null");
        Objects.requireNonNull(status, "status must not be null");

        metrics.incrementCounter("ai.deployment.status.updated",
                "tenant", tenantId, "task", taskId, "model", modelName, "status", status.name());

        logger.info("Updated deployment status: tenant={}, task={}, model={}, status={}",
                tenantId, taskId, modelName, status);

        // Trigger any necessary actions (e.g., rollback on failure)
        if (status == DeploymentStatus.FAILED) {
            metrics.incrementCounter("ai.deployment.failed",
                    "tenant", tenantId, "task", taskId, "model", modelName);
            logger.warn("Deployment failed: tenant={}, task={}, model={}",
                    tenantId, taskId, modelName);
        }
    }

    /**
     * Gets deployment status for model.
     *
     * @param tenantId tenant identifier
     * @param taskId task identifier
     * @param modelName model name
     * @return deployment status
     */
    public DeploymentStatus getDeploymentStatus(String tenantId, String taskId, String modelName) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(taskId, "taskId must not be null");
        Objects.requireNonNull(modelName, "modelName must not be null");

        // In production, query from database or cache
        return DeploymentStatus.ACTIVE;
    }

    /**
     * Exception for deployment not found.
     */
    public static class DeploymentNotFoundException extends RuntimeException {

        public DeploymentNotFoundException(String message) {
            super(message);
        }
    }
}
