package com.ghatana.yappc.domain.validate;

import java.time.Instant;
import java.util.List;

/**
 * @doc.type record
 * @doc.purpose Validation result with issues
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record LifecycleValidationResult(
    boolean passed,
    List<ValidationIssue> issues,
    Instant validatedAt,
    String validatorVersion
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public boolean hasBlockingIssues() {
        return issues.stream().anyMatch(ValidationIssue::blocking);
    }
    
    public List<ValidationIssue> getBlockingIssues() {
        return issues.stream().filter(ValidationIssue::blocking).toList();
    }
    
    public static class Builder {
        private boolean passed = true;
        private List<ValidationIssue> issues = List.of();
        private Instant validatedAt = Instant.now();
        private String validatorVersion = "1.0.0";
        
        public Builder passed(boolean passed) {
            this.passed = passed;
            return this;
        }
        
        public Builder issues(List<ValidationIssue> issues) {
            this.issues = issues;
            return this;
        }
        
        public Builder validatedAt(Instant validatedAt) {
            this.validatedAt = validatedAt;
            return this;
        }
        
        public Builder validatorVersion(String validatorVersion) {
            this.validatorVersion = validatorVersion;
            return this;
        }
        
        public LifecycleValidationResult build() {
            return new LifecycleValidationResult(passed, issues, validatedAt, validatorVersion);
        }
    }
}
