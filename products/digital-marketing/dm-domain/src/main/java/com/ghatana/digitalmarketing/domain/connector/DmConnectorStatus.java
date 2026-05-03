package com.ghatana.digitalmarketing.domain.connector;

/**
 * Lifecycle status of a digital marketing connector configuration.
 *
 * @doc.type class
 * @doc.purpose Represents connector health and availability (DMOS-F2-006)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum DmConnectorStatus {
    /** Connector configured but not yet tested. */
    PENDING,
    /** Connector is healthy and operational. */
    ACTIVE,
    /** Connector is temporarily suspended. */
    SUSPENDED,
    /** Connector credentials are invalid or expired. */
    AUTH_FAILED,
    /** Connector permanently disabled. */
    DISABLED
}
