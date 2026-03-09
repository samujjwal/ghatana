package com.ghatana.core.pipeline;

import com.ghatana.core.operator.catalog.OperatorCatalog;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable execution context for a single pipeline run.
 *
 * <p><b>Purpose</b><br>
 * Carries all execution-scoped state through a pipeline run: identity (pipeline ID,
 * execution ID, tenant), infrastructure references (operator catalog), execution
 * policies (deadline, continue-on-error), and diagnostic metadata.
 *
 * <p><b>Architecture Role</b><br>
 * Created once per {@link PipelineExecutionEngine#execute} invocation and threaded
 * through every stage. Stages use it to resolve operators, check deadlines, and
 * propagate correlation IDs.
 *
 * <p><b>Thread Safety</b><br>
 * Fully immutable — safe to share across concurrent stage executions in parallel branches.
 *
 * <p><b>Usage</b>
 * <pre>{@code
 * PipelineExecutionContext ctx = PipelineExecutionContext.builder()
 *     .pipelineId("fraud-detection")
 *     .tenantId("acme-corp")
 *     .operatorCatalog(catalog)
 *     .deadline(Duration.ofSeconds(30))
 *     .continueOnError(false)
 *     .build();
 * }</pre>
 *
 * @see PipelineExecutionEngine
 * @see StageExecutionResult
 *
 * @doc.type class
 * @doc.purpose Immutable execution context for a single pipeline run
 * @doc.layer core
 * @doc.pattern Value Object
 */
public final class PipelineExecutionContext {

    private final String pipelineId;
    private final String executionId;
    private final String tenantId;
    private final String correlationId;
    private final OperatorCatalog operatorCatalog;
    private final Duration deadline;
    private final Instant startTime;
    private final boolean continueOnError;
    private final Map<String, Object> metadata;

    private PipelineExecutionContext(Builder builder) {
        this.pipelineId = Objects.requireNonNull(builder.pipelineId, "pipelineId");
        this.executionId = builder.executionId != null ? builder.executionId : UUID.randomUUID().toString();
        this.tenantId = builder.tenantId != null ? builder.tenantId : "default";
        this.correlationId = builder.correlationId != null ? builder.correlationId : executionId;
        this.operatorCatalog = Objects.requireNonNull(builder.operatorCatalog, "operatorCatalog");
        this.deadline = builder.deadline != null ? builder.deadline : Duration.ofSeconds(30);
        this.startTime = Instant.now();
        this.continueOnError = builder.continueOnError;
        this.metadata = builder.metadata != null ? Map.copyOf(builder.metadata) : Map.of();
    }

    // ── Accessors ────────────────────────────────────────────────────

    public String getPipelineId() { return pipelineId; }
    public String getExecutionId() { return executionId; }
    public String getTenantId() { return tenantId; }
    public String getCorrelationId() { return correlationId; }
    public OperatorCatalog getOperatorCatalog() { return operatorCatalog; }
    public Duration getDeadline() { return deadline; }
    public Instant getStartTime() { return startTime; }
    public boolean isContinueOnError() { return continueOnError; }
    public Map<String, Object> getMetadata() { return metadata; }

    /**
     * Returns the remaining time before deadline expires.
     *
     * @return remaining duration, or {@link Duration#ZERO} if already expired
     */
    public Duration getRemainingTime() {
        Duration elapsed = Duration.between(startTime, Instant.now());
        Duration remaining = deadline.minus(elapsed);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    /**
     * Returns true if the deadline has been exceeded.
     *
     * @return true if execution time exceeds deadline
     */
    public boolean isDeadlineExceeded() {
        return getRemainingTime().isZero();
    }

    // ── Builder ──────────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String pipelineId;
        private String executionId;
        private String tenantId;
        private String correlationId;
        private OperatorCatalog operatorCatalog;
        private Duration deadline;
        private boolean continueOnError;
        private Map<String, Object> metadata;

        private Builder() {}

        public Builder pipelineId(String pipelineId) {
            this.pipelineId = pipelineId;
            return this;
        }

        public Builder executionId(String executionId) {
            this.executionId = executionId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder operatorCatalog(OperatorCatalog operatorCatalog) {
            this.operatorCatalog = operatorCatalog;
            return this;
        }

        public Builder deadline(Duration deadline) {
            this.deadline = deadline;
            return this;
        }

        public Builder continueOnError(boolean continueOnError) {
            this.continueOnError = continueOnError;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public PipelineExecutionContext build() {
            return new PipelineExecutionContext(this);
        }
    }

    @Override
    public String toString() {
        return String.format("PipelineExecutionContext{pipeline=%s, execution=%s, tenant=%s, deadline=%s, continueOnError=%s}",
                pipelineId, executionId, tenantId, deadline, continueOnError);
    }
}
