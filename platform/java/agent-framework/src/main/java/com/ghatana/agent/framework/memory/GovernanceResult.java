package com.ghatana.agent.framework.memory;

import org.jetbrains.annotations.NotNull;

/**
 * Result of memory governance operation.
 * 
 * @doc.type class
 * @doc.purpose Governance operation result
 * @doc.layer framework
 * @doc.pattern Value Object
 */
public final class GovernanceResult {
    
    private final int recordsProcessed;
    private final int recordsRedacted;
    private final int recordsDeleted;
    private final int recordsRetained;
    
    public GovernanceResult(
            int recordsProcessed, 
            int recordsRedacted, 
            int recordsDeleted, 
            int recordsRetained) {
        this.recordsProcessed = recordsProcessed;
        this.recordsRedacted = recordsRedacted;
        this.recordsDeleted = recordsDeleted;
        this.recordsRetained = recordsRetained;
    }
    
    public int getRecordsProcessed() {
        return recordsProcessed;
    }
    
    public int getRecordsRedacted() {
        return recordsRedacted;
    }
    
    public int getRecordsDeleted() {
        return recordsDeleted;
    }
    
    public int getRecordsRetained() {
        return recordsRetained;
    }
}
