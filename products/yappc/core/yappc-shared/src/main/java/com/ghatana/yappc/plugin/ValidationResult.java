package com.ghatana.yappc.plugin;

import java.util.List;

/**
 * Result of validation operation.
 *
 * @author YAPPC Team
 * @version 1.0.0
 * @since 1.0.0
 
 * @doc.type class
 * @doc.purpose Handles validation result operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class ValidationResult {
    
    private final String validatorId;
    private final boolean valid;
    private final List<ValidationIssue> issues;
    
    private ValidationResult(Builder builder) {
        this.validatorId = builder.validatorId;
        this.valid = builder.valid;
        this.issues = List.copyOf(builder.issues);
    }
    
    public String getValidatorId() {
        return validatorId;
    }
    
    public boolean isValid() {
        return valid;
    }
    
    public List<ValidationIssue> getIssues() {
        return issues;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static final class Builder {
        private String validatorId;
        private boolean valid = true;
        private List<ValidationIssue> issues = List.of();
        
        public Builder validatorId(String validatorId) {
            this.validatorId = validatorId;
            return this;
        }
        
        public Builder valid(boolean valid) {
            this.valid = valid;
            return this;
        }
        
        public Builder issues(List<ValidationIssue> issues) {
            this.issues = issues;
            return this;
        }
        
        public ValidationResult build() {
            return new ValidationResult(this);
        }
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
