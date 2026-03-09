package com.ghatana.yappc.plugin;

/**
 * Context for validation operations.
 *
 * @author YAPPC Team
 * @version 1.0.0
 * @since 1.0.0
 
 * @doc.type interface
 * @doc.purpose Defines the contract for validation context
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public interface ValidationContext {
    
    /**
     * Gets the target object to validate.
     *
     * @return the target object
     */
    Object getTarget();
    
    /**
     * Gets the validation phase.
     *
     * @return the phase
     */
    String getPhase();
    
    /**
     * Whether strict validation is enabled.
     *
     * @return true if strict
     */
    boolean isStrict();
}
