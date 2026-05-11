package com.ghatana.yappc.governance.route;

/**
 * Privacy classification for route data.
 * 
 * @doc.type enum
 * @doc.purpose Defines privacy levels for route data handling
 * @doc.layer governance
 * @doc.pattern Enumeration
 */
public enum PrivacyClassification {
    /**
     * No sensitive data, can be exposed publicly.
     */
    PUBLIC,
    
    /**
     * Internal use only, not for public exposure.
     */
    INTERNAL,
    
    /**
     * Sensitive data requiring access control.
     */
    CONFIDENTIAL,
    
    /**
     * Highly sensitive, strict access control required.
     */
    RESTRICTED
}
