package com.ghatana.aiplatform.registry;

import com.ghatana.platform.observability.MetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A/B testing service for multi-model deployments and traffic splitting.
 *
 * <p>
 * <b>Purpose</b><br>
 * Manages experimental traffic allocation across model variants:
 * <ul>
 * <li>Traffic split policies (90/10, 50/50, canary, shadow)</li>
 * <li>Per-tenant experiment definitions</li>
 * <li>Deterministic assignment using request context</li>
 * <li>Metrics attribution for experiment analysis</li>
 * <li>Shadow mode (test without affecting production)</li>
 * </ul>
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * ABTestingService abTesting = new ABTestingService(metrics);
 *
 * // Create experiment
 * ABTestingService.Experiment exp = new ABTestingService.Experiment(
 *     "fraud-exp-001", "fraud-detection",
 *     "90/10", // traffic split
 *     "gpt-4-baseline", "gpt-35-turbo-variant");
 * abTesting.registerExperiment("tenant-123", exp);
 *
 * // Assign user to experiment variant
 * String variant = abTesting.assignVariant("tenant-123", "fraud-detection", "user-456");
 * // Returns "baseline" (90% chance) or "variant" (10% chance)
 * }</pre>
 *
 * <p>
 * <b>Architecture Role</b><br>
 * Bridges ModelDeploymentService and OnlineInferenceService for controlled
 * model rollouts. Works with QualityMonitor for metric attribution and
 * experiment analysis.
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Thread-safe - uses ConcurrentHashMap for experiment registry.
 *
 * @doc.type class
 * @doc.purpose A/B testing and traffic splitting for multi-model experiments
 * @doc.layer platform
 * @doc.pattern Strategy + Registry
 */
public class ABTestingService {

    private static final Logger logger = LoggerFactory.getLogger(ABTestingService.class);

    private final Map<String, Map<String, Experiment>> tenantExperiments = new ConcurrentHashMap<>();
    private final MetricsCollector metrics;
    private final Random random = ThreadLocalRandom.current();

    /**
     * Constructs A/B testing service.
     *
     * @param metrics metrics collector
     */
    public ABTestingService(MetricsCollector metrics) {
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    /**
     * Registers experiment for task and tenant.
     *
     * <p>
     * GIVEN: Tenant ID, task ID, and experiment configuration
     * <p>
     * WHEN: registerExperiment() is called
     * <p>
     * THEN: Experiment becomes active for traffic splitting
     *
     * @param tenantId tenant identifier
     * @param experiment experiment configuration
     */
    public void registerExperiment(String tenantId, Experiment experiment) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(experiment, "experiment must not be null");

        tenantExperiments.computeIfAbsent(tenantId, t -> new ConcurrentHashMap<>())
                .put(experiment.getTaskId(), experiment);

        metrics.incrementCounter("ab.experiment.registered",
                "tenant", tenantId, "task", experiment.getTaskId(), "experiment", experiment.getId());

        logger.info("Registered A/B test: tenant={}, task={}, experiment={}, split={}",
                tenantId, experiment.getTaskId(), experiment.getId(), experiment.getSplit());
    }

    /**
     * Assigns user/entity to experiment variant.
     *
     * <p>
     * GIVEN: Valid tenant, task, and entity ID
     * <p>
     * WHEN: assignVariant() is called
     * <p>
     * THEN: Returns deterministic variant assignment ("baseline" or "variant")
     *
     * @param tenantId tenant identifier
     * @param taskId task identifier
     * @param entityId entity identifier (user, request, etc)
     * @return assigned variant ("baseline" or "variant")
     */
    public String assignVariant(String tenantId, String taskId, String entityId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(taskId, "taskId must not be null");
        Objects.requireNonNull(entityId, "entityId must not be null");

        Experiment experiment = tenantExperiments
                .getOrDefault(tenantId, Collections.emptyMap())
                .get(taskId);

        // If no experiment, assign to baseline
        if (experiment == null) {
            return "baseline";
        }

        // Deterministic assignment based on entity hash
        int hash = entityId.hashCode();
        int percentage = Math.abs(hash % 100);

        String variant = experiment.shouldAssignToVariant(percentage) ? "variant" : "baseline";

        metrics.incrementCounter("ab.assignment",
                "tenant", tenantId, "task", taskId, "variant", variant);

        return variant;
    }

    /**
     * Gets experiment for task.
     *
     * @param tenantId tenant identifier
     * @param taskId task identifier
     * @return experiment or null if none active
     */
    public Experiment getExperiment(String tenantId, String taskId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(taskId, "taskId must not be null");

        return tenantExperiments
                .getOrDefault(tenantId, Collections.emptyMap())
                .get(taskId);
    }

    /**
     * Ends experiment and returns to baseline.
     *
     * @param tenantId tenant identifier
     * @param taskId task identifier
     */
    public void endExperiment(String tenantId, String taskId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(taskId, "taskId must not be null");

        Experiment removed = tenantExperiments
                .getOrDefault(tenantId, Collections.emptyMap())
                .remove(taskId);

        if (removed != null) {
            metrics.incrementCounter("ab.experiment.ended",
                    "tenant", tenantId, "task", taskId, "experiment", removed.getId());
            logger.info("Ended A/B test: tenant={}, task={}, experiment={}", tenantId, taskId, removed.getId());
        }
    }

    /**
     * A/B test experiment definition.
     */
    public static class Experiment {

        private final String id;
        private final String taskId;
        private final String split;  // "90/10", "50/50", "10/90", "canary", "shadow"
        private final String baselineModel;
        private final String variantModel;
        private final long startTime;
        private final boolean isShadowMode;

        /**
         * Constructs experiment.
         *
         * @param id experiment identifier
         * @param taskId task identifier
         * @param split traffic split (e.g., "90/10")
         * @param baselineModel baseline model name
         * @param variantModel variant model name
         */
        public Experiment(String id, String taskId, String split, String baselineModel, String variantModel) {
            this(id, taskId, split, baselineModel, variantModel, false);
        }

        /**
         * Constructs experiment with shadow mode option.
         *
         * @param id experiment identifier
         * @param taskId task identifier
         * @param split traffic split
         * @param baselineModel baseline model
         * @param variantModel variant model
         * @param isShadowMode if true, variant runs in parallel without
         * affecting user
         */
        public Experiment(String id, String taskId, String split, String baselineModel,
                String variantModel, boolean isShadowMode) {
            this.id = Objects.requireNonNull(id);
            this.taskId = Objects.requireNonNull(taskId);
            this.split = Objects.requireNonNull(split);
            this.baselineModel = Objects.requireNonNull(baselineModel);
            this.variantModel = Objects.requireNonNull(variantModel);
            this.startTime = System.currentTimeMillis();
            this.isShadowMode = isShadowMode;
        }

        /**
         * Determines if percentage should be assigned to variant.
         *
         * @param percentage 0-99 percentage value
         * @return true if variant, false if baseline
         */
        public boolean shouldAssignToVariant(int percentage) {
            if ("shadow".equals(split)) {
                // Shadow: all traffic to baseline, variant runs parallel
                return false;
            }

            // Parse split percentage
            String[] parts = split.split("/");
            if (parts.length == 2) {
                try {
                    int variantPercentage = Integer.parseInt(parts[1]);
                    return percentage < variantPercentage;
                } catch (NumberFormatException e) {
                    return false;
                }
            }

            return false;
        }

        public String getId() {
            return id;
        }

        public String getTaskId() {
            return taskId;
        }

        public String getSplit() {
            return split;
        }

        public String getBaselineModel() {
            return baselineModel;
        }

        public String getVariantModel() {
            return variantModel;
        }

        public long getStartTime() {
            return startTime;
        }

        public boolean isShadowMode() {
            return isShadowMode;
        }

        /**
         * Gets duration of experiment in milliseconds.
         */
        public long getDurationMs() {
            return System.currentTimeMillis() - startTime;
        }
    }
}
