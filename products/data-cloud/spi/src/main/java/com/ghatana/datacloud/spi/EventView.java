package com.ghatana.datacloud.spi;

/**
 * Read-only view of a Data-Cloud event for cross-product consumption.
 *
 * <p>This interface provides a minimal contract that products (like AEP)
 * can depend on without pulling in the full Data-Cloud platform with its
 * JPA/Hibernate dependencies. The concrete {@code Event} entity in
 * Data-Cloud platform implements this interface.
 *
 * @doc.type interface
 * @doc.purpose Cross-product event view contract
 * @doc.layer spi
 * @doc.pattern Interface Segregation, Dependency Inversion
 * @since 1.0.0
 */
public interface EventView {

    /**
     * Get the event type name (e.g., "commerce.order.created").
     *
     * @return event type name, never null
     */
    String getEventTypeName();

    /**
     * Get the event type version.
     *
     * @return event type version, or null if unversioned
     */
    String getEventTypeVersion();

    /**
     * Get the tenant ID that owns this event.
     *
     * @return tenant identifier, never null
     */
    String getTenantId();

    /**
     * Get the event's unique identifier.
     *
     * @return event UUID
     */
    java.util.UUID getId();

    /**
     * Get the event payload as a map.
     *
     * @return event data, never null
     */
    java.util.Map<String, Object> getData();

    /**
     * Get the event creation timestamp.
     *
     * @return creation time
     */
    java.time.Instant getCreatedAt();
}
