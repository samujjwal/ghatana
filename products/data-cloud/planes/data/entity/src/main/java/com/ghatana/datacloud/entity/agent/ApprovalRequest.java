package com.ghatana.datacloud.entity.agent;

import com.ghatana.datacloud.entity.policy.PolicyDecision;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.*;

/**
 * Represents a human approval request generated during agent execution.
 *
 * <p><b>Purpose</b><br>
 * Captures approval requests that require human intervention during agent execution.
 * Provides comprehensive tracking of approval workflows including request details,
 * approver information, decisions, and audit trails for compliance and governance.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * ApprovalRequest request = ApprovalRequest.builder()
 *     .requestId("req-123")
 *     .agentRunId("run-456")
 *     .requestType("DATA_ACCESS")
 *     .title("Access to sensitive customer data")
 *     .description("Agent needs access to PII data for customer support")
 *     .requestedBy("agent-customer-support")
 *     .build();
 * 
 * // Approve the request
 * request.approve("admin-user", "Access granted for verified support case");
 * 
 * // Deny the request
 * request.deny("admin-user", "Insufficient justification for PII access");
 * }</pre>
 *
 * @see AgentRun
 * @doc.type class
 * @doc.purpose Human approval request with workflow tracking
 * @doc.layer product
 * @doc.pattern Domain Model (JPA Embeddable)
 */
@jakarta.persistence.Embeddable
public class ApprovalRequest {

    @Column(name = "request_id", nullable = false, length = 255)
    private String requestId;

    @Column(name = "agent_run_id", length = 255)
    private String agentRunId;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 100)
    private RequestType requestType;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "requested_by", nullable = false, length = 255)
    private String requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @Column(name = "priority", nullable = false)
    private Integer priority = 5;

    /**
     * Detailed request data and context.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_data", columnDefinition = "jsonb")
    private Map<String, Object> requestData = new HashMap<>();

    /**
     * List of required approvers.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "required_approvers", columnDefinition = "jsonb")
    private List<String> requiredApprovers = new ArrayList<>();

    /**
     * List of optional approvers.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "optional_approvers", columnDefinition = "jsonb")
    private List<String> optionalApprovers = new ArrayList<>();

    /**
     * Approval decisions made by approvers.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "approval_decisions", columnDefinition = "jsonb")
    private List<ApprovalDecision> approvalDecisions = new ArrayList<>();

    /**
     * Policy decisions related to this approval request.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "policy_decisions", columnDefinition = "jsonb")
    private List<PolicyDecision> policyDecisions = new ArrayList<>();

    /**
     * Risk assessment for this request.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "risk_assessment", columnDefinition = "jsonb")
    private RiskAssessment riskAssessment;

    /**
     * Compliance and governance metadata.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "compliance_metadata", columnDefinition = "jsonb")
    private Map<String, Object> complianceMetadata = new HashMap<>();

    /**
     * Audit trail information.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "audit_trail", columnDefinition = "jsonb")
    private List<AuditEntry> auditTrail = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by", length = 255)
    private String decidedBy;

    /**
     * Request type enumeration.
     */
    public enum RequestType {
        DATA_ACCESS,        // Access to sensitive data
        SYSTEM_CHANGE,      // System configuration changes
        RESOURCE_ALLOCATION, // Resource allocation requests
        SECURITY_BYPASS,    // Security policy bypass
        COST_EXCEEDANCE,    // Cost threshold exceedance
        COMPLIANCE_WAIVER,  // Compliance requirement waiver
        EXTERNAL_API_CALL,  // External API access
        MUTATING_OPERATION, // Data mutating operations
        CUSTOM              // Custom request types
    }

    /**
     * Approval status enumeration.
     */
    public enum ApprovalStatus {
        PENDING,           // Waiting for approval
        APPROVED,          // Approved
        DENIED,            // Denied
        CANCELLED,         // Cancelled by requester
        EXPIRED,           // Request expired
        ESCALATED,         // Escalated to higher authority
        CONDITIONAL_APPROVED, // Approved with conditions
        AUTO_APPROVED       // Automatically approved
    }

    /**
     * Priority levels (1=highest, 10=lowest).
     */
    public enum Priority {
        CRITICAL(1),
        HIGH(2),
        MEDIUM(5),
        LOW(8),
        ROUTINE(10);

        private final int value;

        Priority(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    /**
     * Individual approval decision.
     */
    public record ApprovalDecision(
        String approverId,
        String approverName,
        Decision decision,
        String comments,
        Instant decidedAt,
        Map<String, Object> metadata
    ) {
        public enum Decision {
            APPROVE,
            DENY,
            ABSTAIN,
            ESCALATE
        }
    }

    /**
     * Risk assessment for the approval request.
     */
    public record RiskAssessment(
        RiskLevel riskLevel,
        String riskCategory,
        List<String> riskFactors,
        String mitigationPlan,
        Double riskScore,
        Map<String, Object> riskDetails
    ) {
        public enum RiskLevel {
            LOW,
            MEDIUM,
            HIGH,
            CRITICAL
        }
    }

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

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getAgentRunId() {
        return agentRunId;
    }

    public void setAgentRunId(String agentRunId) {
        this.agentRunId = agentRunId;
    }

    public RequestType getRequestType() {
        return requestType;
    }

    public void setRequestType(RequestType requestType) {
        this.requestType = requestType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
    }

    public ApprovalStatus getStatus() {
        return status;
    }

    public void setStatus(ApprovalStatus status) {
        this.status = status;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Map<String, Object> getRequestData() {
        return requestData;
    }

    public void setRequestData(Map<String, Object> requestData) {
        this.requestData = requestData;
    }

    public List<String> getRequiredApprovers() {
        return requiredApprovers;
    }

    public void setRequiredApprovers(List<String> requiredApprovers) {
        this.requiredApprovers = requiredApprovers;
    }

    public List<String> getOptionalApprovers() {
        return optionalApprovers;
    }

    public void setOptionalApprovers(List<String> optionalApprovers) {
        this.optionalApprovers = optionalApprovers;
    }

    public List<ApprovalDecision> getApprovalDecisions() {
        return approvalDecisions;
    }

    public void setApprovalDecisions(List<ApprovalDecision> approvalDecisions) {
        this.approvalDecisions = approvalDecisions;
    }

    public List<PolicyDecision> getPolicyDecisions() {
        return policyDecisions;
    }

    public void setPolicyDecisions(List<PolicyDecision> policyDecisions) {
        this.policyDecisions = policyDecisions;
    }

    public RiskAssessment getRiskAssessment() {
        return riskAssessment;
    }

    public void setRiskAssessment(RiskAssessment riskAssessment) {
        this.riskAssessment = riskAssessment;
    }

    public Map<String, Object> getComplianceMetadata() {
        return complianceMetadata;
    }

    public void setComplianceMetadata(Map<String, Object> complianceMetadata) {
        this.complianceMetadata = complianceMetadata;
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

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public void setDecidedBy(String decidedBy) {
        this.decidedBy = decidedBy;
    }

    // ============ Business Methods ============

    /**
     * Approves the request.
     */
    public void approve(String approverId, String approverName, String comments) {
        addApprovalDecision(approverId, approverName, ApprovalDecision.Decision.APPROVE, comments);
        
        if (isFullyApproved()) {
            this.status = ApprovalStatus.APPROVED;
            this.decidedAt = Instant.now();
            this.decidedBy = approverId;
        }
        
        addAuditEntry("APPROVE", approverId, "APPROVER", comments);
        this.updatedAt = Instant.now();
    }

    /**
     * Denies the request.
     */
    public void deny(String approverId, String approverName, String comments) {
        addApprovalDecision(approverId, approverName, ApprovalDecision.Decision.DENY, comments);
        
        this.status = ApprovalStatus.DENIED;
        this.decidedAt = Instant.now();
        this.decidedBy = approverId;
        
        addAuditEntry("DENY", approverId, "APPROVER", comments);
        this.updatedAt = Instant.now();
    }

    /**
     * Cancels the request.
     */
    public void cancel(String userId, String reason) {
        if (status != ApprovalStatus.PENDING) {
            throw new IllegalStateException("Only pending requests can be cancelled");
        }
        
        this.status = ApprovalStatus.CANCELLED;
        this.decidedAt = Instant.now();
        this.decidedBy = userId;
        
        addAuditEntry("CANCEL", userId, "REQUESTER", reason);
        this.updatedAt = Instant.now();
    }

    /**
     * Escalates the request.
     */
    public void escalate(String userId, String reason) {
        this.status = ApprovalStatus.ESCALATED;
        
        addAuditEntry("ESCALATE", userId, "APPROVER", reason);
        this.updatedAt = Instant.now();
    }

    /**
     * Auto-approves the request.
     */
    public void autoApprove(String reason) {
        this.status = ApprovalStatus.AUTO_APPROVED;
        this.decidedAt = Instant.now();
        this.decidedBy = "system";
        
        addAuditEntry("AUTO_APPROVE", "system", "SYSTEM", reason);
        this.updatedAt = Instant.now();
    }

    /**
     * Adds an approval decision.
     */
    public void addApprovalDecision(String approverId, String approverName, 
                                  ApprovalDecision.Decision decision, String comments) {
        ApprovalDecision approvalDecision = new ApprovalDecision(
            approverId, approverName, decision, comments, Instant.now(), Map.of()
        );
        this.approvalDecisions.add(approvalDecision);
    }

    /**
     * Adds a policy decision.
     */
    public void addPolicyDecision(PolicyDecision policyDecision) {
        this.policyDecisions.add(policyDecision);
    }

    /**
     * Adds an audit entry.
     */
    public void addAuditEntry(String action, String userId, String userType, String description) {
        AuditEntry entry = new AuditEntry(action, userId, userType, description, Instant.now(), Map.of());
        this.auditTrail.add(entry);
    }

    /**
     * Checks if the request is fully approved.
     */
    public boolean isFullyApproved() {
        if (requiredApprovers.isEmpty()) return false;
        
        Set<String> approvedApprovers = approvalDecisions.stream()
            .filter(dec -> dec.decision() == ApprovalDecision.Decision.APPROVE)
            .map(ApprovalDecision::approverId)
            .collect(java.util.stream.Collectors.toSet());
        
        return approvedApprovers.containsAll(requiredApprovers);
    }

    /**
     * Checks if the request is denied.
     */
    public boolean isDenied() {
        return approvalDecisions.stream()
            .anyMatch(dec -> dec.decision() == ApprovalDecision.Decision.DENY);
    }

    /**
     * Checks if the request has expired.
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * Gets the age of the request in hours.
     */
    public Long getAgeHours() {
        if (createdAt == null) return null;
        return java.time.Duration.between(createdAt, Instant.now()).toHours();
    }

    /**
     * Checks if the request is in a terminal state.
     */
    public boolean isTerminal() {
        return status == ApprovalStatus.APPROVED || 
               status == ApprovalStatus.DENIED || 
               status == ApprovalStatus.CANCELLED || 
               status == ApprovalStatus.EXPIRED;
    }

    /**
     * Checks if the request is pending.
     */
    public boolean isPending() {
        return status == ApprovalStatus.PENDING;
    }

    /**
     * Gets the approval progress as a percentage.
     */
    public Double getApprovalProgress() {
        if (requiredApprovers.isEmpty()) return 0.0;
        
        long approvedCount = approvalDecisions.stream()
            .filter(dec -> dec.decision() == ApprovalDecision.Decision.APPROVE)
            .count();
        
        return (double) approvedCount / requiredApprovers.size() * 100.0;
    }

    // ============ Lifecycle Callbacks ============

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        
        // Add creation audit entry
        addAuditEntry("CREATE", requestedBy, "REQUESTER", "Approval request created");
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
        private String requestId;
        private String agentRunId;
        private RequestType requestType;
        private String title;
        private String description;
        private String requestedBy;
        private ApprovalStatus status = ApprovalStatus.PENDING;
        private Integer priority = 5;
        private Map<String, Object> requestData = new HashMap<>();
        private List<String> requiredApprovers = new ArrayList<>();
        private List<String> optionalApprovers = new ArrayList<>();
        private List<ApprovalDecision> approvalDecisions = new ArrayList<>();
        private List<PolicyDecision> policyDecisions = new ArrayList<>();
        private RiskAssessment riskAssessment;
        private Map<String, Object> complianceMetadata = new HashMap<>();
        private List<AuditEntry> auditTrail = new ArrayList<>();
        private Instant createdAt;
        private Instant updatedAt;
        private Instant expiresAt;
        private Instant decidedAt;
        private String decidedBy;

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder agentRunId(String agentRunId) {
            this.agentRunId = agentRunId;
            return this;
        }

        public Builder requestType(RequestType requestType) {
            this.requestType = requestType;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder requestedBy(String requestedBy) {
            this.requestedBy = requestedBy;
            return this;
        }

        public Builder status(ApprovalStatus status) {
            this.status = status;
            return this;
        }

        public Builder priority(Integer priority) {
            this.priority = priority;
            return this;
        }

        public Builder requestData(Map<String, Object> requestData) {
            this.requestData = requestData;
            return this;
        }

        public Builder requiredApprovers(List<String> requiredApprovers) {
            this.requiredApprovers = requiredApprovers;
            return this;
        }

        public Builder optionalApprovers(List<String> optionalApprovers) {
            this.optionalApprovers = optionalApprovers;
            return this;
        }

        public Builder approvalDecisions(List<ApprovalDecision> approvalDecisions) {
            this.approvalDecisions = approvalDecisions;
            return this;
        }

        public Builder policyDecisions(List<PolicyDecision> policyDecisions) {
            this.policyDecisions = policyDecisions;
            return this;
        }

        public Builder riskAssessment(RiskAssessment riskAssessment) {
            this.riskAssessment = riskAssessment;
            return this;
        }

        public Builder complianceMetadata(Map<String, Object> complianceMetadata) {
            this.complianceMetadata = complianceMetadata;
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

        public Builder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public Builder decidedAt(Instant decidedAt) {
            this.decidedAt = decidedAt;
            return this;
        }

        public Builder decidedBy(String decidedBy) {
            this.decidedBy = decidedBy;
            return this;
        }

        public ApprovalRequest build() {
            ApprovalRequest request = new ApprovalRequest();
            request.requestId = this.requestId;
            request.agentRunId = this.agentRunId;
            request.requestType = this.requestType;
            request.title = this.title;
            request.description = this.description;
            request.requestedBy = this.requestedBy;
            request.status = this.status;
            request.priority = this.priority;
            request.requestData = this.requestData;
            request.requiredApprovers = this.requiredApprovers;
            request.optionalApprovers = this.optionalApprovers;
            request.approvalDecisions = this.approvalDecisions;
            request.policyDecisions = this.policyDecisions;
            request.riskAssessment = this.riskAssessment;
            request.complianceMetadata = this.complianceMetadata;
            request.auditTrail = this.auditTrail;
            request.createdAt = this.createdAt;
            request.updatedAt = this.updatedAt;
            request.expiresAt = this.expiresAt;
            request.decidedAt = this.decidedAt;
            request.decidedBy = this.decidedBy;
            return request;
        }
    }

    @Override
    public String toString() {
        return "ApprovalRequest{" +
                "requestId='" + requestId + '\'' +
                ", requestType=" + requestType +
                ", status=" + status +
                ", priority=" + priority +
                ", ageHours=" + getAgeHours() +
                '}';
    }
}
