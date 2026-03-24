package com.ghatana.yappc.client;

import java.util.List;

/**
 * Validation report for canvas or other artifacts.
 *
 * @author YAPPC Team
 * @version 1.0.0
 * @since 1.0.0
 
 * @doc.type class
 * @doc.purpose Handles validation report operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class ValidationReport {
    
    private final boolean valid;
    private final List<ValidationIssue> issues;
    
    public ValidationReport(boolean valid, List<ValidationIssue> issues) {
        this.valid = valid;
        this.issues = List.copyOf(issues);
    }
    
    public boolean isValid() {
        return valid;
    }
    
    public List<ValidationIssue> getIssues() {
        return issues;
    }
    
    public static final class ValidationIssue {
        private final String severity;
        private final String message;
        private final String location;
        
        public ValidationIssue(String severity, String message, String location) {
            this.severity = severity;
            this.message = message;
            this.location = location;
        }
        
        public String getSeverity() {
            return severity;
        }
        
        public String getMessage() {
            return message;
        }
        
        public String getLocation() {
            return location;
        }
    }
}
