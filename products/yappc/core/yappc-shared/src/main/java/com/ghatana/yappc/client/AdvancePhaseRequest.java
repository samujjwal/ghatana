package com.ghatana.yappc.client;

/**
 * Request to advance project to next phase.
 *
 * @author YAPPC Team
 * @version 1.0.0
 * @since 1.0.0
 
 * @doc.type class
 * @doc.purpose Handles advance phase request operations
 * @doc.layer core
 * @doc.pattern DTO
*/
public final class AdvancePhaseRequest {
    
    private final String targetPhase;
    private final boolean force;
    
    public AdvancePhaseRequest(String targetPhase, boolean force) {
        this.targetPhase = targetPhase;
        this.force = force;
    }
    
    public String getTargetPhase() {
        return targetPhase;
    }
    
    public boolean isForce() {
        return force;
    }
}
