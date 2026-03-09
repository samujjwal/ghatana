package com.ghatana.datacloud.entity;

import com.ghatana.datacloud.DataRecordInterface;
import java.time.Instant;

/**
 * Entity-specific interface extending the core data record contract.
 *
 * <p>
 * <b>Purpose</b><br>
 * Defines the contract for mutable entity operations while avoiding circular
 * dependencies between modules. Extends DataRecordInterface to maintain clear
 * abstraction boundaries and add entity-specific operations.
 *
 * <p>
 * <b>Entity-Specific Features</b><br>
 * <ul>
 * <li><b>Versioning</b> - Optimistic locking via version field</li>
 * <li><b>Soft Delete</b> - Active flag for logical deletion</li>
 * <li><b>Audit Trail</b> - Tracks updatedAt and updatedBy</li>
 * </ul>
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * EntityInterface entity = // obtained from repository
 * entity.getData().put("status", "active");
 * entity.setUpdatedBy("user-123");
 * entity.setUpdatedAt(Instant.now());
 * }</pre>
 *
 * @see DataRecordInterface
 * @see com.ghatana.datacloud.EntityRecord
 * @doc.type interface
 * @doc.purpose Entity-specific record contract with mutation tracking
 * @doc.layer core
 * @doc.pattern Domain Model, Strategy
 */
public interface EntityInterface extends DataRecordInterface {

    /**
     * Get the last update timestamp.
     *
     * @return update time, or null if never updated
     */
    Instant getUpdatedAt();

    /**
     * Set the last update timestamp.
     *
     * @param updatedAt update time
     */
    void setUpdatedAt(Instant updatedAt);

    /**
     * Get who/what last updated this entity.
     *
     * @return updater identifier
     */
    String getUpdatedBy();

    /**
     * Set who/what last updated this entity.
     *
     * @param updatedBy updater identifier
     */
    void setUpdatedBy(String updatedBy);

    /**
     * Get the entity version for optimistic locking.
     *
     * <p>
     * Version is incremented on each update. Used to detect concurrent
     * modifications and prevent lost updates (optimistic locking).
     *
     * @return version number (usually starting at 1)
     */
    Integer getVersion();

    /**
     * Get the active status.
     *
     * <p>
     * When false, the entity is considered soft-deleted but retained for
     * audit purposes.
     *
     * @return true if active, false if soft-deleted
     */
    Boolean getActive();

    /**
     * Set the active status (soft-delete flag).
     *
     * <p>
     * Set to false to soft-delete the entity while keeping audit trail.
     *
     * @param active true to activate, false to soft-delete
     */
    void setActive(Boolean active);
}
