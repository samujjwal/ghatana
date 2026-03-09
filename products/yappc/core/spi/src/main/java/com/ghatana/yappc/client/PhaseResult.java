package com.ghatana.yappc.client;

/**
 * Result of phase advancement.
 *
 * @author YAPPC Team
 * @version 1.0.0
 * @since 1.0.0
 
 * @doc.type class
 * @doc.purpose Handles phase result operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class PhaseResult {
    
    private final String projectId;
    private final String newPhase;
    private final boolean success;
    
    public PhaseResult(String projectId, String newPhase, boolean success) {
        this.projectId = projectId;
        this.newPhase = newPhase;
        this.success = success;
    }
    
    public String getProjectId() {
        return projectId;
    }
    
    public String getNewPhase() {
        return newPhase;
    }
    
    public boolean isSuccess() {
        return success;
    }
}
