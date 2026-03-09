package com.ghatana.yappc.domain.validate;

import java.util.List;

/**
 * @doc.type record
 * @doc.purpose Individual validation issue
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record ValidationIssue(
    String id,
    String severity,
    String category,
    String message,
    String location,
    List<String> suggestions,
    boolean blocking
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String id;
        private String severity = "warning";
        private String category;
        private String message;
        private String location;
        private List<String> suggestions = List.of();
        private boolean blocking = false;
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public Builder severity(String severity) {
            this.severity = severity;
            return this;
        }
        
        public Builder category(String category) {
            this.category = category;
            return this;
        }
        
        public Builder message(String message) {
            this.message = message;
            return this;
        }
        
        public Builder location(String location) {
            this.location = location;
            return this;
        }
        
        public Builder suggestions(List<String> suggestions) {
            this.suggestions = suggestions;
            return this;
        }
        
        public Builder blocking(boolean blocking) {
            this.blocking = blocking;
            return this;
        }
        
        public ValidationIssue build() {
            return new ValidationIssue(id, severity, category, message, location, suggestions, blocking);
        }
    }
}
