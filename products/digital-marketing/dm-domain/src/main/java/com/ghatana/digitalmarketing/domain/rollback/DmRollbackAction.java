package com.ghatana.digitalmarketing.domain.rollback;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable entity representing a compensating rollback action.
 *
 * @doc.type class
 * @doc.purpose Tracks compensating actions for failed campaign operations (DMOS-F2-014)
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class DmRollbackAction {

    private final String id;
    private final String tenantId;
    private final String workspaceId;
    private final String commandId;
    private final String actionType;
    private final String targetEntityId;
    private final String targetEntityType;
    private final DmRollbackStatus status;
    private final String failureReason;
    private final Instant scheduledAt;
    private final Instant executedAt;
    private final Instant createdAt;

    private DmRollbackAction(Builder b) {
        this.id               = b.id;
        this.tenantId         = b.tenantId;
        this.workspaceId      = b.workspaceId;
        this.commandId        = b.commandId;
        this.actionType       = b.actionType;
        this.targetEntityId   = b.targetEntityId;
        this.targetEntityType = b.targetEntityType;
        this.status           = b.status;
        this.failureReason    = b.failureReason;
        this.scheduledAt      = b.scheduledAt;
        this.executedAt       = b.executedAt;
        this.createdAt        = b.createdAt;
    }

    public DmRollbackAction markCompleted() {
        if (status != DmRollbackStatus.PENDING) {
            throw new IllegalStateException("Cannot complete rollback in status: " + status);
        }
        return toBuilder().status(DmRollbackStatus.COMPLETED).executedAt(Instant.now()).build();
    }

    public DmRollbackAction markFailed(String reason) {
        Objects.requireNonNull(reason, "reason must not be null");
        return toBuilder().status(DmRollbackStatus.FAILED)
            .failureReason(reason).executedAt(Instant.now()).build();
    }

    public String getId()                { return id; }
    public String getTenantId()          { return tenantId; }
    public String getWorkspaceId()       { return workspaceId; }
    public String getCommandId()         { return commandId; }
    public String getActionType()        { return actionType; }
    public String getTargetEntityId()    { return targetEntityId; }
    public String getTargetEntityType()  { return targetEntityType; }
    public DmRollbackStatus getStatus()  { return status; }
    public String getFailureReason()     { return failureReason; }
    public Instant getScheduledAt()      { return scheduledAt; }
    public Instant getExecutedAt()       { return executedAt; }
    public Instant getCreatedAt()        { return createdAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DmRollbackAction)) return false;
        return id.equals(((DmRollbackAction) o).id);
    }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() {
        return "DmRollbackAction{id='" + id + "', status=" + status + '}';
    }

    public Builder toBuilder() {
        return new Builder().id(id).tenantId(tenantId).workspaceId(workspaceId)
            .commandId(commandId).actionType(actionType).targetEntityId(targetEntityId)
            .targetEntityType(targetEntityType).status(status).failureReason(failureReason)
            .scheduledAt(scheduledAt).executedAt(executedAt).createdAt(createdAt);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id, tenantId, workspaceId, commandId, actionType;
        private String targetEntityId, targetEntityType, failureReason;
        private DmRollbackStatus status;
        private Instant scheduledAt, executedAt, createdAt;

        public Builder id(String v)                { this.id = v; return this; }
        public Builder tenantId(String v)          { this.tenantId = v; return this; }
        public Builder workspaceId(String v)       { this.workspaceId = v; return this; }
        public Builder commandId(String v)         { this.commandId = v; return this; }
        public Builder actionType(String v)        { this.actionType = v; return this; }
        public Builder targetEntityId(String v)    { this.targetEntityId = v; return this; }
        public Builder targetEntityType(String v)  { this.targetEntityType = v; return this; }
        public Builder status(DmRollbackStatus v)  { this.status = v; return this; }
        public Builder failureReason(String v)     { this.failureReason = v; return this; }
        public Builder scheduledAt(Instant v)      { this.scheduledAt = v; return this; }
        public Builder executedAt(Instant v)       { this.executedAt = v; return this; }
        public Builder createdAt(Instant v)        { this.createdAt = v; return this; }

        public DmRollbackAction build() {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
            if (actionType == null || actionType.isBlank()) throw new IllegalArgumentException("actionType must not be blank");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            return new DmRollbackAction(this);
        }
    }
}
