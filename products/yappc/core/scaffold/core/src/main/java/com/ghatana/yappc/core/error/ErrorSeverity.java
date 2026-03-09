package com.ghatana.yappc.core.error;

/**
 * Error severity levels for proper categorization and handling.
 * 
 * <p>This enum consolidates severity levels from multiple sources:
 * <ul>
 *   <li>YappcException - application-level error severity</li>
 *   <li>NormalizedBuildLog - build log error severity</li>
 * </ul>
 *
 * @doc.type enum
 * @doc.purpose Error severity levels for proper categorization and handling.
 * @doc.layer platform
 * @doc.pattern Enumeration
 */
public enum ErrorSeverity {
    /**
     * Informational messages or minor warnings that don't affect functionality.
     */
    INFO,
    
    /**
     * Low severity - informational errors, warnings that may need attention.
     */
    LOW,
    
    /**
     * Warning - potential issues that should be reviewed.
     */
    WARNING,
    
    /**
     * Medium severity - recoverable errors, validation failures.
     */
    MEDIUM,
    
    /**
     * Error - significant issues that affect functionality.
     */
    ERROR,
    
    /**
     * High severity - service errors, integration failures, critical issues.
     */
    HIGH,
    
    /**
     * Critical - system-level failures requiring immediate attention.
     */
    CRITICAL;
    
    /**
     * Checks if this severity is at least as severe as the given level.
     * 
     * @param other the severity level to compare against
     * @return true if this severity is >= other
     */
    public boolean isAtLeast(ErrorSeverity other) {
        return this.ordinal() >= other.ordinal();
    }
    
    /**
     * Checks if this severity represents an error (ERROR, HIGH, or CRITICAL).
     * 
     * @return true if this is an error-level severity
     */
    public boolean isError() {
        return this == ERROR || this == HIGH || this == CRITICAL;
    }
    
    /**
     * Checks if this severity represents a warning (WARNING or MEDIUM).
     * 
     * @return true if this is a warning-level severity
     */
    public boolean isWarning() {
        return this == WARNING || this == MEDIUM;
    }
}
