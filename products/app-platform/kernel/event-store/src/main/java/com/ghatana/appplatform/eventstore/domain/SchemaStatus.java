package com.ghatana.appplatform.eventstore.domain;

/**
 * Status of an event schema version within the schema registry.
 *
 * <ul>
 *   <li>{@link #DRAFT}      – registered but not yet enforced on the event store.
 *   <li>{@link #ACTIVE}     – currently enforced; event data is validated against this version.
 *   <li>{@link #DEPRECATED} – superseded by a newer active version; retained for historical reads.
 *   <li>{@link #BROKEN}     – compatibility check failed; version was never made active.
 * </ul>
 *
 * @doc.type enum
 * @doc.purpose Status lifecycle for versioned event schemas
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum SchemaStatus {
    DRAFT,
    ACTIVE,
    DEPRECATED,
    BROKEN
}
