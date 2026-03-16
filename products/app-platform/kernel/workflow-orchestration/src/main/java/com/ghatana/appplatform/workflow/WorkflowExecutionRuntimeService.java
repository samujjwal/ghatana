package com.ghatana.appplatform.workflow;

import com.ghatana.platform.audit.AuditBusPort;
import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.platform.workflow.WorkflowRun;
import com.ghatana.platform.workflow.WorkflowRunStatus;
import com.ghatana.platform.workflow.WorkflowStateStore;
import com.ghatana.platform.workflow.runtime.DurableWorkflowRuntime;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.json.PlatformObjectMapper;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
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

    private final DurableWorkflowRuntime durableRuntime;
    private final WorkflowStateStore stateStore;
    private final Executor executor;
    private final AuditBusPort auditPort;

    private static final Logger log = LoggerFactory.getLogger(WorkflowExecutionRuntimeService.class);
    private static final ObjectMapper MAPPER = PlatformObjectMapper.instance();

    private final Counter instanceCreatedTotal;
    private final Counter instanceCompletedTotal;
    private final Counter instanceFailedTotal;
    private final Counter instanceCancelledTotal;
    private final Timer stepDurationTimer;
    private final AtomicLong activeInstanceCount = new AtomicLong(0);

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public WorkflowExecutionRuntimeService(DurableWorkflowRuntime durableRuntime,
                                            WorkflowStateStore stateStore,
                                            Executor executor,
                                            MeterRegistry meterRegistry,
                                            AuditBusPort auditPort) {
        this.durableRuntime      = durableRuntime;
        this.stateStore          = stateStore;
        this.executor            = executor;
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
        Map<String, Object> initialContext = parseContext(contextJson);
        return durableRuntime.start(workflowId, "default", triggerId != null ? triggerId : "", initialContext)
            .map(run -> {
                instanceCreatedTotal.increment();
                activeInstanceCount.incrementAndGet();
                if (run.status() == WorkflowRunStatus.COMPLETED) {
                    instanceCompletedTotal.increment();
                    activeInstanceCount.decrementAndGet();
                } else if (run.status() == WorkflowRunStatus.FAILED) {
                    instanceFailedTotal.increment();
                    activeInstanceCount.decrementAndGet();
                }
                return toAppInstance(run);
            });
    }

    /**
     * Resume a WAITING instance when an awaited event or signal arrives.
     * Updates the context with the event payload and continues execution.
     */
    public Promise<WorkflowInstance> resume(String instanceId, String resumePayloadJson) {
        Map<String, Object> signal = parseContext(resumePayloadJson);
        return durableRuntime.signal(instanceId, signal)
            .map(run -> {
                if (run.status() == WorkflowRunStatus.COMPLETED) {
                    instanceCompletedTotal.increment();
                    activeInstanceCount.decrementAndGet();
                } else if (run.status() == WorkflowRunStatus.FAILED) {
                    instanceFailedTotal.increment();
                    activeInstanceCount.decrementAndGet();
                }
                return toAppInstance(run);
            });
    }

    /** Cancel a running or waiting workflow instance. */
    public Promise<Void> cancel(String instanceId, String cancelledBy, String reason) {
        return stateStore.findByRunId(instanceId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.ofException(new RuntimeException("Instance not found: " + instanceId));
                }
                WorkflowRun run = opt.get();
                WorkflowRun cancelled = run.withStatus(WorkflowRunStatus.CANCELLED)
                    .withError(reason);
                return stateStore.save(cancelled).map(v -> {
                    instanceCancelledTotal.increment();
                    activeInstanceCount.decrementAndGet();
                    auditPort.emit(AuditEvent.builder()
                        .eventType("WORKFLOW_CANCELLED").principal(cancelledBy)
                        .resourceId(instanceId).resourceType("WORKFLOW_INSTANCE")
                        .details(Map.of("reason", reason != null ? reason : ""))
                        .build());
                    return (Void) null;
                });
            });
    }

    /** Get workflow instance by ID. */
    public Promise<WorkflowInstance> getInstance(String instanceId) {
        return stateStore.findByRunId(instanceId)
            .map(opt -> opt.map(this::toAppInstance)
                .orElseThrow(() -> new RuntimeException("Instance not found: " + instanceId)));
    }

    /** List instances by workflow ID and optional status filter. */
    public Promise<List<WorkflowInstance>> listInstances(String workflowId, InstanceStatus statusFilter,
                                                          int limit, int offset) {
        return stateStore.findByWorkflowId(workflowId)
            .map(runs -> runs.stream()
                .filter(r -> statusFilter == null || toAppStatus(r.status()) == statusFilter)
                .skip(offset)
                .limit(limit)
                .map(this::toAppInstance)
                .toList());
    }

    /** List the step event log for an instance (via lifecycle history from run). */
    public Promise<List<StepEvent>> getStepLog(String instanceId) {
        return stateStore.findByRunId(instanceId)
            .map(opt -> opt.map(run -> run.history().stream()
                .map(e -> new StepEvent(
                    e.runId() + "-" + e.phase().name(),
                    e.runId(), e.stepId() != null ? e.stepId() : "",
                    e.phase().name(), null, null,
                    e.phase().name(), 0, e.timestamp().toString()))
                .toList())
            .orElse(List.of()));
    }

    // -----------------------------------------------------------------------
    // Private helpers — model conversion
    // -----------------------------------------------------------------------

    private WorkflowInstance toAppInstance(WorkflowRun run) {
        String contextJson;
        try {
            contextJson = MAPPER.writeValueAsString(run.variables());
        } catch (Exception e) {
            contextJson = "{}";
        }
        return new WorkflowInstance(
            run.runId(), run.workflowId(), 0,
            toAppStatus(run.status()), run.currentStepId(),
            contextJson, run.errorMessage(),
            run.startedAt().toString(),
            run.completedAt() != null ? run.completedAt().toString() : null);
    }

    private InstanceStatus toAppStatus(WorkflowRunStatus platformStatus) {
        return switch (platformStatus) {
            case PENDING      -> InstanceStatus.PENDING;
            case RUNNING      -> InstanceStatus.RUNNING;
            case WAITING      -> InstanceStatus.WAITING;
            case COMPLETED    -> InstanceStatus.COMPLETED;
            case FAILED       -> InstanceStatus.FAILED;
            case CANCELLED    -> InstanceStatus.CANCELLED;
            case COMPENSATING, COMPENSATED -> InstanceStatus.FAILED;
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseContext(String contextJson) {
        if (contextJson == null || contextJson.isBlank()) return new HashMap<>();
        try {
            return MAPPER.readValue(contextJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("[Workflow] Failed to parse context JSON: {}", e.getMessage());
            return new HashMap<>();
        }
    }
}
