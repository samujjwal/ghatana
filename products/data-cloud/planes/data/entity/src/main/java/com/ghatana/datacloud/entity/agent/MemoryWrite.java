package com.ghatana.datacloud.entity.agent;

import com.ghatana.datacloud.entity.policy.PolicyDecision;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.*;

/**
 * Represents a memory write operation performed during agent execution.
 *
 * <p><b>Purpose</b><br>
 * Captures detailed information about memory operations performed by agents including
 * semantic memory, episodic memory, and working memory updates. Provides comprehensive
 * governance, audit trail, and lifecycle management for all memory operations.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * MemoryWrite write = MemoryWrite.builder()
 *     .memoryWriteId("write-123")
 *     .agentRunId("run-456")
 *     .memoryType(MemoryWrite.MemoryType.SEMANTIC)
 *     .operation(MemoryWrite.Operation.CREATE)
 *     .content(Map.of("fact", "Customer prefers email communication", "confidence", 0.95))
 *     .build();
 * 
 * // Complete the write operation
 * write.complete(MemoryWrite.WriteStatus.SUCCESS, "Memory written successfully");
 * 
 * // Record failure
 * write.fail("MEMORY_FULL", "Semantic memory capacity exceeded", "CAPACITY_ERROR");
 * }</pre>
 *
 * @see AgentRun
 * @doc.type class
 * @doc.purpose Memory write operation with governance and lifecycle tracking
 * @doc.layer product
 * @doc.pattern Domain Model (JPA Embeddable)
 */
@jakarta.persistence.Embeddable
public class MemoryWrite {

    @Column(name = "memory_write_id", nullable = false, length = 255)
    private String memoryWriteId;

    @Column(name = "agent_run_id", length = 255)
    private String agentRunId;

    @Enumerated(EnumType.STRING)
    @Column(name = "memory_type", nullable = false, length = 50)
    private MemoryType memoryType;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation", nullable = false, length = 50)
    private Operation operation;

    @Enumerated(EnumType.STRING)
    @Column(name = "write_status", nullable = false, length = 50)
    private WriteStatus status = WriteStatus.PENDING;

    @Column(name = "memory_key", length = 500)
    private String memoryKey;

    @Column(name = "memory_tier", length = 50)
    private String memoryTier;

    /**
     * Content being written to memory.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content", columnDefinition = "jsonb")
    private Map<String, Object> content = new HashMap<>();

    /**
     * Previous content for update operations.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "previous_content", columnDefinition = "jsonb")
    private Map<String, Object> previousContent = new HashMap<>();

    /**
     * Metadata about the memory write.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "write_metadata", columnDefinition = "jsonb")
    private Map<String, Object> writeMetadata = new HashMap<>();

    /**
     * Policy decisions applied to this memory write.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "policy_decisions", columnDefinition = "jsonb")
    private List<PolicyDecision> policyDecisions = new ArrayList<>();

    /**
     * Validation results for the write operation.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_results", columnDefinition = "jsonb")
    private ValidationResults validationResults;

    /**
     * Error information if the write failed.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "error_info", columnDefinition = "jsonb")
    private ErrorInfo errorInfo;

    /**
     * Performance metrics for the write operation.
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
     * Audit trail for the memory write.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "audit_trail", columnDefinition = "jsonb")
    private List<AuditEntry> auditTrail = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "retention_period_days")
    private Integer retentionPeriodDays;

    @Column(name = "access_count")
    private Integer accessCount = 0;

    @Column(name = "last_accessed_at")
    private Instant lastAccessedAt;

    /**
     * Memory type enumeration.
     */
    public enum MemoryType {
        SEMANTIC,      // Semantic memory (facts, concepts)
        EPISODIC,      // Episodic memory (events, experiences)
        WORKING,       // Working memory (temporary state)
        PROCEDURAL,    // Procedural memory (skills, procedures)
        DECLARATIVE,   // Declarative memory (explicit knowledge)
        LONG_TERM,     // Long-term memory storage
        SHORT_TERM,    // Short-term memory storage
        CACHE          // Cache memory
    }

    /**
     * Operation type enumeration.
     */
    public enum Operation {
        CREATE,        // Create new memory entry
        UPDATE,        // Update existing memory entry
        DELETE,        // Delete memory entry
        UPSERT,        // Update or insert memory entry
        MERGE,         // Merge with existing memory
        APPEND,        // Append to existing memory
        CLEAR,         // Clear memory contents
        ARCHIVE        // Archive memory entry
    }

    /**
     * Write status enumeration.
     */
    public enum WriteStatus {
        PENDING,       // Write operation pending
        RUNNING,       // Write operation in progress
        SUCCESS,       // Write completed successfully
        FAILED,        // Write operation failed
        CANCELLED,     // Write operation cancelled
        RETRYING,      // Write operation being retried
        RATE_LIMITED,  // Write operation rate limited
        ACCESS_DENIED  // Write denied by policy
    }

    /**
     * Validation results for the write operation.
     */
    public record ValidationResults(
        boolean contentValid,
        boolean schemaValid,
        boolean policyCompliant,
        List<String> validationErrors,
        List<String> policyViolations,
        Map<String, Object> validationMetadata
    ) {}

    /**
     * Error information for failed writes.
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

    /**
     * Audit trail entry.
     */
    public record AuditEntry(
        String action,
        String userId,
        String userType,
        String description,
        Instant timestamp,
        Map<String, Object> metadata
    ) {}

    // ============ Getters & Setters ============

    public String getMemoryWriteId() {
        return memoryWriteId;
    }

    public void setMemoryWriteId(String memoryWriteId) {
        this.memoryWriteId = memoryWriteId;
    }

    public String getAgentRunId() {
        return agentRunId;
    }

    public void setAgentRunId(String agentRunId) {
        this.agentRunId = agentRunId;
    }

    public MemoryType getMemoryType() {
        return memoryType;
    }

    public void setMemoryType(MemoryType memoryType) {
        this.memoryType = memoryType;
    }

    public Operation getOperation() {
        return operation;
    }

    public void setOperation(Operation operation) {
        this.operation = operation;
    }

    public WriteStatus getStatus() {
        return status;
    }

    public void setStatus(WriteStatus status) {
        this.status = status;
    }

    public String getMemoryKey() {
        return memoryKey;
    }

    public void setMemoryKey(String memoryKey) {
        this.memoryKey = memoryKey;
    }

    public String getMemoryTier() {
        return memoryTier;
    }

    public void setMemoryTier(String memoryTier) {
        this.memoryTier = memoryTier;
    }

    public Map<String, Object> getContent() {
        return content;
    }

    public void setContent(Map<String, Object> content) {
        this.content = content;
    }

    public Map<String, Object> getPreviousContent() {
        return previousContent;
    }

    public void setPreviousContent(Map<String, Object> previousContent) {
        this.previousContent = previousContent;
    }

    public Map<String, Object> getWriteMetadata() {
        return writeMetadata;
    }

    public void setWriteMetadata(Map<String, Object> writeMetadata) {
        this.writeMetadata = writeMetadata;
    }

    public List<PolicyDecision> getPolicyDecisions() {
        return policyDecisions;
    }

    public void setPolicyDecisions(List<PolicyDecision> policyDecisions) {
        this.policyDecisions = policyDecisions;
    }

    public ValidationResults getValidationResults() {
        return validationResults;
    }

    public void setValidationResults(ValidationResults validationResults) {
        this.validationResults = validationResults;
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

    public List<AuditEntry> getAuditTrail() {
        return auditTrail;
    }

    public void setAuditTrail(List<AuditEntry> auditTrail) {
        this.auditTrail = auditTrail;
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

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Integer getRetentionPeriodDays() {
        return retentionPeriodDays;
    }

    public void setRetentionPeriodDays(Integer retentionPeriodDays) {
        this.retentionPeriodDays = retentionPeriodDays;
    }

    public Integer getAccessCount() {
        return accessCount;
    }

    public void setAccessCount(Integer accessCount) {
        this.accessCount = accessCount;
    }

    public Instant getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void setLastAccessedAt(Instant lastAccessedAt) {
        this.lastAccessedAt = lastAccessedAt;
    }

    // ============ Business Methods ============

    /**
     * Starts the memory write operation.
     */
    public void start() {
        if (status != WriteStatus.PENDING && status != WriteStatus.RETRYING) {
            throw new IllegalStateException("Memory write can only be started from PENDING or RETRYING status");
        }
        this.status = WriteStatus.RUNNING;
        addAuditEntry("START", "agent", "AGENT", "Memory write operation started");
        this.updatedAt = Instant.now();
    }

    /**
     * Completes the memory write successfully.
     */
    public void complete(String message) {
        if (status != WriteStatus.RUNNING) {
            throw new IllegalStateException("Memory write can only be completed from RUNNING status");
        }
        this.status = WriteStatus.SUCCESS;
        this.completedAt = Instant.now();
        this.lastAccessedAt = Instant.now();
        
        // Update metrics
        metrics.put("durationMs", java.time.Duration.between(createdAt, completedAt).toMillis());
        metrics.put("contentSize", content.size());
        
        addAuditEntry("COMPLETE", "agent", "AGENT", message);
        this.updatedAt = Instant.now();
    }

    /**
     * Fails the memory write with error information.
     */
    public void fail(String errorCode, String errorMessage, String errorType, String errorCategory, 
                    Map<String, Object> context, boolean retryable) {
        this.status = WriteStatus.FAILED;
        this.errorInfo = new ErrorInfo(errorCode, errorMessage, errorType, errorCategory, 
                                    context, Instant.now(), retryable);
        this.completedAt = Instant.now();
        
        // Update metrics
        metrics.put("durationMs", java.time.Duration.between(createdAt, completedAt).toMillis());
        metrics.put("failed", true);
        
        addAuditEntry("FAIL", "agent", "AGENT", errorMessage);
        this.updatedAt = Instant.now();
    }

    /**
     * Cancels the memory write.
     */
    public void cancel(String reason) {
        this.status = WriteStatus.CANCELLED;
        this.completedAt = Instant.now();
        
        addAuditEntry("CANCEL", "agent", "AGENT", reason);
        this.updatedAt = Instant.now();
    }

    /**
     * Rate limits the memory write.
     */
    public void rateLimit(String reason) {
        this.status = WriteStatus.RATE_LIMITED;
        this.errorInfo = new ErrorInfo("RATE_LIMITED", reason, "RATE_LIMIT_ERROR", "RATE_LIMIT",
                                    Map.of("memoryType", memoryType), Instant.now(), true);
        
        addAuditEntry("RATE_LIMIT", "system", "SYSTEM", reason);
        this.updatedAt = Instant.now();
    }

    /**
     * Denies access due to policy.
     */
    public void denyAccess(String reason) {
        this.status = WriteStatus.ACCESS_DENIED;
        this.errorInfo = new ErrorInfo("ACCESS_DENIED", reason, "POLICY_ERROR", "SECURITY",
                                    Map.of("memoryType", memoryType, "operation", operation), Instant.now(), false);
        this.completedAt = Instant.now();
        
        addAuditEntry("DENY_ACCESS", "system", "SYSTEM", reason);
        this.updatedAt = Instant.now();
    }

    /**
     * Adds a policy decision to this memory write.
     */
    public void addPolicyDecision(PolicyDecision policyDecision) {
        this.policyDecisions.add(policyDecision);
    }

    /**
     * Updates validation results.
     */
    public void updateValidationResults(boolean contentValid, boolean schemaValid, boolean policyCompliant,
                                      List<String> validationErrors, List<String> policyViolations) {
        this.validationResults = new ValidationResults(contentValid, schemaValid, policyCompliant, 
                                                     validationErrors, policyViolations, Map.of());
    }

    /**
     * Updates metrics.
     */
    public void updateMetrics(Map<String, Object> newMetrics) {
        this.metrics.putAll(newMetrics);
        this.updatedAt = Instant.now();
    }

    /**
     * Records an access to this memory entry.
     */
    public void recordAccess() {
        this.accessCount++;
        this.lastAccessedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Adds an audit entry.
     */
    public void addAuditEntry(String action, String userId, String userType, String description) {
        AuditEntry entry = new AuditEntry(action, userId, userType, description, Instant.now(), Map.of());
        this.auditTrail.add(entry);
    }

    /**
     * Gets the duration of the write operation in milliseconds.
     */
    public Long getDurationMs() {
        if (createdAt == null) return null;
        Instant end = completedAt != null ? completedAt : Instant.now();
        return java.time.Duration.between(createdAt, end).toMillis();
    }

    /**
     * Checks if the write is in a terminal state.
     */
    public boolean isTerminal() {
        return status == WriteStatus.SUCCESS || 
               status == WriteStatus.FAILED || 
               status == WriteStatus.CANCELLED || 
               status == WriteStatus.ACCESS_DENIED;
    }

    /**
     * Checks if the write is active.
     */
    public boolean isActive() {
        return status == WriteStatus.RUNNING || status == WriteStatus.RETRYING;
    }

    /**
     * Checks if the memory entry has expired.
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * Checks if the memory entry should be archived based on retention policy.
     */
    public boolean shouldArchive() {
        if (retentionPeriodDays == null || createdAt == null) return false;
        Instant cutoffDate = Instant.now().minus(java.time.Duration.ofDays(retentionPeriodDays.longValue()));
        return createdAt.isBefore(cutoffDate);
    }

    /**
     * Gets the age of the memory entry in days.
     */
    public Long getAgeDays() {
        if (createdAt == null) return null;
        return java.time.Duration.between(createdAt, Instant.now()).toDays();
    }

    // ============ Lifecycle Callbacks ============

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        
        // Add creation audit entry
        addAuditEntry("CREATE", "agent", "AGENT", "Memory write created");
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
        private String memoryWriteId;
        private String agentRunId;
        private MemoryType memoryType;
        private Operation operation;
        private WriteStatus status = WriteStatus.PENDING;
        private String memoryKey;
        private String memoryTier;
        private Map<String, Object> content = new HashMap<>();
        private Map<String, Object> previousContent = new HashMap<>();
        private Map<String, Object> writeMetadata = new HashMap<>();
        private List<PolicyDecision> policyDecisions = new ArrayList<>();
        private ValidationResults validationResults;
        private ErrorInfo errorInfo;
        private Map<String, Object> metrics = new HashMap<>();
        private Map<String, Object> securityMetadata = new HashMap<>();
        private List<AuditEntry> auditTrail = new ArrayList<>();
        private Instant createdAt;
        private Instant updatedAt;
        private Instant completedAt;
        private Instant expiresAt;
        private Integer retentionPeriodDays;
        private Integer accessCount = 0;
        private Instant lastAccessedAt;

        public Builder memoryWriteId(String memoryWriteId) {
            this.memoryWriteId = memoryWriteId;
            return this;
        }

        public Builder agentRunId(String agentRunId) {
            this.agentRunId = agentRunId;
            return this;
        }

        public Builder memoryType(MemoryType memoryType) {
            this.memoryType = memoryType;
            return this;
        }

        public Builder operation(Operation operation) {
            this.operation = operation;
            return this;
        }

        public Builder status(WriteStatus status) {
            this.status = status;
            return this;
        }

        public Builder memoryKey(String memoryKey) {
            this.memoryKey = memoryKey;
            return this;
        }

        public Builder memoryTier(String memoryTier) {
            this.memoryTier = memoryTier;
            return this;
        }

        public Builder content(Map<String, Object> content) {
            this.content = content;
            return this;
        }

        public Builder previousContent(Map<String, Object> previousContent) {
            this.previousContent = previousContent;
            return this;
        }

        public Builder writeMetadata(Map<String, Object> writeMetadata) {
            this.writeMetadata = writeMetadata;
            return this;
        }

        public Builder policyDecisions(List<PolicyDecision> policyDecisions) {
            this.policyDecisions = policyDecisions;
            return this;
        }

        public Builder validationResults(ValidationResults validationResults) {
            this.validationResults = validationResults;
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

        public Builder auditTrail(List<AuditEntry> auditTrail) {
            this.auditTrail = auditTrail;
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

        public Builder completedAt(Instant completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        public Builder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public Builder retentionPeriodDays(Integer retentionPeriodDays) {
            this.retentionPeriodDays = retentionPeriodDays;
            return this;
        }

        public Builder accessCount(Integer accessCount) {
            this.accessCount = accessCount;
            return this;
        }

        public Builder lastAccessedAt(Instant lastAccessedAt) {
            this.lastAccessedAt = lastAccessedAt;
            return this;
        }

        public MemoryWrite build() {
            MemoryWrite write = new MemoryWrite();
            write.memoryWriteId = this.memoryWriteId;
            write.agentRunId = this.agentRunId;
            write.memoryType = this.memoryType;
            write.operation = this.operation;
            write.status = this.status;
            write.memoryKey = this.memoryKey;
            write.memoryTier = this.memoryTier;
            write.content = this.content;
            write.previousContent = this.previousContent;
            write.writeMetadata = this.writeMetadata;
            write.policyDecisions = this.policyDecisions;
            write.validationResults = this.validationResults;
            write.errorInfo = this.errorInfo;
            write.metrics = this.metrics;
            write.securityMetadata = this.securityMetadata;
            write.auditTrail = this.auditTrail;
            write.createdAt = this.createdAt;
            write.updatedAt = this.updatedAt;
            write.completedAt = this.completedAt;
            write.expiresAt = this.expiresAt;
            write.retentionPeriodDays = this.retentionPeriodDays;
            write.accessCount = this.accessCount;
            write.lastAccessedAt = this.lastAccessedAt;
            return write;
        }
    }

    @Override
    public String toString() {
        return "MemoryWrite{" +
                "memoryWriteId='" + memoryWriteId + '\'' +
                ", memoryType=" + memoryType +
                ", operation=" + operation +
                ", status=" + status +
                ", durationMs=" + getDurationMs() +
                '}';
    }
}
