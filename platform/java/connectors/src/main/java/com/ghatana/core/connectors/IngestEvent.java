package com.ghatana.core.connectors;

import com.ghatana.platform.types.ContentType;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.types.identity.CorrelationId;
import com.ghatana.platform.types.identity.IdempotencyKey;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Event to be ingested through connectors and the IngestionService.
 * Simplified from EventRecord - validation and enrichment happens in the service layer.
 *
 * @doc.type record
 * @doc.purpose Event data structure for ingestion pipeline
 * @doc.layer core
 * @doc.pattern Value Object
 */
public record IngestEvent(
    TenantId tenantId,
    String eventTypeName,
    String eventTypeVersion,
    Instant occurrenceTime,
    Map<String, String> headers,
    ContentType contentType,
    String schemaUri,
    ByteBuffer payload,
    Optional<CorrelationId> correlationId,
    Optional<IdempotencyKey> idempotencyKey,
    Optional<String> partitionKey
) {
    public IngestEvent {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(eventTypeName, "eventTypeName required");
        Objects.requireNonNull(eventTypeVersion, "eventTypeVersion required");
        Objects.requireNonNull(occurrenceTime, "occurrenceTime required");
        headers = Map.copyOf(Objects.requireNonNull(headers, "headers required"));
        Objects.requireNonNull(contentType, "contentType required");
        Objects.requireNonNull(schemaUri, "schemaUri required");
        payload = Objects.requireNonNull(payload, "payload required").asReadOnlyBuffer();
        Objects.requireNonNull(correlationId, "correlationId required");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey required");
        Objects.requireNonNull(partitionKey, "partitionKey required");
    }

    /**
     * Builder for creating IngestEvent instances.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private TenantId tenantId;
        private String eventTypeName;
        private String eventTypeVersion = "1.0.0";
        private Instant occurrenceTime = Instant.now();
        private Map<String, String> headers = Map.of();
        private ContentType contentType;
        private String schemaUri;
        private ByteBuffer payload;
        private Optional<CorrelationId> correlationId = Optional.empty();
        private Optional<IdempotencyKey> idempotencyKey = Optional.empty();
        private Optional<String> partitionKey = Optional.empty();

        public Builder tenantId(TenantId tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder eventTypeName(String eventTypeName) {
            this.eventTypeName = eventTypeName;
            return this;
        }

        public Builder eventTypeVersion(String eventTypeVersion) {
            this.eventTypeVersion = eventTypeVersion;
            return this;
        }

        public Builder occurrenceTime(Instant occurrenceTime) {
            this.occurrenceTime = occurrenceTime;
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        public Builder contentType(ContentType contentType) {
            this.contentType = contentType;
            return this;
        }

        public Builder schemaUri(String schemaUri) {
            this.schemaUri = schemaUri;
            return this;
        }

        public Builder payload(ByteBuffer payload) {
            this.payload = payload;
            return this;
        }

        public Builder payload(byte[] payload) {
            this.payload = ByteBuffer.wrap(payload);
            return this;
        }

        public Builder correlationId(CorrelationId correlationId) {
            this.correlationId = Optional.ofNullable(correlationId);
            return this;
        }

        public Builder idempotencyKey(IdempotencyKey idempotencyKey) {
            this.idempotencyKey = Optional.ofNullable(idempotencyKey);
            return this;
        }

        public Builder partitionKey(String partitionKey) {
            this.partitionKey = Optional.ofNullable(partitionKey);
            return this;
        }

        public IngestEvent build() {
            return new IngestEvent(
                tenantId,
                eventTypeName,
                eventTypeVersion,
                occurrenceTime,
                headers,
                contentType,
                schemaUri,
                payload,
                correlationId,
                idempotencyKey,
                partitionKey
            );
        }
    }
}
