package com.ghatana.platform.core.exception;

/**
 * Exception thrown when a governance policy is violated.
 * 
 * <p>Governance policies control access, modifications, and lifecycle operations
 * on event types and other governed resources.</p>
 *
 * @doc.type exception
 * @doc.purpose Governance policy violation exception
 * @doc.layer core
 * @doc.pattern Exception, Policy Violation
 */
public class GovernancePolicyException extends RuntimeException {
    
    public GovernancePolicyException(String message) {
        super(message);
    }
    
    public GovernancePolicyException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public GovernancePolicyException(Throwable cause) {
        super(cause);
    }
}
