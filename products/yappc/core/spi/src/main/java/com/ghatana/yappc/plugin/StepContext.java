package com.ghatana.yappc.plugin;

import java.util.Map;

/**
 * Context for SDLC step execution.
 *
 * @author YAPPC Team
 * @version 1.0.0
 * @since 1.0.0
 
 * @doc.type interface
 * @doc.purpose Defines the contract for step context
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public interface StepContext {
    
    /**
     * Gets the tenant ID.
     *
     * @return the tenant ID
     */
    String getTenantId();
    
    /**
     * Gets the user ID.
     *
     * @return the user ID
     */
    String getUserId();
    
    /**
     * Gets the project ID.
     *
     * @return the project ID
     */
    String getProjectId();
    
    /**
     * Gets the current phase.
     *
     * @return the phase
     */
    String getPhase();
    
    /**
     * Gets context properties.
     *
     * @return the properties map
     */
    Map<String, Object> getProperties();
}
