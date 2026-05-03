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
 * @doc.type class
 * @doc.purpose DMOS product observability service (DMOS-P1-011)
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

    // Metrics timers
    private Timer apiDurationTimer;
    private Timer commandDurationTimer;
    private Timer workflowDurationTimer;
    private Timer connectorDurationTimer;
    private Timer approvalLatencyTimer;

    public DmosObservability(Metrics metrics, TracingManager tracingManager) {
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
        this.tracingManager = Objects.requireNonNull(tracingManager, "tracingManager must not be null");
        initializeMetrics();
    }

    private void initializeMetrics() {
        this.commandSuccessCounter = metrics.counter("dmos.command.success", "DMOS command success count");
        this.commandFailureCounter = metrics.counter("dmos.command.failure", "DMOS command failure count");
        this.connectorFailureCounter = metrics.counter("dmos.connector.failure", "DMOS connector failure count");
        this.dlqCounter = metrics.counter("dmos.dlq.count", "DMOS dead-letter queue count");

        this.apiDurationTimer = metrics.timer("dmos.api.duration", "DMOS API request duration");
        this.commandDurationTimer = metrics.timer("dmos.command.duration", "DMOS command execution duration");
        this.workflowDurationTimer = metrics.timer("dmos.workflow.duration", "DMOS workflow execution duration");
        this.connectorDurationTimer = metrics.timer("dmos.connector.duration", "DMOS connector call duration");
        this.approvalLatencyTimer = metrics.timer("dmos.approval.latency", "DMOS approval request latency");
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
}
