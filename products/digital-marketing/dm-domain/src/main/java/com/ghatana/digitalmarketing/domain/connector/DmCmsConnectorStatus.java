package com.ghatana.digitalmarketing.domain.connector;

/**
 * Status of a CMS connector.
 *
 * @doc.type enum
 * @doc.purpose Status lifecycle for CMS connector (P3-003)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum DmCmsConnectorStatus {
    /**
     * Connector is pending activation after registration.
     */
    PENDING,
    
    /**
     * Connector is active and operational.
     */
    ACTIVE,
    
    /**
     * Connector authentication failed.
     */
    FAILED,
    
    /**
     * Connector is suspended by user.
     */
    SUSPENDED,
    
    /**
     * Connector is disabled by system.
     */
    DISABLED
}
