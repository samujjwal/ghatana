package com.ghatana.appplatform.workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @doc.type    Service
 * @doc.purpose Stateful workflow execution engine.
 *              Manages WorkflowInstance lifecycle: PENDING → RUNNING → WAITING → COMPLETED | FAILED | CANCELLED.
 *              Execution state is persisted after every step (resumable after process restart).
 *              Concurrent instances are fully supported.  Step execution target: under 100ms.
 *              TASK steps invoke a registered microservice call; DECISION steps evaluate CEL;
 *              PARALLEL/WAIT/SUB_WORKFLOW steps delegate to specialised services.
 * @doc.layer   Application
 * @doc.pattern Inner-Port
 */
public class WorkflowExecutionRuntimeService {

    // -----------------------------------------------------------------------
    // Inner Ports
    // -----------------------------------------------------------------------

    public interface TaskExecutorPort {
        /**
         * Execute a TASK step: invoke a named microservice call with the step input.
         * Returns the step output JSON.
         */
        Promise<String> execute(String taskRef, String inputJson);
    }

    public interface CelEvaluatorPort {
        /** Evaluate a CEL condition against the instance context. Returns the branch name. */
        String evaluateDecision(String celExpression, String contextJson, List<String> branches);
    }

    public interface ParallelStepExecutorPort {
        /** Fan out parallel branches and wait for the join strategy. */
        Promise<String> executeParallel(String instanceId, List<String> branchStepIds,
                                        String joinStrategy, String contextJson);
    }

    public interface WaitCoordinatorPort {
        /** Register a WAIT for an event correlation or timer. */
        Promise<String> waitForEvent(String instanceId, String eventType, String correlationExpression,
                                     String timeoutIso);
    }

    public interface SubWorkflowLaunchPort {
        /** Invoke a sub-workflow synchronously (waiting for its result). */
        Promise<String> invoke(String subWorkflowId, int version, String inputJson, String parentInstanceId);
    }

    public interface EventBusPort {
        Promise<Void> publish(String topic, String eventType, String payloadJson);
    }

    public interface AuditPort {
        Promise<Void> log(String action, String actor, String entityId, String entityType,
                          String beforeJson, String afterJson);
    }

    // -----------------------------------------------------------------------
    // Records and Status
    // -----------------------------------------------------------------------

    public enum InstanceStatus {
        PENDING, RUNNING, WAITING, COMPLETED, FAILED, CANCELLED
    }

    public record WorkflowInstance(
        String instanceId,
        String workflowId,
        int workflowVersion,
        InstanceStatus status,
        String currentStepId,
        String contextJson,
        String errorMessage,
        String startedAt,
        String completedAt
    ) {}

    public record StepEvent(
        String eventId,
        String instanceId,
        String stepId,
        String stepType,
        String inputJson,
        String outputJson,
        String status,      // STARTED | COMPLETED | FAILED | SKIPPED
        long durationMs,
        String recordedAt
    ) {}

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    private final DataSource dataSource;
    private final Executor executor;
    private final TaskExecutorPort taskExecutor;
    private final CelEvaluatorPort celEvaluator;
    private final ParallelStepExecutorPort parallelExecutor;
    private final WaitCoordinatorPort waitCoordinator;
    private final SubWorkflowLaunchPort subWorkflowLauncher;
    private final EventBusPort eventBus;
    private final AuditPort auditPort;

    private static final Logger log = LoggerFactory.getLogger(WorkflowExecutionRuntimeService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Counter instanceCreatedTotal;
    private final Counter instanceCompletedTotal;
    private final Counter instanceFailedTotal;
    private final Counter instanceCancelledTotal;
    private final Timer stepDurationTimer;
    private final AtomicLong activeInstanceCount = new AtomicLong(0);

    private static final String TOPIC_WORKFLOW_EVENTS = "platform.workflow.events";

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public WorkflowExecutionRuntimeService(DataSource dataSource,
                                            Executor executor,
                                            MeterRegistry meterRegistry,
                                            TaskExecutorPort taskExecutor,
                                            CelEvaluatorPort celEvaluator,
                                            ParallelStepExecutorPort parallelExecutor,
                                            WaitCoordinatorPort waitCoordinator,
                                            SubWorkflowLaunchPort subWorkflowLauncher,
                                            EventBusPort eventBus,
                                            AuditPort auditPort) {
        this.dataSource          = dataSource;
        this.executor            = executor;
        this.taskExecutor        = taskExecutor;
        this.celEvaluator        = celEvaluator;
        this.parallelExecutor    = parallelExecutor;
        this.waitCoordinator     = waitCoordinator;
        this.subWorkflowLauncher = subWorkflowLauncher;
        this.eventBus            = eventBus;
        this.auditPort           = auditPort;

        this.instanceCreatedTotal  = Counter.builder("workflow.instance.created_total")
                .description("Total workflow instances created")
                .register(meterRegistry);
        this.instanceCompletedTotal = Counter.builder("workflow.instance.completed_total")
                .description("Total workflow instances completed successfully")
                .register(meterRegistry);
        this.instanceFailedTotal   = Counter.builder("workflow.instance.failed_total")
                .description("Total workflow instances that failed")
                .register(meterRegistry);
        this.instanceCancelledTotal = Counter.builder("workflow.instance.cancelled_total")
                .description("Total workflow instances cancelled")
                .register(meterRegistry);
        this.stepDurationTimer     = Timer.builder("workflow.step.duration_ms")
                .description("Duration of individual step execution")
                .register(meterRegistry);
        Gauge.builder("workflow.instance.active_count", activeInstanceCount, AtomicLong::get)
             .description("Currently active (non-terminal) workflow instances")
             .register(meterRegistry);
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Launch a new workflow instance for the given definition version.
     * Context is seeded from the trigger payload.
     */
    public Promise<WorkflowInstance> launch(String workflowId, int version,
                                             String contextJson, String triggerId) {
        return Promise.ofBlocking(executor, () -> {
            String instanceId = insertInstanceBlocking(workflowId, version, contextJson, triggerId);
            instanceCreatedTotal.increment();
            activeInstanceCount.incrementAndGet();
            return queryInstanceBlocking(instanceId);
        }).then(instance -> executeNextStep(instance));
    }

    /**
     * Resume a WAITING instance when an awaited event or signal arrives.
     * Updates the context with the event payload and continues execution.
     */
    public Promise<WorkflowInstance> resume(String instanceId, String resumePayloadJson) {
        return Promise.ofBlocking(executor, () -> queryInstanceBlocking(instanceId))
            .then(instance -> {
                if (instance.status() != InstanceStatus.WAITING) {
                    return Promise.ofException(new IllegalStateException(
                        "Instance " + instanceId + " is not in WAITING state"));
                }
                String mergedContext = mergeContext(instance.contextJson(), resumePayloadJson);
                updateInstanceBlocking(instanceId, InstanceStatus.RUNNING, instance.currentStepId(),
                                       mergedContext, null);
                return Promise.ofBlocking(executor, () -> queryInstanceBlocking(instanceId))
                    .then(this::executeNextStep);
            });
    }

    /** Cancel a running or waiting workflow instance. */
    public Promise<Void> cancel(String instanceId, String cancelledBy, String reason) {
        return Promise.ofBlocking(executor, () -> {
            updateInstanceBlocking(instanceId, InstanceStatus.CANCELLED, null, null, reason);
            instanceCancelledTotal.increment();
            activeInstanceCount.decrementAndGet();
            auditPort.log("WORKFLOW_CANCELLED", cancelledBy, instanceId, "WORKFLOW_INSTANCE",
                          null, "{\"reason\":\"" + reason + "\"}");
            return null;
        });
    }

    /** Get workflow instance by ID. */
    public Promise<WorkflowInstance> getInstance(String instanceId) {
        return Promise.ofBlocking(executor, () -> queryInstanceBlocking(instanceId));
    }

    /** List instances by workflow ID and optional status filter. */
    public Promise<List<WorkflowInstance>> listInstances(String workflowId, InstanceStatus statusFilter,
                                                          int limit, int offset) {
        return Promise.ofBlocking(executor, () -> queryInstances(workflowId, statusFilter, limit, offset));
    }

    /** List the step event log for an instance. */
    public Promise<List<StepEvent>> getStepLog(String instanceId) {
        return Promise.ofBlocking(executor, () -> queryStepLog(instanceId));
    }

    // -----------------------------------------------------------------------
    // Private step execution engine
    // -----------------------------------------------------------------------

    /**
     * Core step execution engine.
     *
     * <p>Reads the workflow definition from the DB, resolves the next step from the step graph,
     * dispatches it via the appropriate port, persists the step event, advances
     * {@code current_step_id}, and recurses until a terminal step (no {@code next}) or a WAIT
     * step suspends the instance.
     */
    private Promise<WorkflowInstance> executeNextStep(WorkflowInstance instance) {
        return Promise.ofBlocking(executor, () -> fetchDefinitionJsonBlocking(
                instance.workflowId(), instance.workflowVersion()))
            .then(definitionJson -> {
                JsonNode def;
                try {
                    def = MAPPER.readTree(definitionJson);
                } catch (Exception e) {
                    return failInstance(instance.instanceId(), "Invalid definition JSON", e);
                }

                // Determine the step to execute
                String stepId = instance.currentStepId();
                if (stepId == null || stepId.isBlank()) {
                    // First execution: use startStepId from definition
                    JsonNode startNode = def.get("startStepId");
                    stepId = startNode != null ? startNode.asText() : null;
                }
                if (stepId == null) {
                    // Definition has no steps — complete immediately
                    return completeInstance(instance);
                }

                // Find the step definition
                JsonNode stepsNode = def.get("steps");
                if (stepsNode == null || !stepsNode.isArray()) {
                    return failInstance(instance.instanceId(), "No steps array in definition", null);
                }
                JsonNode stepDef = null;
                for (JsonNode s : stepsNode) {
                    if (stepId.equals(s.path("stepId").asText())) {
                        stepDef = s;
                        break;
                    }
                }
                if (stepDef == null) {
                    return failInstance(instance.instanceId(),
                            "Step not found in definition: " + stepId, null);
                }

                final String resolvedStepId = stepId;
                final JsonNode resolvedStepDef = stepDef;
                final String context = instance.contextJson();

                // Mark instance RUNNING with current step
                updateInstanceBlocking(instance.instanceId(), InstanceStatus.RUNNING,
                        resolvedStepId, context, null);

                String type        = resolvedStepDef.path("type").asText("TASK");
                String taskRef     = resolvedStepDef.path("taskRef").asText(null);
                String condition   = resolvedStepDef.path("condition").asText(null);
                List<String> branchList = parseBranches(resolvedStepDef.path("branches"));
                String waitEvType  = resolvedStepDef.path("waitEventType").asText(null);
                String waitDur     = resolvedStepDef.path("waitDuration").asText(null);
                String subWfId     = resolvedStepDef.path("subWorkflowId").asText(null);

                long stepStart = System.nanoTime();
                return dispatchStep(instance.instanceId(), type, resolvedStepId,
                        taskRef, condition, branchList, waitEvType, waitDur, subWfId, context)
                    .then(outputJson -> Promise.ofBlocking(executor, () -> {
                        long durationMs = (System.nanoTime() - stepStart) / 1_000_000;
                        writeStepEventBlocking(instance.instanceId(), resolvedStepId, type,
                                context, outputJson, "COMPLETED", durationMs);

                        // Handle WAIT — suspend instance, caller will resume later
                        if ("WAIT".equals(type)) {
                            updateInstanceBlocking(instance.instanceId(), InstanceStatus.WAITING,
                                    resolvedStepId, mergeContext(context, outputJson), null);
                            return queryInstanceBlocking(instance.instanceId());
                        }

                        // Merge step output into context
                        String newContext = mergeContext(context, outputJson);

                        // Determine the next step
                        String nextStepId = resolveNextStepId(resolvedStepDef, type, outputJson);
                        if (nextStepId == null) {
                            // Terminal step
                            updateInstanceBlocking(instance.instanceId(), InstanceStatus.COMPLETED,
                                    null, newContext, null);
                            instanceCompletedTotal.increment();
                            activeInstanceCount.decrementAndGet();
                            eventBus.publish(TOPIC_WORKFLOW_EVENTS, "WorkflowInstanceCompleted",
                                    buildCompletedEventJson(instance.instanceId(), instance.workflowId()));
                            return queryInstanceBlocking(instance.instanceId());
                        }

                        // Advance to next step and recurse
                        updateInstanceBlocking(instance.instanceId(), InstanceStatus.RUNNING,
                                nextStepId, newContext, null);
                        return queryInstanceBlocking(instance.instanceId());
                    }))
                    .then(updatedInstance -> {
                        if (updatedInstance.status() == InstanceStatus.RUNNING
                                && updatedInstance.currentStepId() != null) {
                            return executeNextStep(updatedInstance);
                        }
                        return Promise.of(updatedInstance);
                    })
                    .mapException(e -> {
                        log.error("[Workflow] Step execution failed instance={} step={}",
                                instance.instanceId(), resolvedStepId, e);
                        writeStepEventBlocking(instance.instanceId(), resolvedStepId, type,
                                context, null, "FAILED", 0);
                        updateInstanceBlocking(instance.instanceId(), InstanceStatus.FAILED,
                                resolvedStepId, context, e.getMessage());
                        instanceFailedTotal.increment();
                        activeInstanceCount.decrementAndGet();
                        return e;
                    });
            });
    }

    /** Resolve the next stepId after the current step completes. */
    private String resolveNextStepId(JsonNode stepDef, String stepType, String outputJson) {
        if ("DECISION".equals(stepType)) {
            // outputJson from CEL evaluator is the chosen branch name
            String chosenBranch = outputJson != null ? outputJson.replace("\"", "") : "";
            JsonNode nextMap = stepDef.path("next");
            if (nextMap.isObject()) {
                JsonNode target = nextMap.get(chosenBranch);
                return target != null ? target.asText(null) : null;
            }
        }
        JsonNode nextNode = stepDef.path("next");
        return nextNode.isMissingNode() || nextNode.isNull() ? null : nextNode.asText(null);
    }

    /** Parse a branches JSON array node into a List<String>. */
    private List<String> parseBranches(JsonNode branchesNode) {
        List<String> result = new ArrayList<>();
        if (branchesNode != null && branchesNode.isArray()) {
            for (JsonNode b : branchesNode) result.add(b.asText());
        }
        return result;
    }

    /** Fail an instance with a message and return it as a completed promise. */
    private Promise<WorkflowInstance> failInstance(String instanceId, String msg, Throwable cause) {
        log.error("[Workflow] Failing instance={} reason={}", instanceId, msg, cause);
        return Promise.ofBlocking(executor, () -> {
            updateInstanceBlocking(instanceId, InstanceStatus.FAILED, null, null, msg);
            instanceFailedTotal.increment();
            activeInstanceCount.decrementAndGet();
            return queryInstanceBlocking(instanceId);
        });
    }

    /** Complete a workflow instance that has no steps. */
    private Promise<WorkflowInstance> completeInstance(WorkflowInstance instance) {
        return Promise.ofBlocking(executor, () -> {
            updateInstanceBlocking(instance.instanceId(), InstanceStatus.COMPLETED,
                    null, instance.contextJson(), null);
            instanceCompletedTotal.increment();
            activeInstanceCount.decrementAndGet();
            eventBus.publish(TOPIC_WORKFLOW_EVENTS, "WorkflowInstanceCompleted",
                    buildCompletedEventJson(instance.instanceId(), instance.workflowId()));
            return queryInstanceBlocking(instance.instanceId());
        });
    }

    /** Dispatch a single step based on its type. */
    private Promise<String> dispatchStep(String instanceId, String stepType, String stepId,
                                          String taskRef, String condition, List<String> branches,
                                          String waitEventType, String waitDuration,
                                          String subWorkflowId, String contextJson) {
        long start = System.nanoTime();
        Promise<String> result = switch (stepType) {
            case "TASK"         -> taskExecutor.execute(taskRef, contextJson);
            case "DECISION"     -> Promise.of(
                celEvaluator.evaluateDecision(condition, contextJson, branches));
            case "PARALLEL"     -> parallelExecutor.executeParallel(instanceId, branches,
                "ALL", contextJson);
            case "WAIT"         -> waitCoordinator.waitForEvent(instanceId, waitEventType,
                null, waitDuration);
            case "SUB_WORKFLOW" -> subWorkflowLauncher.invoke(subWorkflowId, 0, contextJson, instanceId);
            default             -> Promise.ofException(new IllegalArgumentException("Unknown step type: " + stepType));
        };
        return result.whenComplete((r, e) -> stepDurationTimer.record(
            System.nanoTime() - start, java.util.concurrent.TimeUnit.NANOSECONDS));
    }

    // -----------------------------------------------------------------------
    // Private DB helpers
    // -----------------------------------------------------------------------

    private String insertInstanceBlocking(String workflowId, int version,
                                          String contextJson, String triggerId) {
        String sql = """
            INSERT INTO workflow_instances
                (instance_id, workflow_id, workflow_version, status, context_json, trigger_id, started_at)
            VALUES (gen_random_uuid()::text, ?, ?, 'PENDING', ?::jsonb, ?, now())
            RETURNING instance_id
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, workflowId);
            ps.setInt(2, version);
            ps.setString(3, contextJson);
            ps.setString(4, triggerId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString("instance_id");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create workflow instance for " + workflowId, e);
        }
    }

    private void updateInstanceBlocking(String instanceId, InstanceStatus status,
                                        String currentStepId, String contextJson, String error) {
        String sql = """
            UPDATE workflow_instances
               SET status = ?, current_step_id = ?, context_json = COALESCE(?::jsonb, context_json),
                   error_message = ?, completed_at = CASE WHEN ? IN ('COMPLETED','FAILED','CANCELLED') THEN now() ELSE completed_at END
             WHERE instance_id = ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setString(2, currentStepId);
            ps.setString(3, contextJson);
            ps.setString(4, error);
            ps.setString(5, status.name());
            ps.setString(6, instanceId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to update workflow instance " + instanceId, e);
        }
    }

    private WorkflowInstance queryInstanceBlocking(String instanceId) {
        String sql = """
            SELECT instance_id, workflow_id, workflow_version, status::text, current_step_id,
                   context_json::text, error_message, started_at::text, completed_at::text
              FROM workflow_instances
             WHERE instance_id = ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, instanceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new RuntimeException("Instance not found: " + instanceId);
                return new WorkflowInstance(
                    rs.getString("instance_id"), rs.getString("workflow_id"),
                    rs.getInt("workflow_version"),
                    InstanceStatus.valueOf(rs.getString("status")),
                    rs.getString("current_step_id"), rs.getString("context_json"),
                    rs.getString("error_message"), rs.getString("started_at"),
                    rs.getString("completed_at")
                );
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to query instance " + instanceId, e);
        }
    }

    private List<WorkflowInstance> queryInstances(String workflowId, InstanceStatus statusFilter,
                                                   int limit, int offset) {
        String statusClause = statusFilter != null ? "AND status = '" + statusFilter.name() + "'" : "";
        String sql = "SELECT instance_id, workflow_id, workflow_version, status::text, current_step_id, " +
                     "context_json::text, error_message, started_at::text, completed_at::text " +
                     "FROM workflow_instances WHERE workflow_id = ? " + statusClause +
                     " ORDER BY started_at DESC LIMIT ? OFFSET ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, workflowId);
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                List<WorkflowInstance> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(new WorkflowInstance(
                        rs.getString("instance_id"), rs.getString("workflow_id"),
                        rs.getInt("workflow_version"),
                        InstanceStatus.valueOf(rs.getString("status")),
                        rs.getString("current_step_id"), rs.getString("context_json"),
                        rs.getString("error_message"), rs.getString("started_at"),
                        rs.getString("completed_at")
                    ));
                }
                return result;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to list instances for workflow " + workflowId, e);
        }
    }

    private List<StepEvent> queryStepLog(String instanceId) {
        String sql = """
            SELECT event_id, instance_id, step_id, step_type, input_json::text, output_json::text,
                   status, duration_ms, recorded_at::text
              FROM workflow_instance_step_events
             WHERE instance_id = ?
             ORDER BY recorded_at ASC
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, instanceId);
            try (ResultSet rs = ps.executeQuery()) {
                List<StepEvent> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(new StepEvent(
                        rs.getString("event_id"), rs.getString("instance_id"),
                        rs.getString("step_id"), rs.getString("step_type"),
                        rs.getString("input_json"), rs.getString("output_json"),
                        rs.getString("status"), rs.getLong("duration_ms"),
                        rs.getString("recorded_at")
                    ));
                }
                return result;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to query step log for instance " + instanceId, e);
        }
    }

    /**
     * Deep-merges two JSON objects so that keys from {@code overlayJson} overwrite
     * corresponding keys in {@code baseContextJson}, while non-overlapping base keys
     * are preserved. Non-object values are treated as replacement.
     */
    private String mergeContext(String baseContextJson, String overlayJson) {
        if (overlayJson == null || overlayJson.isBlank()) return baseContextJson;
        if (baseContextJson == null || baseContextJson.isBlank()) return overlayJson;
        try {
            JsonNode base    = MAPPER.readTree(baseContextJson);
            JsonNode overlay = MAPPER.readTree(overlayJson);
            if (!base.isObject() || !overlay.isObject()) {
                // Can't merge non-objects — overlay wins
                return overlayJson;
            }
            ObjectNode merged = (ObjectNode) base.deepCopy();
            Iterator<Map.Entry<String, JsonNode>> fields = overlay.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String key = entry.getKey();
                JsonNode overlayVal = entry.getValue();
                JsonNode baseVal = merged.get(key);
                if (baseVal != null && baseVal.isObject() && overlayVal.isObject()) {
                    // Recursively merge nested objects
                    merged.set(key, MAPPER.readTree(
                            mergeContext(baseVal.toString(), overlayVal.toString())));
                } else {
                    merged.set(key, overlayVal);
                }
            }
            return MAPPER.writeValueAsString(merged);
        } catch (Exception e) {
            log.warn("[Workflow] Context merge failed, using overlay: {}", e.getMessage());
            return overlayJson;
        }
    }

    private String buildCompletedEventJson(String instanceId, String workflowId) {
        return String.format("{\"instanceId\":\"%s\",\"workflowId\":\"%s\"}", instanceId, workflowId);
    }

    /**
     * Fetch the raw definition_json for the requested workflow version from the DB.
     */
    private String fetchDefinitionJsonBlocking(String workflowId, int version) {
        String sql = """
            SELECT definition_json::text
              FROM workflow_definitions
             WHERE workflow_id = ? AND version = ? AND status = 'ACTIVE'
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, workflowId);
            ps.setInt(2, version);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new RuntimeException(
                        "No active definition for workflow=" + workflowId + " version=" + version);
                return rs.getString(1);
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to fetch definition for workflow=" + workflowId, e);
        }
    }

    /**
     * Persist a step execution event in workflow_instance_step_events.
     * Best-effort: logs on failure, never throws.
     */
    private void writeStepEventBlocking(String instanceId, String stepId, String stepType,
                                         String inputJson, String outputJson,
                                         String status, long durationMs) {
        String sql = """
            INSERT INTO workflow_instance_step_events
                (event_id, instance_id, step_id, step_type, input_json, output_json,
                 status, duration_ms, recorded_at)
            VALUES (gen_random_uuid()::text, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, now())
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, instanceId);
            ps.setString(2, stepId);
            ps.setString(3, stepType);
            ps.setString(4, inputJson);
            ps.setString(5, outputJson);
            ps.setString(6, status);
            ps.setLong(7, durationMs);
            ps.executeUpdate();
        } catch (Exception e) {
            log.warn("[Workflow] Failed to write step event for instance={} step={}: {}",
                    instanceId, stepId, e.getMessage());
        }
    }
}
