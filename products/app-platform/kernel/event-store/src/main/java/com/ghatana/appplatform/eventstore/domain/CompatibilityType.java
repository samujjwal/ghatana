package com.ghatana.appplatform.eventstore.domain;

/**
 * Compatibility guarantee advertised by an event schema version.
 *
 * <ul>
 *   <li>{@link #NONE}     – no compatibility guarantee; consumers must migrate explicitly.
 *   <li>{@link #BACKWARD} – new schema can read data written with the previous version
 *                           (consumers can upgrade without producer changes).
 *   <li>{@link #FORWARD}  – old schema can read data written with the new version
 *                           (producers can upgrade without consumer changes).
 *   <li>{@link #FULL}     – both backward and forward compatible.
 * </ul>
 *
 * @doc.type enum
 * @doc.purpose Declares the compatibility contract for schema evolution
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum CompatibilityType {
    NONE,
    BACKWARD,
    FORWARD,
    FULL
}
