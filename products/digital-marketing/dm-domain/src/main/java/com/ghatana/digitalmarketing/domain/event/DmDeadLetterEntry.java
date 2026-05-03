package com.ghatana.digitalmarketing.domain.event;

import java.time.Instant;
import java.util.Objects;

/**
 * A dead-letter queue entry for a DMOS outbox event that exhausted dispatch retries.
 *
 * <p>DLQ entries are retained for manual inspection and selective replay.
 * They capture the full serialized payload, all prior failure reasons,
 * and the total attempt count so operators can diagnose the root cause.</p>
 *
 * @doc.type class
 * @doc.purpose DMOS dead-letter queue entry for failed event dispatch (DMOS-F2-002)
 * @doc.layer product
 * @doc.pattern Dead-Letter Queue, Value Object
 */
public final class DmDeadLetterEntry {

    private final String id;
    private final String originalOutboxId;
    private final String eventId;
    private final DmEventType eventType;
    private final String tenantId;
    private final String workspaceId;
    private final String correlationId;
    private final String serializedPayload;
    private final int totalAttempts;
    private final String lastFailureReason;
    private final Instant createdAt;
    private final Instant deadAt;
    private final boolean replayed;
    private final Instant replayedAt;

    private DmDeadLetterEntry(Builder builder) {
        this.id                 = requireNonBlank(builder.id, "id");
        this.originalOutboxId   = requireNonBlank(builder.originalOutboxId, "originalOutboxId");
        this.eventId            = requireNonBlank(builder.eventId, "eventId");
        this.eventType          = Objects.requireNonNull(builder.eventType, "eventType must not be null");
        this.tenantId           = requireNonBlank(builder.tenantId, "tenantId");
        this.workspaceId        = requireNonBlank(builder.workspaceId, "workspaceId");
        this.correlationId      = requireNonBlank(builder.correlationId, "correlationId");
        this.serializedPayload  = requireNonBlank(builder.serializedPayload, "serializedPayload");
        this.totalAttempts      = builder.totalAttempts;
        this.lastFailureReason  = builder.lastFailureReason != null ? builder.lastFailureReason : "unknown";
        this.createdAt          = Objects.requireNonNull(builder.createdAt, "createdAt must not be null");
        this.deadAt             = Objects.requireNonNull(builder.deadAt, "deadAt must not be null");
        this.replayed           = builder.replayed;
        this.replayedAt         = builder.replayedAt;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(field + " must not be null or blank");
        return value;
    }

    /** Creates a DLQ entry from a dead outbox entry. */
    public static DmDeadLetterEntry fromOutboxEntry(DmOutboxEntry entry) {
        Objects.requireNonNull(entry, "entry must not be null");
        if (entry.getStatus() != DmOutboxStatus.DEAD) {
            throw new IllegalArgumentException("Only DEAD outbox entries can become DLQ entries");
        }
        Instant now = Instant.now();
        return new Builder()
            .id(java.util.UUID.randomUUID().toString())
            .originalOutboxId(entry.getId())
            .eventId(entry.getEventId())
            .eventType(entry.getEventType())
            .tenantId(entry.getTenantId())
            .workspaceId(entry.getWorkspaceId())
            .correlationId(entry.getCorrelationId())
            .serializedPayload(entry.getSerializedPayload())
            .totalAttempts(entry.getAttemptCount())
            .lastFailureReason(entry.getLastFailureReason())
            .createdAt(entry.getCreatedAt())
            .deadAt(now)
            .replayed(false)
            .build();
    }

    /** Returns a copy of this entry marked as replayed. */
    public DmDeadLetterEntry markReplayed() {
        return new Builder()
            .id(id).originalOutboxId(originalOutboxId).eventId(eventId).eventType(eventType)
            .tenantId(tenantId).workspaceId(workspaceId).correlationId(correlationId)
            .serializedPayload(serializedPayload).totalAttempts(totalAttempts)
            .lastFailureReason(lastFailureReason).createdAt(createdAt).deadAt(deadAt)
            .replayed(true).replayedAt(Instant.now())
            .build();
    }

    public String getId() { return id; }
    public String getOriginalOutboxId() { return originalOutboxId; }
    public String getEventId() { return eventId; }
    public DmEventType getEventType() { return eventType; }
    public String getTenantId() { return tenantId; }
    public String getWorkspaceId() { return workspaceId; }
    public String getCorrelationId() { return correlationId; }
    public String getSerializedPayload() { return serializedPayload; }
    public int getTotalAttempts() { return totalAttempts; }
    public String getLastFailureReason() { return lastFailureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDeadAt() { return deadAt; }
    public boolean isReplayed() { return replayed; }
    public Instant getReplayedAt() { return replayedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DmDeadLetterEntry other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "DmDeadLetterEntry{id=" + id + ", eventType=" + eventType + ", replayed=" + replayed + '}';
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private String originalOutboxId;
        private String eventId;
        private DmEventType eventType;
        private String tenantId;
        private String workspaceId;
        private String correlationId;
        private String serializedPayload;
        private int totalAttempts;
        private String lastFailureReason;
        private Instant createdAt;
        private Instant deadAt;
        private boolean replayed;
        private Instant replayedAt;

        private Builder() {}

        public Builder id(String id) { this.id = id; return this; }
        public Builder originalOutboxId(String v) { this.originalOutboxId = v; return this; }
        public Builder eventId(String v) { this.eventId = v; return this; }
        public Builder eventType(DmEventType v) { this.eventType = v; return this; }
        public Builder tenantId(String v) { this.tenantId = v; return this; }
        public Builder workspaceId(String v) { this.workspaceId = v; return this; }
        public Builder correlationId(String v) { this.correlationId = v; return this; }
        public Builder serializedPayload(String v) { this.serializedPayload = v; return this; }
        public Builder totalAttempts(int v) { this.totalAttempts = v; return this; }
        public Builder lastFailureReason(String v) { this.lastFailureReason = v; return this; }
        public Builder createdAt(Instant v) { this.createdAt = v; return this; }
        public Builder deadAt(Instant v) { this.deadAt = v; return this; }
        public Builder replayed(boolean v) { this.replayed = v; return this; }
        public Builder replayedAt(Instant v) { this.replayedAt = v; return this; }

        public DmDeadLetterEntry build() { return new DmDeadLetterEntry(this); }
    }
}
