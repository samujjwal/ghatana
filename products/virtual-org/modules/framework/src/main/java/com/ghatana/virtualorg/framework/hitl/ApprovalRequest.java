package com.ghatana.virtualorg.framework.hitl;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Request for human approval on an agent action.
 *
 * <p>
 * <b>Purpose</b><br>
 * Represents a pending approval request that pauses agent execution until a
 * human approves or rejects the proposed action.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * ApprovalRequest request = ApprovalRequest.builder()
 *     .id("approval-123")
 *     .action("Deploy to production")
 *     .requestorAgentId("devops-agent-001")
 *     .context(ApprovalContext.of("Deploy service v2.1.0"))
 *     .timeout(Duration.ofHours(24))
 *     .build();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Approval request value object
 * @doc.layer product
 * @doc.pattern Value Object
 */
public final class ApprovalRequest {

    private final String id;
    private final String action;
    private final String description;
    private final String requestorAgentId;
    private final String requestorAgentName;
    private final ApprovalContext context;
    private final ApprovalStatus status;
    private final Duration timeout;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final String requiredRole;
    private final ApprovalPriority priority;

    private ApprovalRequest(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id required");
        this.action = Objects.requireNonNull(builder.action, "action required");
        this.description = builder.description != null ? builder.description : builder.action;
        this.requestorAgentId = Objects.requireNonNull(builder.requestorAgentId, "requestorAgentId required");
        this.requestorAgentName = builder.requestorAgentName != null ? builder.requestorAgentName : builder.requestorAgentId;
        this.context = builder.context != null ? builder.context : ApprovalContext.empty();
        this.status = builder.status != null ? builder.status : ApprovalStatus.PENDING;
        this.timeout = builder.timeout != null ? builder.timeout : Duration.ofHours(24);
        this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
        this.expiresAt = this.createdAt.plus(this.timeout);
        this.requiredRole = builder.requiredRole;
        this.priority = builder.priority != null ? builder.priority : ApprovalPriority.NORMAL;
    }

    // ========== Getters ==========
    public String getId() {
        return id;
    }

    public String getAction() {
        return action;
    }

    public String getDescription() {
        return description;
    }

    public String getRequestorAgentId() {
        return requestorAgentId;
    }

    public String getRequestorAgentName() {
        return requestorAgentName;
    }

    public ApprovalContext getContext() {
        return context;
    }

    public ApprovalStatus getStatus() {
        return status;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public String getRequiredRole() {
        return requiredRole;
    }

    public ApprovalPriority getPriority() {
        return priority;
    }

    /**
     * Checks if this request has expired.
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Checks if this request is still pending.
     */
    public boolean isPending() {
        return status == ApprovalStatus.PENDING && !isExpired();
    }

    /**
     * Creates a copy with updated status.
     */
    public ApprovalRequest withStatus(ApprovalStatus newStatus) {
        return new Builder(this).status(newStatus).build();
    }

    // ========== Builder ==========
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String id;
        private String action;
        private String description;
        private String requestorAgentId;
        private String requestorAgentName;
        private ApprovalContext context;
        private ApprovalStatus status;
        private Duration timeout;
        private Instant createdAt;
        private String requiredRole;
        private ApprovalPriority priority;

        private Builder() {
        }

        private Builder(ApprovalRequest source) {
            this.id = source.id;
            this.action = source.action;
            this.description = source.description;
            this.requestorAgentId = source.requestorAgentId;
            this.requestorAgentName = source.requestorAgentName;
            this.context = source.context;
            this.status = source.status;
            this.timeout = source.timeout;
            this.createdAt = source.createdAt;
            this.requiredRole = source.requiredRole;
            this.priority = source.priority;
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder action(String action) {
            this.action = action;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder requestorAgentId(String requestorAgentId) {
            this.requestorAgentId = requestorAgentId;
            return this;
        }

        public Builder requestorAgentName(String requestorAgentName) {
            this.requestorAgentName = requestorAgentName;
            return this;
        }

        public Builder context(ApprovalContext context) {
            this.context = context;
            return this;
        }

        public Builder status(ApprovalStatus status) {
            this.status = status;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder requiredRole(String requiredRole) {
            this.requiredRole = requiredRole;
            return this;
        }

        public Builder priority(ApprovalPriority priority) {
            this.priority = priority;
            return this;
        }

        public ApprovalRequest build() {
            return new ApprovalRequest(this);
        }
    }

    @Override
    public String toString() {
        return "ApprovalRequest{"
                + "id='" + id + '\''
                + ", action='" + action + '\''
                + ", status=" + status
                + ", priority=" + priority
                + ", expiresAt=" + expiresAt
                + '}';
    }

    // ========== Enums ==========
    public enum ApprovalPriority {
        LOW,
        NORMAL,
        HIGH,
        URGENT
    }
}
