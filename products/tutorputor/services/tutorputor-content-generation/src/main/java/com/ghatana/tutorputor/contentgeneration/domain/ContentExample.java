package com.ghatana.tutorputor.explorer.model;

import java.util.List;

public class ContentExample {
    private final String id;
    private final String claimId;
    private final String title;
    private final String description;
    private final List<String> steps;
    private final String visualAidDescription;
    private final String gradeLevel;
    private final String domain;
    private final long createdAt;
    
    public ContentExample(String id, String claimId, String title, String description, List<String> steps, String visualAidDescription, String gradeLevel, String domain, long createdAt) {
        this.id = id; this.claimId = claimId; this.title = title; this.description = description;
        this.steps = steps; this.visualAidDescription = visualAidDescription;
        this.gradeLevel = gradeLevel; this.domain = domain; this.createdAt = createdAt;
    }
    
    public String getId() { return id; }
    public String getClaimId() { return claimId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public List<String> getSteps() { return steps; }
    public String getVisualAidDescription() { return visualAidDescription; }
    public String getGradeLevel() { return gradeLevel; }
    public String getDomain() { return domain; }
    public long getCreatedAt() { return createdAt; }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String id, claimId, title, description, visualAidDescription, gradeLevel, domain;
        private List<String> steps;
        private long createdAt;
        
        public Builder id(String id) { this.id = id; return this; }
        public Builder claimId(String claimId) { this.claimId = claimId; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder steps(List<String> steps) { this.steps = steps; return this; }
        public Builder visualAidDescription(String visualAidDescription) { this.visualAidDescription = visualAidDescription; return this; }
        public Builder gradeLevel(String gradeLevel) { this.gradeLevel = gradeLevel; return this; }
        public Builder domain(String domain) { this.domain = domain; return this; }
        public Builder createdAt(long createdAt) { this.createdAt = createdAt; return this; }
        
        public ContentExample build() {
            return new ContentExample(id, claimId, title, description, steps, visualAidDescription, gradeLevel, domain, createdAt);
        }
    }
}
