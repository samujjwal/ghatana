package com.ghatana.datacloud.entity.agent;

import com.ghatana.datacloud.entity.policy.PolicyDecision;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.*;

/**
 * Represents a complete agent execution run with governance and lifecycle tracking.
 *
 * <p><b>Purpose</b><br>
 * Tracks the entire lifecycle of an agent execution from initialization through completion.
 * Provides comprehensive governance, audit trail, and observability for all agent activities.
 * Serves as the central entity for agent runtime governance and memory lifecycle management.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * AgentRun run = AgentRun.builder()
 *     .agentId("customer-support-agent")
 *     .tenantId("tenant-123")
 *     .sessionId("session-456")
 *     .input(Map.of("query", "Help with order status", "customerId", "cust-789"))
 *     .build();
 * 
 * // Track tool calls during execution
 * run.addToolCall(toolCall);
 * 
 * // Record memory writes
 * run.addMemoryWrite(memoryWrite);
 * 
 * // Complete with final state
 * run.complete(AgentRun.RunStatus.SUCCESS, Map.of("response", "Order shipped"));
 * }</pre>
 *
 * @see ToolCall
 * @see MemoryWrite
 * @see ApprovalRequest
 * @see PolicyDecision
 * @see RunTrace
 * @doc.type class
 * @doc.purpose Agent execution run with governance and lifecycle tracking
 * @doc.layer product
 * @doc.pattern Domain Model (JPA Entity)
 */
@Entity
@Table(name = "agent_runs", 
       indexes = {
           @Index(name = "idx_agent_runs_agent_id", columnList = "agent_id"),
           @Index(name = "idx_agent_runs_tenant_id", columnList = "tenant_id"),
           @Index(name = "idx_agent_runs_session_id", columnList = "session_id"),
           @Index(name = "idx_agent_runs_status", columnList = "status"),
           @Index(name = "idx_agent_runs_created_at", columnList = "created_at")
       })
public class AgentRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "agent_id", nullable = false, length = 255)
    private String agentId;

    @NotNull
    @Column(name = "tenant_id", nullable = false, length = 255)
    private String tenantId;

    @Column(name = "session_id", length = 255)
    private String sessionId;

    @Column(name = "correlation_id", length = 255)
    private String correlationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private RunStatus status = RunStatus.INITIALIZED;

    /**
     * Initial input provided to the agent.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input", columnDefinition = "jsonb")
    private Map<String, Object> input = new HashMap<>();

    /**
     * Final output produced by the agent.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output", columnDefinition = "jsonb")
    private Map<String, Object> output = new HashMap<>();

    /**
     * All tool calls made during this run.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tool_calls", columnDefinition = "jsonb")
    private List<ToolCall> toolCalls = new ArrayList<>();

    /**
     * All memory writes performed during this run.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "memory_writes", columnDefinition = "jsonb")
    private List<MemoryWrite> memoryWrites = new ArrayList<>();

    /**
     * All approval requests generated during this run.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "approval_requests", columnDefinition = "jsonb")
    private List<ApprovalRequest> approvalRequests = new ArrayList<>();

    /**
     * Policy decisions made during this run.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "policy_decisions", columnDefinition = "jsonb")
    private List<PolicyDecision> policyDecisions = new ArrayList<>();

    /**
     * Detailed execution trace.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "run_trace", columnDefinition = "jsonb")
    private RunTrace runTrace;

    /**
     * Runtime metrics and telemetry.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metrics", columnDefinition = "jsonb")
    private Map<String, Object> metrics = new HashMap<>();

    /**
     * Error information if the run failed.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "error_info", columnDefinition = "jsonb")
    private ErrorInfo errorInfo;

    /**
     * Governance metadata including compliance checks and audit trail.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "governance_metadata", columnDefinition = "jsonb")
    private Map<String, Object> governanceMetadata = new HashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "completed_by", length = 255)
    private String completedBy;

    /**
     * Run status enumeration.
     */
    public enum RunStatus {
        INITIALIZED,     // Run created but not started
        RUNNING,         // Run is actively executing
        WAITING_APPROVAL, // Run paused waiting for human approval
        COMPLETED,       // Run completed successfully
        FAILED,          // Run failed with error
        CANCELLED,       // Run was cancelled
        TIMEOUT,         // Run timed out
        SUSPENDED        // Run temporarily suspended
    }

    /**
     * Error information for failed runs.
     */
    public record ErrorInfo(
        String errorCode,
        String errorMessage,
        String errorType,
        Map<String, Object> context,
        Instant occurredAt,
        String stackTrace
    ) {}

    // ============ Getters & Setters ============

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public RunStatus getStatus() {
        return status;
    }

    public void setStatus(RunStatus status) {
        this.status = status;
    }

    public Map<String, Object> getInput() {
        return input;
    }

    public void setInput(Map<String, Object> input) {
        this.input = input;
    }

    public Map<String, Object> getOutput() {
        return output;
    }

    public void setOutput(Map<String, Object> output) {
        this.output = output;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(List<ToolCall> toolCalls) {
        this.toolCalls = toolCalls;
    }

    public List<MemoryWrite> getMemoryWrites() {
        return memoryWrites;
    }

    public void setMemoryWrites(List<MemoryWrite> memoryWrites) {
        this.memoryWrites = memoryWrites;
    }

    public List<ApprovalRequest> getApprovalRequests() {
        return approvalRequests;
    }

    public void setApprovalRequests(List<ApprovalRequest> approvalRequests) {
        this.approvalRequests = approvalRequests;
    }

    public List<PolicyDecision> getPolicyDecisions() {
        return policyDecisions;
    }

    public void setPolicyDecisions(List<PolicyDecision> policyDecisions) {
        this.policyDecisions = policyDecisions;
    }

    public RunTrace getRunTrace() {
        return runTrace;
    }

    public void setRunTrace(RunTrace runTrace) {
        this.runTrace = runTrace;
    }

    public Map<String, Object> getMetrics() {
        return metrics;
    }

    public void setMetrics(Map<String, Object> metrics) {
        this.metrics = metrics;
    }

    public ErrorInfo getErrorInfo() {
        return errorInfo;
    }

    public void setErrorInfo(ErrorInfo errorInfo) {
        this.errorInfo = errorInfo;
    }

    public Map<String, Object> getGovernanceMetadata() {
        return governanceMetadata;
    }

    public void setGovernanceMetadata(Map<String, Object> governanceMetadata) {
        this.governanceMetadata = governanceMetadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getCompletedBy() {
        return completedBy;
    }

    public void setCompletedBy(String completedBy) {
        this.completedBy = completedBy;
    }

    // ============ Business Methods ============

    /**
     * Starts the agent run.
     */
    public void start() {
        if (status != RunStatus.INITIALIZED) {
            throw new IllegalStateException("Run can only be started from INITIALIZED status");
        }
        this.status = RunStatus.RUNNING;
        this.startedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Completes the agent run successfully.
     */
    public void complete(Map<String, Object> output) {
        if (status != RunStatus.RUNNING) {
            throw new IllegalStateException("Run can only be completed from RUNNING status");
        }
        this.status = RunStatus.COMPLETED;
        this.output = output;
        this.completedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Fails the agent run with error information.
     */
    public void fail(String errorCode, String errorMessage, String errorType, Map<String, Object> context) {
        this.status = RunStatus.FAILED;
        this.errorInfo = new ErrorInfo(errorCode, errorMessage, errorType, context, Instant.now(), null);
        this.completedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Cancels the agent run.
     */
    public void cancel() {
        this.status = RunStatus.CANCELLED;
        this.completedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Puts the run in waiting for approval state.
     */
    public void waitForApproval() {
        if (status != RunStatus.RUNNING) {
            throw new IllegalStateException("Run can only wait for approval from RUNNING status");
        }
        this.status = RunStatus.WAITING_APPROVAL;
        this.updatedAt = Instant.now();
    }

    /**
     * Resumes the run after approval.
     */
    public void resumeAfterApproval() {
        if (status != RunStatus.WAITING_APPROVAL) {
            throw new IllegalStateException("Run can only resume from WAITING_APPROVAL status");
        }
        this.status = RunStatus.RUNNING;
        this.updatedAt = Instant.now();
    }

    /**
     * Adds a tool call to the run.
     */
    public void addToolCall(ToolCall toolCall) {
        this.toolCalls.add(toolCall);
        this.updatedAt = Instant.now();
    }

    /**
     * Adds a memory write to the run.
     */
    public void addMemoryWrite(MemoryWrite memoryWrite) {
        this.memoryWrites.add(memoryWrite);
        this.updatedAt = Instant.now();
    }

    /**
     * Adds an approval request to the run.
     */
    public void addApprovalRequest(ApprovalRequest approvalRequest) {
        this.approvalRequests.add(approvalRequest);
        this.updatedAt = Instant.now();
    }

    /**
     * Adds a policy decision to the run.
     */
    public void addPolicyDecision(PolicyDecision policyDecision) {
        this.policyDecisions.add(policyDecision);
        this.updatedAt = Instant.now();
    }

    /**
     * Updates metrics for the run.
     */
    public void updateMetrics(Map<String, Object> newMetrics) {
        this.metrics.putAll(newMetrics);
        this.updatedAt = Instant.now();
    }

    /**
     * Gets the duration of the run in milliseconds.
     */
    public Long getDurationMs() {
        if (startedAt == null) return null;
        Instant end = completedAt != null ? completedAt : Instant.now();
        return java.time.Duration.between(startedAt, end).toMillis();
    }

    /**
     * Checks if the run is in a terminal state.
     */
    public boolean isTerminal() {
        return status == RunStatus.COMPLETED || 
               status == RunStatus.FAILED || 
               status == RunStatus.CANCELLED || 
               status == RunStatus.TIMEOUT;
    }

    /**
     * Checks if the run is active.
     */
    public boolean isActive() {
        return status == RunStatus.RUNNING || status == RunStatus.WAITING_APPROVAL;
    }

    // ============ Lifecycle Callbacks ============

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // ============ Builder Pattern ============

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id;
        private String agentId;
        private String tenantId;
        private String sessionId;
        private String correlationId;
        private RunStatus status = RunStatus.INITIALIZED;
        private Map<String, Object> input = new HashMap<>();
        private Map<String, Object> output = new HashMap<>();
        private List<ToolCall> toolCalls = new ArrayList<>();
        private List<MemoryWrite> memoryWrites = new ArrayList<>();
        private List<ApprovalRequest> approvalRequests = new ArrayList<>();
        private List<PolicyDecision> policyDecisions = new ArrayList<>();
        private RunTrace runTrace;
        private Map<String, Object> metrics = new HashMap<>();
        private ErrorInfo errorInfo;
        private Map<String, Object> governanceMetadata = new HashMap<>();
        private Instant createdAt;
        private Instant updatedAt;
        private Instant startedAt;
        private Instant completedAt;
        private String createdBy;
        private String completedBy;

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder status(RunStatus status) {
            this.status = status;
            return this;
        }

        public Builder input(Map<String, Object> input) {
            this.input = input;
            return this;
        }

        public Builder output(Map<String, Object> output) {
            this.output = output;
            return this;
        }

        public Builder toolCalls(List<ToolCall> toolCalls) {
            this.toolCalls = toolCalls;
            return this;
        }

        public Builder memoryWrites(List<MemoryWrite> memoryWrites) {
            this.memoryWrites = memoryWrites;
            return this;
        }

        public Builder approvalRequests(List<ApprovalRequest> approvalRequests) {
            this.approvalRequests = approvalRequests;
            return this;
        }

        public Builder policyDecisions(List<PolicyDecision> policyDecisions) {
            this.policyDecisions = policyDecisions;
            return this;
        }

        public Builder runTrace(RunTrace runTrace) {
            this.runTrace = runTrace;
            return this;
        }

        public Builder metrics(Map<String, Object> metrics) {
            this.metrics = metrics;
            return this;
        }

        public Builder errorInfo(ErrorInfo errorInfo) {
            this.errorInfo = errorInfo;
            return this;
        }

        public Builder governanceMetadata(Map<String, Object> governanceMetadata) {
            this.governanceMetadata = governanceMetadata;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Builder startedAt(Instant startedAt) {
            this.startedAt = startedAt;
            return this;
        }

        public Builder completedAt(Instant completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        public Builder createdBy(String createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public Builder completedBy(String completedBy) {
            this.completedBy = completedBy;
            return this;
        }

        public AgentRun build() {
            AgentRun run = new AgentRun();
            run.id = this.id;
            run.agentId = this.agentId;
            run.tenantId = this.tenantId;
            run.sessionId = this.sessionId;
            run.correlationId = this.correlationId;
            run.status = this.status;
            run.input = this.input;
            run.output = this.output;
            run.toolCalls = this.toolCalls;
            run.memoryWrites = this.memoryWrites;
            run.approvalRequests = this.approvalRequests;
            run.policyDecisions = this.policyDecisions;
            run.runTrace = this.runTrace;
            run.metrics = this.metrics;
            run.errorInfo = this.errorInfo;
            run.governanceMetadata = this.governanceMetadata;
            run.createdAt = this.createdAt;
            run.updatedAt = this.updatedAt;
            run.startedAt = this.startedAt;
            run.completedAt = this.completedAt;
            run.createdBy = this.createdBy;
            run.completedBy = this.completedBy;
            return run;
        }
    }

    @Override
    public String toString() {
        return "AgentRun{" +
                "id=" + id +
                ", agentId='" + agentId + '\'' +
                ", tenantId='" + tenantId + '\'' +
                ", status=" + status +
                ", durationMs=" + getDurationMs() +
                '}';
    }
}
