package com.ghatana.platform.governance;

/**
 * Defines the sensitivity level of data within an event.
 * This classification helps determine the appropriate handling,
 * storage, and access controls for the data.
 
 *
 * @doc.type enum
 * @doc.purpose Data classification
 * @doc.layer platform
 * @doc.pattern Enumeration
*/
public enum DataClassification {
    /**
     * Public data that can be freely shared and has no restrictions.
     * Example: Marketing materials, public announcements.
     */
    PUBLIC,

    /**
     * Internal data that is not sensitive but should not be publicly exposed.
     * Example: Internal documentation, non-sensitive business processes.
     */
    INTERNAL,

    /**
     * Confidential data that requires protection from unauthorized access.
     * Example: Business plans, financial projections, employee information.
     */
    CONFIDENTIAL,

    /**
     * Highly sensitive data that could cause significant harm if disclosed.
     * Example: Personal identifiable information (PII), health records.
     */
    SENSITIVE,

    /**
     * Data that is subject to regulatory compliance requirements.
     * Example: Payment card information (PCI), healthcare data (HIPAA).
     */
    REGULATED,

    /**
     * The highest level of classification for extremely sensitive data.
     * Example: National security information, trade secrets.
     */
    TOP_SECRET;

    /**
     * Checks if this classification is at or above the specified level.
     *
     * @param other The classification level to compare against
     * @return true if this classification is at or above the specified level
     */
    public boolean isAtLeast(DataClassification other) {
        return this.ordinal() >= other.ordinal();
    }

    /**
     * Gets the default data classification.
     *
     * @return The default classification (INTERNAL)
     */
    public static DataClassification getDefault() {
        return INTERNAL;
    }
}
