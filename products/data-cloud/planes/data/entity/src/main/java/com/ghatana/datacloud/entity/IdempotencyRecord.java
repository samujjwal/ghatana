package com.ghatana.datacloud.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Idempotency record for retry-safe entity operations.
 *
 * <p><b>Purpose</b><br>
 * Tracks idempotency keys to ensure that repeated requests with the same key
 * return the same result, preventing duplicate entity creation in distributed systems.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // First request with idempotency key
 * Entity entity = repository.saveWithIdempotency("tenant-123", newEntity, "req-abc-123");
 * // Creates new entity and stores idempotency record
 *
 * // Retry with same idempotency key
 * Entity sameEntity = repository.saveWithIdempotency("tenant-123", newEntity, "req-abc-123");
 * // Returns the previously created entity (no duplicate)
 * }</pre>
 *
 * <p><b>Key Scope</b><br>
 * Idempotency keys are scoped to (tenantId, collectionName, idempotencyKey).
 * Different collections can reuse the same key safely.
 *
 * <p><b>Expiration</b><br>
 * Idempotency records should have a TTL (e.g., 24 hours) to prevent unbounded growth.
 *
 * @doc.type class
 * @doc.purpose Idempotency tracking for retry-safe operations
 * @doc.layer product
 * @doc.pattern Idempotency Pattern
 */
@Entity
@Table(name = "idempotency_records", indexes = {
    @Index(name = "idx_idempotency_scope", columnList = "tenant_id, collection_name, idempotency_key", unique = true),
    @Index(name = "idx_idempotency_expires", columnList = "expires_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Tenant identifier for multi-tenancy.
     */
    @Column(name = "tenant_id", nullable = false, length = 255)
    private String tenantId;

    /**
     * Collection name for key scoping.
     */
    @Column(name = "collection_name", nullable = false, length = 255)
    private String collectionName;

    /**
     * The idempotency key provided by the client.
     */
    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    /**
     * The entity ID that was created/updated.
     */
    @Column(name = "entity_id")
    private UUID entityId;

    /**
     * The entity data snapshot (for verification).
     */
    @Column(name = "entity_data", columnDefinition = "jsonb")
    private String entityData;

    /**
     * The response returned to the client.
     */
    @Column(name = "response_data", columnDefinition = "jsonb")
    private String responseData;

    /**
     * When this idempotency record expires.
     */
    @Column(name = "expires_at")
    private Instant expiresAt;

    /**
     * When this record was created.
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * User or system that created this record.
     */
    @Column(name = "created_by", length = 255)
    private String createdBy;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (expiresAt == null) {
            // Default TTL: 24 hours
            expiresAt = Instant.now().plusSeconds(24 * 60 * 60);
        }
    }

    /**
     * Check if this idempotency record has expired.
     *
     * @return true if expired
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IdempotencyRecord that = (IdempotencyRecord) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "IdempotencyRecord{" +
                "id=" + id +
                ", tenantId='" + tenantId + '\'' +
                ", collectionName='" + collectionName + '\'' +
                ", idempotencyKey='" + idempotencyKey + '\'' +
                ", entityId=" + entityId +
                ", expiresAt=" + expiresAt +
                '}';
    }
}
