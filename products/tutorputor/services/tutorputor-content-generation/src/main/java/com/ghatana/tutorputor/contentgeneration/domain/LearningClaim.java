package com.ghatana.tutorputor.explorer.model;

import java.util.List;

/**
 * @doc.type class
 * @doc.purpose Model for learning claim
 * @doc.layer product
 */
public class LearningClaim {
    private final String id;
    private final String text;
    private final String domain;
    private final String gradeLevel;
    private final List<String> prerequisites;
    
    public LearningClaim(String id, String text, String domain, String gradeLevel, List<String> prerequisites) {
        this.id = id;
        this.text = text;
        this.domain = domain;
        this.gradeLevel = gradeLevel;
        this.prerequisites = prerequisites;
    }
    
    public String getId() { return id; }
    public String getText() { return text; }
    public String getDomain() { return domain; }
    public String getGradeLevel() { return gradeLevel; }
    public List<String> getPrerequisites() { return prerequisites; }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String id;
        private String text;
        private String domain;
        private String gradeLevel;
        private List<String> prerequisites;
        
        public Builder id(String id) { this.id = id; return this; }
        public Builder text(String text) { this.text = text; return this; }
        public Builder domain(String domain) { this.domain = domain; return this; }
        public Builder gradeLevel(String gradeLevel) { this.gradeLevel = gradeLevel; return this; }
        public Builder prerequisites(List<String> prerequisites) { this.prerequisites = prerequisites; return this; }
        
        public LearningClaim build() {
            return new LearningClaim(id, text, domain, gradeLevel, prerequisites);
        }
    }
}
