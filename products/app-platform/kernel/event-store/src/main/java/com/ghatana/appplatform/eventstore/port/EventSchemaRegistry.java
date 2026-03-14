package com.ghatana.appplatform.eventstore.port;

import com.ghatana.appplatform.eventstore.domain.EventSchemaVersion;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Hexagonal port — versioned JSON schema registry for event types.
 *
 * <p>The registry tracks the full evolution history of each event type's schema and
 * enforces that at most one version per event type is {@code ACTIVE} at any time.
 * Schemas start as {@link com.ghatana.appplatform.eventstore.domain.SchemaStatus#DRAFT}
 * and are explicitly promoted to {@code ACTIVE} or rejected to {@code BROKEN}.
 *
 * <p>All operations return ActiveJ {@link Promise} so they compose cleanly within
 * the non-blocking eventloop. Blocking JDBC work is handled by the adapter layer
 * via {@code Promise.ofBlocking}.
 *
 * @doc.type interface
 * @doc.purpose Hexagonal port for the event schema version registry
 * @doc.layer product
 * @doc.pattern Port
 */
public interface EventSchemaRegistry {

    /**
     * Register a new schema version (status = DRAFT).
     *
     * <p>If a schema with the same {@code (eventType, version)} pair already exists,
     * the existing record is updated in place only while it is still {@code DRAFT}.
     * Updating an {@code ACTIVE} or {@code DEPRECATED} version is rejected.
     *
     * @param schema schema to persist (status is overridden to DRAFT; activatedAt is cleared)
     * @return promise that completes when the schema is persisted
     */
    Promise<Void> registerSchema(EventSchemaVersion schema);

    /**
     * Promote the given version to {@code ACTIVE} and deprecate the current active version.
     *
     * <p>The operation is atomic: the previous ACTIVE row transitions to DEPRECATED and
     * the target row transitions to ACTIVE in the same database transaction. If no
     * previous active version exists the target is simply activated.
     *
     * <p>The schema must exist and be in {@code DRAFT} or {@code ACTIVE} state.
     *
     * @param eventType event type name
     * @param version   version to activate
     * @return promise that completes once the transition is committed
     */
    Promise<Void> activateSchema(String eventType, int version);

    /**
     * Return the currently ACTIVE schema for an event type, if one exists.
     *
     * @param eventType event type name
     * @return optional active schema
     */
    Promise<Optional<EventSchemaVersion>> getActiveSchema(String eventType);

    /**
     * Return a specific version of a schema regardless of its status.
     *
     * @param eventType event type name
     * @param version   version number
     * @return optional schema version
     */
    Promise<Optional<EventSchemaVersion>> getSchema(String eventType, int version);

    /**
     * List all registered versions for an event type in ascending version order.
     *
     * @param eventType event type name
     * @return list of schema versions (empty list if none registered)
     */
    Promise<List<EventSchemaVersion>> listVersions(String eventType);
}
