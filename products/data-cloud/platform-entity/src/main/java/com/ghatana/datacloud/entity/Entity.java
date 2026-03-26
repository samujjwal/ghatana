package com.ghatana.datacloud.entity;

import com.ghatana.datacloud.EntityRecord;
import jakarta.persistence.*;
import lombok.experimental.SuperBuilder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Domain model for dynamic entity data.
 *
 * <p><b>Purpose</b><br>
 * Represents a single entity instance for a collection with dynamic JSONB data storage.
 * Extends {@link EntityRecord} from core module to reuse standardized CRUD operations,
 * multi-tenancy, soft delete, optimistic locking, and audit tracking.
 *
 * <p><b>Inheritance</b><br>
 * <pre>
 * DataRecord (core)
 *   └── EntityRecord (core) - mutable, versioned, soft-delete
 *         └── Entity (domain) - domain-specific entity
 * </pre>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * Entity entity = Entity.builder()
 *     .tenantId("tenant-123")
 *     .collectionName("orders")
 *     .data(Map.of(
 *         "orderId", "ORD-001",
 *         "customerId", "CUST-456",
 *         "amount", 99.99,
 *         "status", "pending"
 *     ))
 *     .createdBy("user-789")
 *     .build();
 *
 * // Access data
 * String orderId = (String) entity.getData().get("orderId");
 * Double amount = (Double) entity.getData().get("amount");
 *
 * // Soft delete
 * entity.softDelete("user-789");
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - Domain model in domain layer (hexagonal architecture)
 * - JPA entity extending core EntityRecord
 * - JSONB column for dynamic data storage (inherited)
 * - Supports optimistic locking via @Version (inherited)
 *
 * <p><b>Thread Safety</b><br>
 * Mutable entity - not thread-safe. Manage lifecycle within transaction boundaries.
 *
 * <p><b>Multi-Tenancy</b><br>
 * All queries MUST filter by tenant_id to enforce tenant isolation.
 *
 * @see EntityRecord
 * @see MetaCollection
 * @see MetaField
 * @doc.type class
 * @doc.purpose Domain model for dynamic entity data extending core EntityRecord
 * @doc.layer product
 * @doc.pattern Domain Entity (Domain Layer)
 */
@jakarta.persistence.Entity
@Table(name = "entities", indexes = {
    @Index(name = "idx_entities_tenant", columnList = "tenant_id"),
    @Index(name = "idx_entities_collection", columnList = "tenant_id, collection_name"),
    @Index(name = "idx_entities_active", columnList = "tenant_id, collection_name, active")
})
@Getter
@Setter
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
public class Entity extends EntityRecord {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Entity entity = (Entity) o;
        return Objects.equals(getId(), entity.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }

    @Override
    public String toString() {
        return "Entity{" +
                "id=" + getId() +
                ", tenantId='" + getTenantId() + '\'' +
                ", collectionName='" + getCollectionName() + '\'' +
                ", version=" + getVersion() +
                ", active=" + getActive() +
                ", createdAt=" + getCreatedAt() +
                ", updatedAt=" + getUpdatedAt() +
                '}';
    }
}
