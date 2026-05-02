package com.ghatana.digitalmarketing.domain.consent;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable consent proof snapshot captured when consent state transitions occur.
 *
 * @doc.type class
 * @doc.purpose DMOS durable consent proof entity for consent lifecycle evidence
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class ConsentProofSnapshot {

    private final String snapshotId;
    private final String contactId;
    private final DmWorkspaceId workspaceId;
    private final String consentStatus;
    private final String consentPurpose;
    private final String evidenceType;
    private final String evidenceReference;
    private final Instant recordedAt;
    private final String recordedBy;
    private final String correlationId;

    private ConsentProofSnapshot(Builder builder) {
        this.snapshotId = Objects.requireNonNull(builder.snapshotId, "snapshotId must not be null");
        this.contactId = Objects.requireNonNull(builder.contactId, "contactId must not be null");
        this.workspaceId = Objects.requireNonNull(builder.workspaceId, "workspaceId must not be null");
        this.consentStatus = Objects.requireNonNull(builder.consentStatus, "consentStatus must not be null");
        this.consentPurpose = builder.consentPurpose != null ? builder.consentPurpose : "";
        this.evidenceType = Objects.requireNonNull(builder.evidenceType, "evidenceType must not be null");
        this.evidenceReference = Objects.requireNonNull(builder.evidenceReference, "evidenceReference must not be null");
        this.recordedAt = Objects.requireNonNull(builder.recordedAt, "recordedAt must not be null");
        this.recordedBy = Objects.requireNonNull(builder.recordedBy, "recordedBy must not be null");
        this.correlationId = Objects.requireNonNull(builder.correlationId, "correlationId must not be null");

        if (this.snapshotId.isBlank()) {
            throw new IllegalArgumentException("snapshotId must not be blank");
        }
        if (this.contactId.isBlank()) {
            throw new IllegalArgumentException("contactId must not be blank");
        }
        if (this.evidenceType.isBlank()) {
            throw new IllegalArgumentException("evidenceType must not be blank");
        }
        if (this.evidenceReference.isBlank()) {
            throw new IllegalArgumentException("evidenceReference must not be blank");
        }
    }

    public String getSnapshotId() {
        return snapshotId;
    }

    public String getContactId() {
        return contactId;
    }

    public DmWorkspaceId getWorkspaceId() {
        return workspaceId;
    }

    public String getConsentStatus() {
        return consentStatus;
    }

    public String getConsentPurpose() {
        return consentPurpose;
    }

    public String getEvidenceType() {
        return evidenceType;
    }

    public String getEvidenceReference() {
        return evidenceReference;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public String getRecordedBy() {
        return recordedBy;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String snapshotId;
        private String contactId;
        private DmWorkspaceId workspaceId;
        private String consentStatus;
        private String consentPurpose;
        private String evidenceType;
        private String evidenceReference;
        private Instant recordedAt;
        private String recordedBy;
        private String correlationId;

        private Builder() {
        }

        public Builder snapshotId(String snapshotId) {
            this.snapshotId = snapshotId;
            return this;
        }

        public Builder contactId(String contactId) {
            this.contactId = contactId;
            return this;
        }

        public Builder workspaceId(DmWorkspaceId workspaceId) {
            this.workspaceId = workspaceId;
            return this;
        }

        public Builder consentStatus(String consentStatus) {
            this.consentStatus = consentStatus;
            return this;
        }

        public Builder consentPurpose(String consentPurpose) {
            this.consentPurpose = consentPurpose;
            return this;
        }

        public Builder evidenceType(String evidenceType) {
            this.evidenceType = evidenceType;
            return this;
        }

        public Builder evidenceReference(String evidenceReference) {
            this.evidenceReference = evidenceReference;
            return this;
        }

        public Builder recordedAt(Instant recordedAt) {
            this.recordedAt = recordedAt;
            return this;
        }

        public Builder recordedBy(String recordedBy) {
            this.recordedBy = recordedBy;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public ConsentProofSnapshot build() {
            return new ConsentProofSnapshot(this);
        }
    }
}
