package com.ghatana.phr.kernel.evidence;

import com.ghatana.kernel.adapter.datacloud.DataWriteRequest;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

/**
 * Durable outbox entry for regulated PHR lifecycle, audit, and consent evidence.
 *
 * @doc.type class
 * @doc.purpose Domain model for PHR evidence outbox entries
 * @doc.layer product
 * @doc.pattern Value Object
 */
public final class PhrEvidenceOutboxEntry {

    public enum Status {
        PENDING,
        DELIVERED,
        DEAD_LETTER
    }

    private final String outboxId;
    private final String datasetId;
    private final String eventId;
    private final byte[] body;
    private final Map<String, String> metadata;
    private final int attempts;
    private final Status status;
    private final String lastError;
    private final Instant createdAt;
    private final Instant updatedAt;

    public PhrEvidenceOutboxEntry(
            String outboxId,
            String datasetId,
            String eventId,
            byte[] body,
            Map<String, String> metadata,
            int attempts,
            Status status,
            String lastError,
            Instant createdAt,
            Instant updatedAt) {
        this.outboxId = Objects.requireNonNull(outboxId, "outboxId cannot be null");
        this.datasetId = Objects.requireNonNull(datasetId, "datasetId cannot be null");
        this.eventId = Objects.requireNonNull(eventId, "eventId cannot be null");
        this.body = body == null ? new byte[0] : Arrays.copyOf(body, body.length);
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        this.attempts = attempts;
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.lastError = lastError == null ? "" : lastError;
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
        this.updatedAt = updatedAt == null ? this.createdAt : updatedAt;
    }

    public static PhrEvidenceOutboxEntry pending(
            String outboxId,
            String datasetId,
            String eventId,
            byte[] body,
            Map<String, String> metadata,
            Instant now) {
        return new PhrEvidenceOutboxEntry(
            outboxId,
            datasetId,
            eventId,
            body,
            metadata,
            0,
            Status.PENDING,
            "",
            now,
            now
        );
    }

    public PhrEvidenceOutboxEntry failed(String error, int maxAttempts, Instant now) {
        int nextAttempts = attempts + 1;
        Status nextStatus = nextAttempts >= maxAttempts ? Status.DEAD_LETTER : Status.PENDING;
        return new PhrEvidenceOutboxEntry(
            outboxId,
            datasetId,
            eventId,
            body,
            metadata,
            nextAttempts,
            nextStatus,
            error,
            createdAt,
            now
        );
    }

    public PhrEvidenceOutboxEntry delivered(Instant now) {
        return new PhrEvidenceOutboxEntry(
            outboxId,
            datasetId,
            eventId,
            body,
            metadata,
            attempts,
            Status.DELIVERED,
            "",
            createdAt,
            now
        );
    }

    public DataWriteRequest toDataWriteRequest() {
        return new DataWriteRequest(datasetId, eventId, body, metadata);
    }

    public String outboxId() {
        return outboxId;
    }

    public String datasetId() {
        return datasetId;
    }

    public String eventId() {
        return eventId;
    }

    public byte[] body() {
        return Arrays.copyOf(body, body.length);
    }

    public Map<String, String> metadata() {
        return metadata;
    }

    public int attempts() {
        return attempts;
    }

    public Status status() {
        return status;
    }

    public String lastError() {
        return lastError;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
