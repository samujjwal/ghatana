package com.ghatana.digitalmarketing.domain.email;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable entity representing an email follow-up or export job.
 *
 * @doc.type class
 * @doc.purpose Domain entity for email follow-up execution (DMOS-F2-012)
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class DmEmailFollowUp {

    private final String id;
    private final String tenantId;
    private final String workspaceId;
    private final String connectorId;
    private final List<String> recipientEmails;
    private final String subject;
    private final String bodyHtml;
    private final DmEmailFollowUpStatus status;
    private final int sentCount;
    private final int failedCount;
    private final String failureReason;
    private final Instant scheduledAt;
    private final Instant executedAt;
    private final Instant createdAt;

    private DmEmailFollowUp(Builder b) {
        this.id               = b.id;
        this.tenantId         = b.tenantId;
        this.workspaceId      = b.workspaceId;
        this.connectorId      = b.connectorId;
        this.recipientEmails  = List.copyOf(b.recipientEmails);
        this.subject          = b.subject;
        this.bodyHtml         = b.bodyHtml;
        this.status           = b.status;
        this.sentCount        = b.sentCount;
        this.failedCount      = b.failedCount;
        this.failureReason    = b.failureReason;
        this.scheduledAt      = b.scheduledAt;
        this.executedAt       = b.executedAt;
        this.createdAt        = b.createdAt;
    }

    public DmEmailFollowUp markSent(int sentCount, int failedCount) {
        if (status != DmEmailFollowUpStatus.PENDING) {
            throw new IllegalStateException("markSent requires PENDING status, was: " + status);
        }
        return toBuilder().status(DmEmailFollowUpStatus.SENT)
            .sentCount(sentCount).failedCount(failedCount)
            .executedAt(Instant.now()).build();
    }

    public DmEmailFollowUp markFailed(String reason) {
        return toBuilder().status(DmEmailFollowUpStatus.FAILED)
            .failureReason(reason).executedAt(Instant.now()).build();
    }

    public DmEmailFollowUp cancel() {
        if (status != DmEmailFollowUpStatus.PENDING) {
            throw new IllegalStateException("cancel requires PENDING status, was: " + status);
        }
        return toBuilder().status(DmEmailFollowUpStatus.CANCELLED).executedAt(Instant.now()).build();
    }

    public String getId()                   { return id; }
    public String getTenantId()             { return tenantId; }
    public String getWorkspaceId()          { return workspaceId; }
    public String getConnectorId()          { return connectorId; }
    public List<String> getRecipientEmails() { return recipientEmails; }
    public String getSubject()              { return subject; }
    public String getBodyHtml()             { return bodyHtml; }
    public DmEmailFollowUpStatus getStatus() { return status; }
    public int getSentCount()               { return sentCount; }
    public int getFailedCount()             { return failedCount; }
    public String getFailureReason()        { return failureReason; }
    public Instant getScheduledAt()         { return scheduledAt; }
    public Instant getExecutedAt()          { return executedAt; }
    public Instant getCreatedAt()           { return createdAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DmEmailFollowUp)) return false;
        return id.equals(((DmEmailFollowUp) o).id);
    }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() {
        return "DmEmailFollowUp{id='" + id + "', status=" + status + '}';
    }

    public Builder toBuilder() {
        return new Builder().id(id).tenantId(tenantId).workspaceId(workspaceId)
            .connectorId(connectorId).recipientEmails(recipientEmails)
            .subject(subject).bodyHtml(bodyHtml).status(status)
            .sentCount(sentCount).failedCount(failedCount)
            .failureReason(failureReason).scheduledAt(scheduledAt)
            .executedAt(executedAt).createdAt(createdAt);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id, tenantId, workspaceId, connectorId, subject, bodyHtml;
        private List<String> recipientEmails = List.of();
        private DmEmailFollowUpStatus status;
        private int sentCount, failedCount;
        private String failureReason;
        private Instant scheduledAt, executedAt, createdAt;

        public Builder id(String v)                         { this.id = v; return this; }
        public Builder tenantId(String v)                   { this.tenantId = v; return this; }
        public Builder workspaceId(String v)                { this.workspaceId = v; return this; }
        public Builder connectorId(String v)                { this.connectorId = v; return this; }
        public Builder recipientEmails(List<String> v)      { this.recipientEmails = v; return this; }
        public Builder subject(String v)                    { this.subject = v; return this; }
        public Builder bodyHtml(String v)                   { this.bodyHtml = v; return this; }
        public Builder status(DmEmailFollowUpStatus v)      { this.status = v; return this; }
        public Builder sentCount(int v)                     { this.sentCount = v; return this; }
        public Builder failedCount(int v)                   { this.failedCount = v; return this; }
        public Builder failureReason(String v)              { this.failureReason = v; return this; }
        public Builder scheduledAt(Instant v)               { this.scheduledAt = v; return this; }
        public Builder executedAt(Instant v)                { this.executedAt = v; return this; }
        public Builder createdAt(Instant v)                 { this.createdAt = v; return this; }

        public DmEmailFollowUp build() {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
            if (subject == null || subject.isBlank()) throw new IllegalArgumentException("subject must not be blank");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            Objects.requireNonNull(recipientEmails, "recipientEmails must not be null");
            return new DmEmailFollowUp(this);
        }
    }
}
