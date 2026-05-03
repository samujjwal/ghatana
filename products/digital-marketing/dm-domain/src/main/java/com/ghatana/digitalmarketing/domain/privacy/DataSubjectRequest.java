package com.ghatana.digitalmarketing.domain.privacy;

import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;

import java.time.Instant;
import java.util.Objects;

/**
 * Domain entity representing a data subject request (DSR) for privacy compliance (DMOS-P1-017).
 *
 * <p>Supports GDPR/CCPA data subject rights: export, deletion, correction, processing restriction,
 * and consent withdrawal. Each request is auditable and trackable.</p>
 *
 * @doc.type class
 * @doc.purpose Represents privacy lifecycle workflows for DSRs (DMOS-P1-017)
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class DataSubjectRequest {

    private final String id;
    private final DmTenantId tenantId;
    private final DmWorkspaceId workspaceId;
    private final RequestType requestType;
    private final String contactPointHash; // Hashed contact point (email/phone)
    private final RequestStatus status;
    private final Instant submittedAt;
    private final String submittedBy;
    private final Instant completedAt;
    private final String completedBy;
    private final String rejectionReason;
    private final String evidenceLocation; // Location of audit evidence

    private DataSubjectRequest(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id must not be null");
        this.tenantId = Objects.requireNonNull(builder.tenantId, "tenantId must not be null");
        this.workspaceId = Objects.requireNonNull(builder.workspaceId, "workspaceId must not be null");
        this.requestType = Objects.requireNonNull(builder.requestType, "requestType must not be null");
        this.contactPointHash = Objects.requireNonNull(builder.contactPointHash, "contactPointHash must not be null");
        this.status = Objects.requireNonNull(builder.status, "status must not be null");
        this.submittedAt = Objects.requireNonNull(builder.submittedAt, "submittedAt must not be null");
        this.submittedBy = Objects.requireNonNull(builder.submittedBy, "submittedBy must not be null");
        this.completedAt = builder.completedAt;
        this.completedBy = builder.completedBy;
        this.rejectionReason = builder.rejectionReason;
        this.evidenceLocation = builder.evidenceLocation;

        if (this.id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (this.contactPointHash.isBlank()) {
            throw new IllegalArgumentException("contactPointHash must not be blank");
        }
    }

    /**
     * Returns a completed request with evidence location.
     */
    public DataSubjectRequest complete(String completedBy, String evidenceLocation) {
        if (status != RequestStatus.PENDING) {
            throw new IllegalStateException("Request is not pending");
        }
        return toBuilder()
            .status(RequestStatus.COMPLETED)
            .completedAt(Instant.now())
            .completedBy(completedBy)
            .evidenceLocation(evidenceLocation)
            .build();
    }

    /**
     * Returns a rejected request with reason.
     */
    public DataSubjectRequest reject(String rejectedBy, String rejectionReason) {
        if (status != RequestStatus.PENDING) {
            throw new IllegalStateException("Request is not pending");
        }
        return toBuilder()
            .status(RequestStatus.REJECTED)
            .completedAt(Instant.now())
            .completedBy(rejectedBy)
            .rejectionReason(rejectionReason)
            .build();
    }

    /**
     * Returns a request marked as in progress.
     */
    public DataSubjectRequest markInProgress() {
        if (status != RequestStatus.PENDING) {
            throw new IllegalStateException("Request is not pending");
        }
        return toBuilder().status(RequestStatus.IN_PROGRESS).build();
    }

    public String getId() { return id; }
    public DmTenantId getTenantId() { return tenantId; }
    public DmWorkspaceId getWorkspaceId() { return workspaceId; }
    public RequestType getRequestType() { return requestType; }
    public String getContactPointHash() { return contactPointHash; }
    public RequestStatus getStatus() { return status; }
    public Instant getSubmittedAt() { return submittedAt; }
    public String getSubmittedBy() { return submittedBy; }
    public Instant getCompletedAt() { return completedAt; }
    public String getCompletedBy() { return completedBy; }
    public String getRejectionReason() { return rejectionReason; }
    public String getEvidenceLocation() { return evidenceLocation; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DataSubjectRequest other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        // Redact contact point hash from logs (DMOS-P1-017)
        return "DataSubjectRequest{id='" + id + "', tenantId='" + tenantId + "', workspaceId='" + workspaceId + "', requestType=" + requestType + ", status=" + status + "}";
    }

    public Builder toBuilder() {
        return new Builder()
            .id(id)
            .tenantId(tenantId)
            .workspaceId(workspaceId)
            .requestType(requestType)
            .contactPointHash(contactPointHash)
            .status(status)
            .submittedAt(submittedAt)
            .submittedBy(submittedBy)
            .completedAt(completedAt)
            .completedBy(completedBy)
            .rejectionReason(rejectionReason)
            .evidenceLocation(evidenceLocation);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private DmTenantId tenantId;
        private DmWorkspaceId workspaceId;
        private RequestType requestType;
        private String contactPointHash;
        private RequestStatus status = RequestStatus.PENDING;
        private Instant submittedAt;
        private String submittedBy;
        private Instant completedAt;
        private String completedBy;
        private String rejectionReason;
        private String evidenceLocation;

        public Builder id(String id) { this.id = id; return this; }
        public Builder tenantId(DmTenantId tenantId) { this.tenantId = tenantId; return this; }
        public Builder workspaceId(DmWorkspaceId workspaceId) { this.workspaceId = workspaceId; return this; }
        public Builder requestType(RequestType requestType) { this.requestType = requestType; return this; }
        public Builder contactPointHash(String contactPointHash) { this.contactPointHash = contactPointHash; return this; }
        public Builder status(RequestStatus status) { this.status = status; return this; }
        public Builder submittedAt(Instant submittedAt) { this.submittedAt = submittedAt; return this; }
        public Builder submittedBy(String submittedBy) { this.submittedBy = submittedBy; return this; }
        public Builder completedAt(Instant completedAt) { this.completedAt = completedAt; return this; }
        public Builder completedBy(String completedBy) { this.completedBy = completedBy; return this; }
        public Builder rejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; return this; }
        public Builder evidenceLocation(String evidenceLocation) { this.evidenceLocation = evidenceLocation; return this; }

        public DataSubjectRequest build() {
            return new DataSubjectRequest(this);
        }
    }

    /**
     * Types of data subject requests (DMOS-P1-017).
     */
    public enum RequestType {
        DATA_EXPORT,
        DATA_DELETION,
        DATA_CORRECTION,
        PROCESSING_RESTRICTION,
        CONSENT_WITHDRAWAL
    }

    /**
     * Status of data subject requests (DMOS-P1-017).
     */
    public enum RequestStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        REJECTED
    }
}
