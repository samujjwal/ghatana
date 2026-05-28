package com.ghatana.aep.pattern.lifecycle;

import io.activej.promise.Promise;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Human-in-the-loop (HITL) service for pattern lifecycle approvals.
 *
 * <p><b>Purpose</b><br>
 * Provides human approval workflows for sensitive lifecycle transitions
 * such as promotion to ACTIVE or RETIREMENT of production patterns.
 *
 * <p><b>Capabilities</b><br>
 * <ul>
 *   <li><b>Approval Requests</b>: Create approval requests for sensitive transitions</li>
 *   <li><b>Approval Flow</b>: Track approval status and required approvers</li>
 *   <li><b>Rejection</b>: Handle rejected transitions with feedback</li>
 *   <li><b>Escalation</b>: Escalate stuck approvals to higher authorities</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Human-in-the-loop approval workflows for pattern lifecycle
 * @doc.layer product
 * @doc.pattern Service
 */
public final class PatternHITLService {

    private final Clock clock;
    private final ApprovalRepository approvalRepository;

    public PatternHITLService(Clock clock, ApprovalRepository approvalRepository) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.approvalRepository = Objects.requireNonNull(approvalRepository, "approvalRepository");
    }

    /**
     * Creates an approval request for a sensitive lifecycle transition.
     *
     * @param patternId pattern identifier
     * @param tenantId tenant identifier
     * @param transition the proposed transition
     * @param requestedBy user requesting the transition
     * @param requiredApprovers list of required approvers
     * @return Promise of approval request
     */
    public Promise<ApprovalRequest> createApprovalRequest(
            String patternId,
            String tenantId,
            PatternLifecycleTransition transition,
            String requestedBy,
            java.util.List<String> requiredApprovers) {
        ApprovalRequest request = ApprovalRequest.builder()
            .requestId(UUID.randomUUID().toString())
            .patternId(patternId)
            .tenantId(tenantId)
            .transition(transition)
            .requestedBy(requestedBy)
            .requiredApprovers(requiredApprovers)
            .status(ApprovalStatus.PENDING)
            .createdAt(clock.instant())
            .expiresAt(clock.instant().plus(java.time.Duration.ofHours(24)))
            .build();

        return approvalRepository.save(request);
    }

    /**
     * Approves a transition request.
     *
     * @param requestId approval request ID
     * @param approver user approving the request
     * @param comment optional approval comment
     * @return Promise of updated approval request
     */
    public Promise<ApprovalRequest> approve(String requestId, String approver, String comment) {
        return approvalRepository.findById(requestId)
            .then(optRequest -> {
                if (optRequest.isEmpty()) {
                    return Promise.ofException(new IllegalArgumentException("Approval request not found: " + requestId));
                }
                ApprovalRequest request = optRequest.get();
                if (request.status() != ApprovalStatus.PENDING) {
                    return Promise.ofException(new IllegalStateException("Request is not pending: " + request.status()));
                }
                if (!request.requiredApprovers().contains(approver)) {
                    return Promise.ofException(new IllegalArgumentException("User is not a required approver: " + approver));
                }

                ApprovalRequest updated = request.toBuilder()
                    .addApproval(approver, comment, clock.instant())
                    .build();

                // Check if all required approvers have approved
                if (updated.allRequiredApprovalsReceived()) {
                    updated = updated.toBuilder()
                        .status(ApprovalStatus.APPROVED)
                        .completedAt(clock.instant())
                        .build();
                }

                return approvalRepository.save(updated);
            });
    }

    /**
     * Rejects a transition request.
     *
     * @param requestId approval request ID
     * @param approver user rejecting the request
     * @param reason rejection reason
     * @return Promise of updated approval request
     */
    public Promise<ApprovalRequest> reject(String requestId, String approver, String reason) {
        return approvalRepository.findById(requestId)
            .then(optRequest -> {
                if (optRequest.isEmpty()) {
                    return Promise.ofException(new IllegalArgumentException("Approval request not found: " + requestId));
                }
                ApprovalRequest request = optRequest.get();
                if (request.status() != ApprovalStatus.PENDING) {
                    return Promise.ofException(new IllegalStateException("Request is not pending: " + request.status()));
                }
                if (!request.requiredApprovers().contains(approver)) {
                    return Promise.ofException(new IllegalArgumentException("User is not a required approver: " + approver));
                }

                ApprovalRequest updated = request.toBuilder()
                    .status(ApprovalStatus.REJECTED)
                    .rejectedBy(approver)
                    .rejectionReason(reason)
                    .completedAt(clock.instant())
                    .build();

                return approvalRepository.save(updated);
            });
    }

    /**
     * Escalates a stuck approval request to higher authorities.
     *
     * @param requestId approval request ID
     * @param escalatedBy user escalating the request
     * @param escalationReason reason for escalation
     * @param escalatedTo list of users to escalate to
     * @return Promise of updated approval request
     */
    public Promise<ApprovalRequest> escalate(
            String requestId,
            String escalatedBy,
            String escalationReason,
            java.util.List<String> escalatedTo) {
        return approvalRepository.findById(requestId)
            .then(optRequest -> {
                if (optRequest.isEmpty()) {
                    return Promise.ofException(new IllegalArgumentException("Approval request not found: " + requestId));
                }
                ApprovalRequest request = optRequest.get();
                if (request.status() != ApprovalStatus.PENDING) {
                    return Promise.ofException(new IllegalStateException("Request is not pending: " + request.status()));
                }

                ApprovalRequest updated = request.toBuilder()
                    .status(ApprovalStatus.ESCALATED)
                    .escalatedBy(escalatedBy)
                    .escalationReason(escalationReason)
                    .escalatedAt(clock.instant())
                    .requiredApprovers(escalatedTo)
                    .build();

                return approvalRepository.save(updated);
            });
    }

    /**
     * Gets the approval status for a pattern.
     *
     * @param patternId pattern identifier
     * @param tenantId tenant identifier
     * @return Promise of approval status
     */
    public Promise<ApprovalStatus> getApprovalStatus(String patternId, String tenantId) {
        return approvalRepository.findByPatternId(patternId, tenantId)
            .map(requests -> {
                if (requests.isEmpty()) {
                    return ApprovalStatus.NOT_REQUIRED;
                }
                // Return the status of the most recent request
                return requests.get(0).status();
            });
    }

    // ==================== Data Models ====================

    public enum ApprovalStatus {
        NOT_REQUIRED,
        PENDING,
        APPROVED,
        REJECTED,
        ESCALATED,
        EXPIRED
    }

    public record ApprovalRequest(
        String requestId,
        String patternId,
        String tenantId,
        PatternLifecycleTransition transition,
        String requestedBy,
        java.util.List<String> requiredApprovers,
        ApprovalStatus status,
        Instant createdAt,
        Instant expiresAt,
        Instant completedAt,
        java.util.List<Approval> approvals,
        String rejectedBy,
        String rejectionReason,
        String escalatedBy,
        String escalationReason,
        Instant escalatedAt
    ) {
        public boolean allRequiredApprovalsReceived() {
            return approvals != null && approvals.size() >= requiredApprovers.size();
        }

        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }

        public static Builder builder() {
            return new Builder();
        }

        public Builder toBuilder() {
            return new Builder(this);
        }

        public static class Builder {
            private String requestId;
            private String patternId;
            private String tenantId;
            private PatternLifecycleTransition transition;
            private String requestedBy;
            private java.util.List<String> requiredApprovers;
            private ApprovalStatus status;
            private Instant createdAt;
            private Instant expiresAt;
            private Instant completedAt;
            private java.util.List<Approval> approvals = new java.util.ArrayList<>();
            private String rejectedBy;
            private String rejectionReason;
            private String escalatedBy;
            private String escalationReason;
            private Instant escalatedAt;

            private Builder() {}

            private Builder(ApprovalRequest request) {
                this.requestId = request.requestId;
                this.patternId = request.patternId;
                this.tenantId = request.tenantId;
                this.transition = request.transition;
                this.requestedBy = request.requestedBy;
                this.requiredApprovers = request.requiredApprovers;
                this.status = request.status;
                this.createdAt = request.createdAt;
                this.expiresAt = request.expiresAt;
                this.completedAt = request.completedAt;
                this.approvals = new java.util.ArrayList<>(request.approvals);
                this.rejectedBy = request.rejectedBy;
                this.rejectionReason = request.rejectionReason;
                this.escalatedBy = request.escalatedBy;
                this.escalationReason = request.escalationReason;
                this.escalatedAt = request.escalatedAt;
            }

            public Builder requestId(String requestId) { this.requestId = requestId; return this; }
            public Builder patternId(String patternId) { this.patternId = patternId; return this; }
            public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
            public Builder transition(PatternLifecycleTransition transition) { this.transition = transition; return this; }
            public Builder requestedBy(String requestedBy) { this.requestedBy = requestedBy; return this; }
            public Builder requiredApprovers(java.util.List<String> requiredApprovers) { this.requiredApprovers = requiredApprovers; return this; }
            public Builder status(ApprovalStatus status) { this.status = status; return this; }
            public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
            public Builder expiresAt(Instant expiresAt) { this.expiresAt = expiresAt; return this; }
            public Builder completedAt(Instant completedAt) { this.completedAt = completedAt; return this; }
            public Builder rejectedBy(String rejectedBy) { this.rejectedBy = rejectedBy; return this; }
            public Builder rejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; return this; }
            public Builder escalatedBy(String escalatedBy) { this.escalatedBy = escalatedBy; return this; }
            public Builder escalationReason(String escalationReason) { this.escalationReason = escalationReason; return this; }
            public Builder escalatedAt(Instant escalatedAt) { this.escalatedAt = escalatedAt; return this; }

            public Builder addApproval(String approver, String comment, Instant approvedAt) {
                this.approvals.add(new Approval(approver, comment, approvedAt));
                return this;
            }

            public ApprovalRequest build() {
                return new ApprovalRequest(
                    requestId, patternId, tenantId, transition, requestedBy,
                    requiredApprovers, status, createdAt, expiresAt, completedAt,
                    approvals, rejectedBy, rejectionReason, escalatedBy, escalationReason, escalatedAt
                );
            }
        }
    }

    public record Approval(
        String approver,
        String comment,
        Instant approvedAt
    ) {}

    // ==================== Repository Interface ====================

    public interface ApprovalRepository {
        Promise<ApprovalRequest> save(ApprovalRequest request);
        Promise<java.util.Optional<ApprovalRequest>> findById(String requestId);
        Promise<java.util.List<ApprovalRequest>> findByPatternId(String patternId, String tenantId);
    }
}
