package com.ghatana.yappc.agents.common;

import lombok.Data;
import lombok.experimental.SuperBuilder;

/**
 * Base class for all agent input types.
 * Provides common fields and functionality for agent inputs.
 */
@Data
@SuperBuilder
public abstract class AgentInput {
    /**
     * Unique identifier for this input request.
     */
    private String requestId;
    
    /**
     * User or system that initiated this request.
     */
    private String initiator;
    
    /**
     * Timestamp when this input was created.
     */
    private Long timestamp;
    
    /**
     * Optional context information.
     */
    private String context;
}
