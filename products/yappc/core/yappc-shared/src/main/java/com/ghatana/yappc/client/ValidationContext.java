package com.ghatana.yappc.client;

/**
 * Context for validation operations.
 *
 * @author YAPPC Team
 * @version 1.0.0
 * @since 1.0.0
 
 * @doc.type class
 * @doc.purpose Handles validation context operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class ValidationContext {
    
    private final String phase;
    private final boolean strict;
    
    public ValidationContext(String phase, boolean strict) {
        this.phase = phase;
        this.strict = strict;
    }
    
    public String getPhase() {
        return phase;
    }
    
    public boolean isStrict() {
        return strict;
    }
    
    public static ValidationContext forPhase(String phase) {
        return new ValidationContext(phase, false);
    }
}
