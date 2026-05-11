package com.ghatana.datacloud.spi;

import java.time.Instant;

/**
 * P1-08: Human override/delegation controls for automation.
 * 
 * <p>This interface defines the states and controls for human intervention
 * in automated workflows, allowing for override, delegation, and rollback.
 *
 * @doc.type interface
 * @doc.purpose Human override/delegation controls for automation
 * @doc.layer product
 * @doc.pattern Domain Model
 */
public interface AutomationControl {
    
    /**
     * Gets the current action state.
     * @return the current state
     */
    ActionState getState();
    
    /**
     * Gets the automation run ID.
     * @return the run identifier
     */
    String getRunId();
    
    /**
     * Gets the principal who initiated the last state transition.
     * @return the principal ID
     */
    String getLastTransitionedBy();
    
    /**
     * Gets the timestamp of the last state transition.
     * @return when the last transition occurred
     */
    Instant getLastTransitionedAt();
    
    /**
     * Gets the reason for the current state.
     * @return human-readable reason
     */
    String getReason();
    
    /**
     * Action state enumeration.
     */
    enum ActionState {
        PROPOSED,
        AUTO_APPLIED,
        REVIEW_REQUIRED,
        INTERRUPTED_BY_HUMAN,
        TAKEN_OVER_BY_HUMAN,
        RESUMED_AUTOMATION,
        REJECTED,
        ROLLED_BACK
    }
    
    /**
     * Record implementation of AutomationControl.
     */
    record AutomationControlRecord(
        ActionState state,
        String runId,
        String lastTransitionedBy,
        Instant lastTransitionedAt,
        String reason
    ) implements AutomationControl {
        
        @Override
        public ActionState getState() {
            return state;
        }
        
        @Override
        public String getRunId() {
            return runId;
        }
        
        @Override
        public String getLastTransitionedBy() {
            return lastTransitionedBy;
        }
        
        @Override
        public Instant getLastTransitionedAt() {
            return lastTransitionedAt;
        }
        
        @Override
        public String getReason() {
            return reason;
        }
    }
}
