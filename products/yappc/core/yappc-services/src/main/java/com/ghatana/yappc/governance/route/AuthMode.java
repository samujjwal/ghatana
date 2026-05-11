package com.ghatana.yappc.governance.route;

/**
 * @doc.type enum
 * @doc.purpose Authentication mode for routes
 * @doc.layer governance
 * @doc.pattern Enumeration
 */
public enum AuthMode {
    /**
     * No authentication required - publicly accessible
     */
    PUBLIC,
    
    /**
     * Authentication required - request must have valid credentials
     */
    REQUIRED,
    
    /**
     * Authentication optional - request may have credentials but they're not required
     */
    OPTIONAL
}
