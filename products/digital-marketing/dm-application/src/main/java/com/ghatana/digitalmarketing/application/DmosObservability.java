package com.ghatana.digitalmarketing.application;

import com.ghatana.platform.observability.Metrics;
import com.ghatana.platform.observability.TracingManager;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * DMOS product-level observability service.
 *
 * <p>Provides DMOS-specific metrics and tracing abstractions wrapping the platform
 * observability module. Instruments critical flows: API handlers, command execution,
 * workflow steps, connector calls, and repository operations.</p>
 *
 * <p>P2-1: Enhanced with per-flow metrics, startup readiness signals, and comprehensive
 * span definitions for key flows.</p>
 *
 * @doc.type class
 * @doc.purpose DMOS product observability service (DMOS-P1-011, P2-1)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class DmosObservability {

    private static final Logger LOG = LoggerFactory.getLogger(DmosObservability.class);

    private final Metrics metrics;
    private final TracingManager tracingManager;

    // Metrics counters
    private Counter commandSuccessCounter;
    private Counter commandFailureCounter;
    private Counter connectorFailureCounter;
    private Counter dlqCounter;
    private Counter apiRequestCounter;
    private Counter approvalRequestCounter;
    private Counter notificationDispatchCounter;

    // Metrics timers
    private Timer apiDurationTimer;
    private Timer commandDurationTimer;
    private Timer workflowDurationTimer;
    private Timer connectorDurationTimer;
    private Timer approvalLatencyTimer;
    private Timer notificationDeliveryTimer;

    // P2-1: Startup readiness gauge
    private boolean startupComplete = false;
    private long startupStartTime;

    public DmosObservability(Metrics metrics, TracingManager tracingManager) {
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
        this.tracingManager = Objects.requireNonNull(tracingManager, "tracingManager must not be null");
        initializeMetrics();
    }

    private void initializeMetrics() {
        // P2-1: Enhanced metrics with proper tags
        this.commandSuccessCounter = metrics.counter("dmos.command.success", "type", "command");
        this.commandFailureCounter = metrics.counter("dmos.command.failure", "type", "command");
        this.connectorFailureCounter = metrics.counter("dmos.connector.failure", "type", "connector");
        this.dlqCounter = metrics.counter("dmos.dlq.count", "type", "dlq");
        this.apiRequestCounter = metrics.counter("dmos.api.requests", "type", "api");
        this.approvalRequestCounter = metrics.counter("dmos.approval.requests", "type", "approval");
        this.notificationDispatchCounter = metrics.counter("dmos.notification.dispatch", "type", "notification");

        this.apiDurationTimer = metrics.timer("dmos.api.duration", "type", "api");
        this.commandDurationTimer = metrics.timer("dmos.command.duration", "type", "command");
        this.workflowDurationTimer = metrics.timer("dmos.workflow.duration", "type", "workflow");
        this.connectorDurationTimer = metrics.timer("dmos.connector.duration", "type", "connector");
        this.approvalLatencyTimer = metrics.timer("dmos.approval.latency", "type", "approval");
        this.notificationDeliveryTimer = metrics.timer("dmos.notification.delivery", "type", "notification");

        // P2-1: Track startup time
        this.startupStartTime = System.currentTimeMillis();
    }

    /**
     * Records a successful command execution.
     */
    public void recordCommandSuccess(String commandType) {
        commandSuccessCounter.increment();
        LOG.debug("[DMOS-O11Y] Command success: {}", commandType);
    }

    /**
     * Records a failed command execution.
     */
    public void recordCommandFailure(String commandType, String failureReason) {
        commandFailureCounter.increment();
        LOG.warn("[DMOS-O11Y] Command failure: type={} reason={}", commandType, failureReason);
    }

    /**
     * Records a connector failure.
     */
    public void recordConnectorFailure(String connectorType, String failureReason) {
        connectorFailureCounter.increment();
        LOG.warn("[DMOS-O11Y] Connector failure: type={} reason={}", connectorType, failureReason);
    }

    /**
     * Records a DLQ entry.
     */
    public void recordDlqEntry(String entityType, String reason) {
        dlqCounter.increment();
        LOG.warn("[DMOS-O11Y] DLQ entry: entity={} reason={}", entityType, reason);
    }

    /**
     * Records API request duration.
     */
    public void recordApiDuration(String endpoint, long durationMillis) {
        apiDurationTimer.record(durationMillis, TimeUnit.MILLISECONDS);
        LOG.debug("[DMOS-O11Y] API duration: endpoint={} duration={}ms", endpoint, durationMillis);
    }

    /**
     * Records command execution duration.
     */
    public void recordCommandDuration(String commandType, long durationMillis) {
        commandDurationTimer.record(durationMillis, TimeUnit.MILLISECONDS);
        LOG.debug("[DMOS-O11Y] Command duration: type={} duration={}ms", commandType, durationMillis);
    }

    /**
     * Records workflow execution duration.
     */
    public void recordWorkflowDuration(String workflowName, long durationMillis) {
        workflowDurationTimer.record(durationMillis, TimeUnit.MILLISECONDS);
        LOG.debug("[DMOS-O11Y] Workflow duration: name={} duration={}ms", workflowName, durationMillis);
    }

    /**
     * Records connector call duration.
     */
    public void recordConnectorDuration(String connectorType, String operation, long durationMillis) {
        connectorDurationTimer.record(durationMillis, TimeUnit.MILLISECONDS);
        LOG.debug("[DMOS-O11Y] Connector duration: type={} op={} duration={}ms", connectorType, operation, durationMillis);
    }

    /**
     * Records approval request latency.
     */
    public void recordApprovalLatency(String targetType, long durationMillis) {
        approvalLatencyTimer.record(durationMillis, TimeUnit.MILLISECONDS);
        LOG.debug("[DMOS-O11Y] Approval latency: target={} duration={}ms", targetType, durationMillis);
    }

    /**
     * Creates a span for the given operation name.
     */
    public Span createSpan(String operationName) {
        Tracer tracer = tracingManager.getProvider("dmos").getTracer();
        return tracer.spanBuilder(operationName).startSpan();
    }

    /**
     * Creates a span with attributes.
     */
    public Span createSpan(String operationName, String key, String value) {
        Tracer tracer = tracingManager.getProvider("dmos").getTracer();
        return tracer.spanBuilder(operationName)
            .setAttribute(key, value)
            .startSpan();
    }

    // P2-1: Per-flow metrics and spans

    /**
     * Records an API request (per-flow metric).
     */
    public void recordApiRequest(String endpoint, String method) {
        apiRequestCounter.increment();
        LOG.debug("[DMOS-O11Y] API request: {} {}", method, endpoint);
    }

    /**
     * Records an approval request (per-flow metric).
     */
    public void recordApprovalRequest(String targetType) {
        approvalRequestCounter.increment();
        LOG.debug("[DMOS-O11Y] Approval request: target={}", targetType);
    }

    /**
     * Records a notification dispatch (per-flow metric).
     */
    public void recordNotificationDispatch(String template) {
        notificationDispatchCounter.increment();
        LOG.debug("[DMOS-O11Y] Notification dispatch: template={}", template);
    }

    /**
     * Records notification delivery duration (per-flow metric).
     */
    public void recordNotificationDelivery(String template, long durationMillis) {
        notificationDeliveryTimer.record(durationMillis, TimeUnit.MILLISECONDS);
        LOG.debug("[DMOS-O11Y] Notification delivery: template={} duration={}ms", template, durationMillis);
    }

    // P2-1: Startup readiness signals

    /**
     * Marks startup as complete and records startup duration.
     */
    public void markStartupComplete() {
        if (!startupComplete) {
            startupComplete = true;
            long startupDuration = System.currentTimeMillis() - startupStartTime;
            metrics.gauge("dmos.startup.duration", startupDuration);
            metrics.gauge("dmos.startup.ready", 1.0);
            LOG.info("[DMOS-O11Y] Startup complete in {}ms", startupDuration);
        }
    }

    /**
     * Returns whether the application has completed startup.
     */
    public boolean isStartupComplete() {
        return startupComplete;
    }

    /**
     * Records a component readiness state.
     */
    public void recordComponentReady(String component) {
        metrics.gauge("dmos.component." + component + ".ready", 1.0);
        LOG.debug("[DMOS-O11Y] Component ready: {}", component);
    }

    /**
     * Records a component failure.
     */
    public void recordComponentFailure(String component, String reason) {
        metrics.gauge("dmos.component." + component + ".ready", 0.0);
        LOG.error("[DMOS-O11Y] Component failed: component={} reason={}", component, reason);
    }
}
