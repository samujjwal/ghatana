package com.ghatana.datacloud.entity.agent;

import com.ghatana.datacloud.entity.policy.PolicyDecision;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.*;

/**
 * Represents a tool call made during agent execution with governance and audit tracking.
 *
 * <p><b>Purpose</b><br>
 * Captures detailed information about each tool invocation made by an agent.
 * Provides comprehensive audit trail, governance controls, and observability for
 * all tool interactions including input validation, output verification, and policy enforcement.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * ToolCall call = ToolCall.builder()
 *     .toolName("customer_lookup")
 *     .toolCallId("call-123")
 *     .agentRunId("run-456")
 *     .input(Map.of("customerId", "cust-789", "includeHistory", true))
 *     .mutating(false)
 *     .build();
 * 
 * // Record successful execution
 * call.complete(Map.of("customer", customerData), ToolCall.CallStatus.SUCCESS);
 * 
 * // Record failure with error
 * call.fail("CUSTOMER_NOT_FOUND", "Customer not found", "VALIDATION_ERROR");
 * }</pre>
 *
 * @see AgentRun
 * @doc.type class
 * @doc.purpose Tool call with governance and audit tracking
 * @doc.layer product
 * @doc.pattern Domain Model (JPA Embeddable)
 */
@jakarta.persistence.Embeddable
public class ToolCall {

    @Column(name = "tool_call_id", nullable = false, length = 255)
    private String toolCallId;

    @Column(name = "tool_name", nullable = false, length = 255)
    private String toolName;

    @Column(name = "tool_version", length = 50)
    private String toolVersion;

    @Column(name = "agent_run_id", length = 255)
    private String agentRunId;

    @Column(name = "idempotency_key", length = 255)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "call_status", nullable = false, length = 50)
    private CallStatus status = CallStatus.PENDING;

    @Column(name = "mutating", nullable = false)
    private Boolean mutating = false;

    /**
     * Input parameters provided to the tool.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input", columnDefinition = "jsonb")
    private Map<String, Object> input = new HashMap<>();

    /**
     * Output returned by the tool.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output", columnDefinition = "jsonb")
    private Map<String, Object> output = new HashMap<>();

    /**
     * Validation results for input and output.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_results", columnDefinition = "jsonb")
    private ValidationResults validationResults;

    /**
     * Policy decisions applied to this tool call.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "policy_decisions", columnDefinition = "jsonb")
    private List<PolicyDecision> policyDecisions = new ArrayList<>();

    /**
     * Error information if the call failed.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "error_info", columnDefinition = "jsonb")
    private ErrorInfo errorInfo;

    /**
     * Performance metrics for this call.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metrics", columnDefinition = "jsonb")
    private Map<String, Object> metrics = new HashMap<>();

    /**
     * Security and governance metadata.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "security_metadata", columnDefinition = "jsonb")
    private Map<String, Object> securityMetadata = new HashMap<>();

    /**
     * Audit trail information.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "audit_metadata", columnDefinition = "jsonb")
    private Map<String, Object> auditMetadata = new HashMap<>();

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "timeout_seconds")
    private Integer timeoutSeconds;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "max_retries")
    private Integer maxRetries = 3;

    /**
     * Tool call status enumeration.
     */
    public enum CallStatus {
        PENDING,         // Call created but not started
        RUNNING,         // Call is executing
        SUCCESS,         // Call completed successfully
        FAILED,          // Call failed with error
        TIMEOUT,         // Call timed out
        CANCELLED,       // Call was cancelled
        RETRYING,        // Call is being retried
        RATE_LIMITED,    // Call is rate limited
        ACCESS_DENIED    // Call denied by policy
    }

    /**
     * Validation results for input/output.
     */
    public record ValidationResults(
        boolean inputValid,
        boolean outputValid,
        List<String> inputErrors,
        List<String> outputErrors,
        Map<String, Object> validationMetadata
    ) {}

    /**
     * Error information for failed calls.
     */
    public record ErrorInfo(
        String errorCode,
        String errorMessage,
        String errorType,
        String errorCategory,
        Map<String, Object> context,
        Instant occurredAt,
        boolean retryable
    ) {}

    // ============ Getters & Setters ============

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getToolVersion() {
        return toolVersion;
    }

    public void setToolVersion(String toolVersion) {
        this.toolVersion = toolVersion;
    }

    public String getAgentRunId() {
        return agentRunId;
    }

    public void setAgentRunId(String agentRunId) {
        this.agentRunId = agentRunId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public CallStatus getStatus() {
        return status;
    }

    public void setStatus(CallStatus status) {
        this.status = status;
    }

    public Boolean getMutating() {
        return mutating;
    }

    public void setMutating(Boolean mutating) {
        this.mutating = mutating;
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

    public ValidationResults getValidationResults() {
        return validationResults;
    }

    public void setValidationResults(ValidationResults validationResults) {
        this.validationResults = validationResults;
    }

    public List<PolicyDecision> getPolicyDecisions() {
        return policyDecisions;
    }

    public void setPolicyDecisions(List<PolicyDecision> policyDecisions) {
        this.policyDecisions = policyDecisions;
    }

    public ErrorInfo getErrorInfo() {
        return errorInfo;
    }

    public void setErrorInfo(ErrorInfo errorInfo) {
        this.errorInfo = errorInfo;
    }

    public Map<String, Object> getMetrics() {
        return metrics;
    }

    public void setMetrics(Map<String, Object> metrics) {
        this.metrics = metrics;
    }

    public Map<String, Object> getSecurityMetadata() {
        return securityMetadata;
    }

    public void setSecurityMetadata(Map<String, Object> securityMetadata) {
        this.securityMetadata = securityMetadata;
    }

    public Map<String, Object> getAuditMetadata() {
        return auditMetadata;
    }

    public void setAuditMetadata(Map<String, Object> auditMetadata) {
        this.auditMetadata = auditMetadata;
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

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(Integer timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Integer getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(Integer maxRetries) {
        this.maxRetries = maxRetries;
    }

    // ============ Business Methods ============

    /**
     * Starts the tool call execution.
     */
    public void start() {
        if (status != CallStatus.PENDING && status != CallStatus.RETRYING) {
            throw new IllegalStateException("Tool call can only be started from PENDING or RETRYING status");
        }
        this.status = CallStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    /**
     * Completes the tool call successfully.
     */
    public void complete(Map<String, Object> output) {
        if (status != CallStatus.RUNNING) {
            throw new IllegalStateException("Tool call can only be completed from RUNNING status");
        }
        this.status = CallStatus.SUCCESS;
        this.output = output;
        this.completedAt = Instant.now();
        
        // Update metrics
        if (startedAt != null) {
            metrics.put("durationMs", java.time.Duration.between(startedAt, completedAt).toMillis());
        }
        metrics.put("retryCount", retryCount);
    }

    /**
     * Fails the tool call with error information.
     */
    public void fail(String errorCode, String errorMessage, String errorType, String errorCategory, 
                    Map<String, Object> context, boolean retryable) {
        this.status = CallStatus.FAILED;
        this.errorInfo = new ErrorInfo(errorCode, errorMessage, errorType, errorCategory, 
                                    context, Instant.now(), retryable);
        this.completedAt = Instant.now();
        
        // Update metrics
        if (startedAt != null) {
            metrics.put("durationMs", java.time.Duration.between(startedAt, completedAt).toMillis());
        }
        metrics.put("retryCount", retryCount);
        metrics.put("failed", true);
    }

    /**
     * Times out the tool call.
     */
    public void timeout() {
        this.status = CallStatus.TIMEOUT;
        this.errorInfo = new ErrorInfo("TIMEOUT", "Tool call timed out", "TIMEOUT_ERROR", "TIMEOUT",
                                    Map.of("timeoutSeconds", timeoutSeconds), Instant.now(), true);
        this.completedAt = Instant.now();
        
        // Update metrics
        if (startedAt != null) {
            metrics.put("durationMs", java.time.Duration.between(startedAt, completedAt).toMillis());
        }
        metrics.put("timeout", true);
    }

    /**
     * Cancels the tool call.
     */
    public void cancel() {
        this.status = CallStatus.CANCELLED;
        this.completedAt = Instant.now();
        
        // Update metrics
        if (startedAt != null) {
            metrics.put("durationMs", java.time.Duration.between(startedAt, completedAt).toMillis());
        }
        metrics.put("cancelled", true);
    }

    /**
     * Marks the call for retry.
     */
    public void retry() {
        if (retryCount >= maxRetries) {
            throw new IllegalStateException("Maximum retries exceeded");
        }
        this.status = CallStatus.RETRYING;
        this.retryCount++;
        this.startedAt = null;
        this.completedAt = null;
        this.errorInfo = null;
    }

    /**
     * Rate limits the tool call.
     */
    public void rateLimit(String reason) {
        this.status = CallStatus.RATE_LIMITED;
        this.errorInfo = new ErrorInfo("RATE_LIMITED", reason, "RATE_LIMIT_ERROR", "RATE_LIMIT",
                                    Map.of("retryCount", retryCount), Instant.now(), true);
    }

    /**
     * Denies access due to policy.
     */
    public void denyAccess(String reason) {
        this.status = CallStatus.ACCESS_DENIED;
        this.errorInfo = new ErrorInfo("ACCESS_DENIED", reason, "POLICY_ERROR", "SECURITY",
                                    Map.of("mutating", mutating), Instant.now(), false);
        this.completedAt = Instant.now();
    }

    /**
     * Adds a policy decision to this tool call.
     */
    public void addPolicyDecision(PolicyDecision policyDecision) {
        this.policyDecisions.add(policyDecision);
    }

    /**
     * Updates validation results.
     */
    public void updateValidationResults(boolean inputValid, boolean outputValid, 
                                      List<String> inputErrors, List<String> outputErrors) {
        this.validationResults = new ValidationResults(inputValid, outputValid, inputErrors, outputErrors, Map.of());
    }

    /**
     * Updates metrics.
     */
    public void updateMetrics(Map<String, Object> newMetrics) {
        this.metrics.putAll(newMetrics);
    }

    /**
     * Gets the duration of the call in milliseconds.
     */
    public Long getDurationMs() {
        if (startedAt == null) return null;
        Instant end = completedAt != null ? completedAt : Instant.now();
        return java.time.Duration.between(startedAt, end).toMillis();
    }

    /**
     * Checks if the call is in a terminal state.
     */
    public boolean isTerminal() {
        return status == CallStatus.SUCCESS || 
               status == CallStatus.FAILED || 
               status == CallStatus.TIMEOUT || 
               status == CallStatus.CANCELLED || 
               status == CallStatus.ACCESS_DENIED;
    }

    /**
     * Checks if the call is active.
     */
    public boolean isActive() {
        return status == CallStatus.RUNNING || status == CallStatus.RETRYING;
    }

    /**
     * Checks if the call can be retried.
     */
    public boolean canRetry() {
        return retryCount < maxRetries && 
               (status == CallStatus.FAILED || status == CallStatus.TIMEOUT) && 
               errorInfo != null && errorInfo.retryable();
    }

    // ============ Builder Pattern ============

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String toolCallId;
        private String toolName;
        private String toolVersion;
        private String agentRunId;
        private String idempotencyKey;
        private CallStatus status = CallStatus.PENDING;
        private Boolean mutating = false;
        private Map<String, Object> input = new HashMap<>();
        private Map<String, Object> output = new HashMap<>();
        private ValidationResults validationResults;
        private List<PolicyDecision> policyDecisions = new ArrayList<>();
        private ErrorInfo errorInfo;
        private Map<String, Object> metrics = new HashMap<>();
        private Map<String, Object> securityMetadata = new HashMap<>();
        private Map<String, Object> auditMetadata = new HashMap<>();
        private Instant startedAt;
        private Instant completedAt;
        private Integer timeoutSeconds;
        private Integer retryCount = 0;
        private Integer maxRetries = 3;

        public Builder toolCallId(String toolCallId) {
            this.toolCallId = toolCallId;
            return this;
        }

        public Builder toolName(String toolName) {
            this.toolName = toolName;
            return this;
        }

        public Builder toolVersion(String toolVersion) {
            this.toolVersion = toolVersion;
            return this;
        }

        public Builder agentRunId(String agentRunId) {
            this.agentRunId = agentRunId;
            return this;
        }

        public Builder idempotencyKey(String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        public Builder status(CallStatus status) {
            this.status = status;
            return this;
        }

        public Builder mutating(Boolean mutating) {
            this.mutating = mutating;
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

        public Builder validationResults(ValidationResults validationResults) {
            this.validationResults = validationResults;
            return this;
        }

        public Builder policyDecisions(List<PolicyDecision> policyDecisions) {
            this.policyDecisions = policyDecisions;
            return this;
        }

        public Builder errorInfo(ErrorInfo errorInfo) {
            this.errorInfo = errorInfo;
            return this;
        }

        public Builder metrics(Map<String, Object> metrics) {
            this.metrics = metrics;
            return this;
        }

        public Builder securityMetadata(Map<String, Object> securityMetadata) {
            this.securityMetadata = securityMetadata;
            return this;
        }

        public Builder auditMetadata(Map<String, Object> auditMetadata) {
            this.auditMetadata = auditMetadata;
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

        public Builder timeoutSeconds(Integer timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
            return this;
        }

        public Builder retryCount(Integer retryCount) {
            this.retryCount = retryCount;
            return this;
        }

        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public ToolCall build() {
            ToolCall call = new ToolCall();
            call.toolCallId = this.toolCallId;
            call.toolName = this.toolName;
            call.toolVersion = this.toolVersion;
            call.agentRunId = this.agentRunId;
            call.idempotencyKey = this.idempotencyKey;
            call.status = this.status;
            call.mutating = this.mutating;
            call.input = this.input;
            call.output = this.output;
            call.validationResults = this.validationResults;
            call.policyDecisions = this.policyDecisions;
            call.errorInfo = this.errorInfo;
            call.metrics = this.metrics;
            call.securityMetadata = this.securityMetadata;
            call.auditMetadata = this.auditMetadata;
            call.startedAt = this.startedAt;
            call.completedAt = this.completedAt;
            call.timeoutSeconds = this.timeoutSeconds;
            call.retryCount = this.retryCount;
            call.maxRetries = this.maxRetries;
            return call;
        }
    }

    @Override
    public String toString() {
        return "ToolCall{" +
                "toolCallId='" + toolCallId + '\'' +
                ", toolName='" + toolName + '\'' +
                ", status=" + status +
                ", durationMs=" + getDurationMs() +
                '}';
    }
}
