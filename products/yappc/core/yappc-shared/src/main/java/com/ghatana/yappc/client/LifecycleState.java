package com.ghatana.yappc.client;

/**
 * Lifecycle state of a project.
 *
 * @author YAPPC Team
 * @version 1.0.0
 * @since 1.0.0
 
 * @doc.type class
 * @doc.purpose Handles lifecycle state operations
 * @doc.layer core
 * @doc.pattern ValueObject
* @doc.gaa.lifecycle perceive
*/
public final class LifecycleState {
    
    private final String projectId;
    private final String currentPhase;
    private final String status;
    
    public LifecycleState(String projectId, String currentPhase, String status) {
        this.projectId = projectId;
        this.currentPhase = currentPhase;
        this.status = status;
    }
    
    public String getProjectId() {
        return projectId;
    }
    
    public String getCurrentPhase() {
        return currentPhase;
    }
    
    public String getStatus() {
        return status;
    }
}
