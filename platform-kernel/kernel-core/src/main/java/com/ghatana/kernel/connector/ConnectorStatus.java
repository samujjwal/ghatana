package com.ghatana.kernel.connector;

/**
 * Lifecycle status of a connector.
 *
 * @doc.type enum
 * @doc.purpose Connector lifecycle states (KERNEL-P1)
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public enum ConnectorStatus {
    /**
     * Connector is pending activation after registration.
     */
    PENDING,

    /**
     * Connector is active and operational.
     */
    ACTIVE,

    /**
     * Connector is temporarily inactive.
     */
    INACTIVE,

    /**
     * Connector authentication failed.
     */
    AUTH_FAILED,

    /**
     * Connector is permanently disabled.
     */
    DISABLED
}
