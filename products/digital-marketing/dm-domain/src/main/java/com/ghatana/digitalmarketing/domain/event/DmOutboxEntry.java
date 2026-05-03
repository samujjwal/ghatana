package com.ghatana.digitalmarketing.domain.event;

import java.time.Instant;
import java.util.Objects;

/**
 * A transactional outbox entry wrapping a {@link DmEvent}.
 *
 * <p>Written atomically within the same database transaction as the business
 * state change that produced the event. A dispatcher reads PENDING entries and
 * publishes them to the event bus, then marks them DISPATCHED. On failure, the
 * attempt count is incremented; after {@value #MAX_ATTEMPTS} failures the entry
 * is marked DEAD and moved to the dead-letter table.</p>
 *
 * <p>The {@code serializedPayload} field holds the JSON-serialised event body
 * so that the dispatcher does not need to re-query domain state.</p>
 *
 * @doc.type class
 * @doc.purpose DMOS transactional outbox entry for reliable event dispatch (DMOS-F2-002)
 * @doc.layer product
 * @doc.pattern Outbox, Value Object
 */
public final class DmOutboxEntry {

    /** Maximum dispatch attempts before the entry is declared DEAD. */
    public static final int MAX_ATTEMPTS = 5;

    private final String id;
    private final String eventId;
    private final DmEventType eventType;
    private final String tenantId;
    private final String workspaceId;
    private final String correlationId;
    private final String serializedPayload;
    private final DmOutboxStatus status;
    private final int attemptCount;
    private final Instant createdAt;
    private final Instant scheduledAt;
    private final Instant lastAttemptAt;
    private final String lastFailureReason;

    private DmOutboxEntry(Builder builder) {
        this.id                 = requireNonBlank(builder.id, "id");
        this.eventId            = requireNonBlank(builder.eventId, "eventId");
        this.eventType          = Objects.requireNonNull(builder.eventType, "eventType must not be null");
        this.tenantId           = requireNonBlank(builder.tenantId, "tenantId");
        this.workspaceId        = requireNonBlank(builder.workspaceId, "workspaceId");
        this.correlationId      = requireNonBlank(builder.correlationId, "correlationId");
        this.serializedPayload  = requireNonBlank(builder.serializedPayload, "serializedPayload");
        this.status             = Objects.requireNonNull(builder.status, "status must not be null");
        this.attemptCount       = builder.attemptCount;
        this.createdAt          = Objects.requireNonNull(builder.createdAt, "createdAt must not be null");
        this.scheduledAt        = Objects.requireNonNull(builder.scheduledAt, "scheduledAt must not be null");
        this.lastAttemptAt      = builder.lastAttemptAt;
        this.lastFailureReason  = builder.lastFailureReason;
        if (this.attemptCount < 0) throw new IllegalArgumentException("attemptCount must be >= 0");
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(field + " must not be null or blank");
        return value;
    }

    public String getId() { return id; }
    public String getEventId() { return eventId; }
    public DmEventType getEventType() { return eventType; }
    public String getTenantId() { return tenantId; }
    public String getWorkspaceId() { return workspaceId; }
    public String getCorrelationId() { return correlationId; }
    public String getSerializedPayload() { return serializedPayload; }
    public DmOutboxStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getScheduledAt() { return scheduledAt; }
    public Instant getLastAttemptAt() { return lastAttemptAt; }
    public String getLastFailureReason() { return lastFailureReason; }

    /** Returns a copy marked as DISPATCHED. */
    public DmOutboxEntry markDispatched() {
        return toBuilder().status(DmOutboxStatus.DISPATCHED).lastAttemptAt(Instant.now()).build();
    }

    /**
     * Returns a copy with an incremented attempt count and failure reason.
     * If attempts reach {@value #MAX_ATTEMPTS}, status becomes {@link DmOutboxStatus#DEAD};
     * otherwise {@link DmOutboxStatus#FAILED}.
     */
    public DmOutboxEntry recordFailure(String reason) {
        int next = attemptCount + 1;
        DmOutboxStatus nextStatus = next >= MAX_ATTEMPTS ? DmOutboxStatus.DEAD : DmOutboxStatus.FAILED;
        return toBuilder()
            .attemptCount(next)
            .status(nextStatus)
            .lastAttemptAt(Instant.now())
            .lastFailureReason(reason != null ? reason : "unknown")
            .build();
    }

    /** @return {@code true} if this entry is eligible for dispatch retry. */
    public boolean isRetryable() {
        return status == DmOutboxStatus.FAILED && attemptCount < MAX_ATTEMPTS;
    }

    private Builder toBuilder() {
        return new Builder()
            .id(id).eventId(eventId).eventType(eventType)
            .tenantId(tenantId).workspaceId(workspaceId).correlationId(correlationId)
            .serializedPayload(serializedPayload).status(status)
            .attemptCount(attemptCount).createdAt(createdAt).scheduledAt(scheduledAt)
            .lastAttemptAt(lastAttemptAt).lastFailureReason(lastFailureReason);
    }

    public static Builder builder() { return new Builder(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DmOutboxEntry other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "DmOutboxEntry{id=" + id + ", eventType=" + eventType + ", status=" + status + '}';
    }

    public static final class Builder {
        private String id;
        private String eventId;
        private DmEventType eventType;
        private String tenantId;
        private String workspaceId;
        private String correlationId;
        private String serializedPayload;
        private DmOutboxStatus status = DmOutboxStatus.PENDING;
        private int attemptCount;
        private Instant createdAt;
        private Instant scheduledAt;
        private Instant lastAttemptAt;
        private String lastFailureReason;

        private Builder() {}

        public Builder id(String id) { this.id = id; return this; }
        public Builder eventId(String eventId) { this.eventId = eventId; return this; }
        public Builder eventType(DmEventType eventType) { this.eventType = eventType; return this; }
        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder workspaceId(String workspaceId) { this.workspaceId = workspaceId; return this; }
        public Builder correlationId(String correlationId) { this.correlationId = correlationId; return this; }
        public Builder serializedPayload(String serializedPayload) { this.serializedPayload = serializedPayload; return this; }
        public Builder status(DmOutboxStatus status) { this.status = status; return this; }
        public Builder attemptCount(int attemptCount) { this.attemptCount = attemptCount; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder scheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; return this; }
        public Builder lastAttemptAt(Instant lastAttemptAt) { this.lastAttemptAt = lastAttemptAt; return this; }
        public Builder lastFailureReason(String lastFailureReason) { this.lastFailureReason = lastFailureReason; return this; }

        public DmOutboxEntry build() { return new DmOutboxEntry(this); }
    }
}
