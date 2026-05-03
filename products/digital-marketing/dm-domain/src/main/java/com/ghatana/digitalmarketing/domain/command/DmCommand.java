package com.ghatana.digitalmarketing.domain.command;

import java.time.Instant;
import java.util.Objects;

/**
 * An immutable, durable DMOS command record.
 *
 * <p>Commands are the write-side of CQRS in DMOS. They are created when an
 * agent recommendation or user action triggers a state change on an external
 * platform (e.g. Google Ads, a landing page CDN). Each command stores a
 * JSON-serialised payload so the executor can replay it without re-querying
 * the domain.</p>
 *
 * <p>Status transitions:
 * {@code PENDING → EXECUTING → SUCCEEDED}
 * {@code PENDING → EXECUTING → FAILED → ROLLED_BACK}</p>
 *
 * @doc.type class
 * @doc.purpose Durable CQRS command entity for DMOS external-platform actions (DMOS-F2-003)
 * @doc.layer product
 * @doc.pattern CQRS, Command, Value Object
 */
public final class DmCommand {

    /** Maximum execution attempts before the command is permanently FAILED. */
    public static final int MAX_ATTEMPTS = 3;

    private final String id;
    private final DmCommandType commandType;
    private final String tenantId;
    private final String workspaceId;
    private final String correlationId;
    private final String issuedBy;
    private final String serializedPayload;
    private final DmCommandStatus status;
    private final int attemptCount;
    private final Instant createdAt;
    private final Instant scheduledAt;
    private final Instant executedAt;
    private final Instant completedAt;
    private final String failureReason;

    private DmCommand(Builder builder) {
        this.id                = requireNonBlank(builder.id, "id");
        this.commandType       = Objects.requireNonNull(builder.commandType, "commandType must not be null");
        this.tenantId          = requireNonBlank(builder.tenantId, "tenantId");
        this.workspaceId       = requireNonBlank(builder.workspaceId, "workspaceId");
        this.correlationId     = requireNonBlank(builder.correlationId, "correlationId");
        this.issuedBy          = requireNonBlank(builder.issuedBy, "issuedBy");
        this.serializedPayload = requireNonBlank(builder.serializedPayload, "serializedPayload");
        this.status            = Objects.requireNonNull(builder.status, "status must not be null");
        this.attemptCount      = builder.attemptCount;
        this.createdAt         = Objects.requireNonNull(builder.createdAt, "createdAt must not be null");
        this.scheduledAt       = Objects.requireNonNull(builder.scheduledAt, "scheduledAt must not be null");
        this.executedAt        = builder.executedAt;
        this.completedAt       = builder.completedAt;
        this.failureReason     = builder.failureReason;
        if (this.attemptCount < 0) throw new IllegalArgumentException("attemptCount must be >= 0");
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(field + " must not be null or blank");
        return value;
    }

    /** Returns a copy of this command marked EXECUTING. */
    public DmCommand markExecuting() {
        return toBuilder().status(DmCommandStatus.EXECUTING)
                          .executedAt(Instant.now())
                          .attemptCount(attemptCount + 1)
                          .build();
    }

    /** Returns a copy of this command marked SUCCEEDED. */
    public DmCommand markSucceeded() {
        return toBuilder().status(DmCommandStatus.SUCCEEDED)
                          .completedAt(Instant.now())
                          .build();
    }

    /**
     * Returns a copy of this command marked FAILED.
     *
     * @param reason failure reason for diagnostics
     */
    public DmCommand markFailed(String reason) {
        return toBuilder().status(DmCommandStatus.FAILED)
                          .completedAt(Instant.now())
                          .failureReason(reason != null ? reason : "unknown")
                          .build();
    }

    /** Returns a copy of this command marked ROLLED_BACK. */
    public DmCommand markRolledBack() {
        return toBuilder().status(DmCommandStatus.ROLLED_BACK)
                          .build();
    }

    /** @return {@code true} if command can be retried (PENDING or FAILED within attempt limit). */
    public boolean isRetryable() {
        return (status == DmCommandStatus.PENDING || status == DmCommandStatus.FAILED)
            && attemptCount < MAX_ATTEMPTS;
    }

    private Builder toBuilder() {
        return new Builder()
            .id(id).commandType(commandType).tenantId(tenantId).workspaceId(workspaceId)
            .correlationId(correlationId).issuedBy(issuedBy).serializedPayload(serializedPayload)
            .status(status).attemptCount(attemptCount).createdAt(createdAt).scheduledAt(scheduledAt)
            .executedAt(executedAt).completedAt(completedAt).failureReason(failureReason);
    }

    public String getId() { return id; }
    public DmCommandType getCommandType() { return commandType; }
    public String getTenantId() { return tenantId; }
    public String getWorkspaceId() { return workspaceId; }
    public String getCorrelationId() { return correlationId; }
    public String getIssuedBy() { return issuedBy; }
    public String getSerializedPayload() { return serializedPayload; }
    public DmCommandStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getScheduledAt() { return scheduledAt; }
    public Instant getExecutedAt() { return executedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getFailureReason() { return failureReason; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DmCommand other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "DmCommand{id=" + id + ", type=" + commandType + ", status=" + status + '}';
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private DmCommandType commandType;
        private String tenantId;
        private String workspaceId;
        private String correlationId;
        private String issuedBy;
        private String serializedPayload;
        private DmCommandStatus status = DmCommandStatus.PENDING;
        private int attemptCount;
        private Instant createdAt;
        private Instant scheduledAt;
        private Instant executedAt;
        private Instant completedAt;
        private String failureReason;

        private Builder() {}

        public Builder id(String id) { this.id = id; return this; }
        public Builder commandType(DmCommandType v) { this.commandType = v; return this; }
        public Builder tenantId(String v) { this.tenantId = v; return this; }
        public Builder workspaceId(String v) { this.workspaceId = v; return this; }
        public Builder correlationId(String v) { this.correlationId = v; return this; }
        public Builder issuedBy(String v) { this.issuedBy = v; return this; }
        public Builder serializedPayload(String v) { this.serializedPayload = v; return this; }
        public Builder status(DmCommandStatus v) { this.status = v; return this; }
        public Builder attemptCount(int v) { this.attemptCount = v; return this; }
        public Builder createdAt(Instant v) { this.createdAt = v; return this; }
        public Builder scheduledAt(Instant v) { this.scheduledAt = v; return this; }
        public Builder executedAt(Instant v) { this.executedAt = v; return this; }
        public Builder completedAt(Instant v) { this.completedAt = v; return this; }
        public Builder failureReason(String v) { this.failureReason = v; return this; }

        public DmCommand build() { return new DmCommand(this); }
    }
}
