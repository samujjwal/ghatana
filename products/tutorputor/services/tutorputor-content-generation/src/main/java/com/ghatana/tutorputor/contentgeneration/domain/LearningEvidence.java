package com.ghatana.tutorputor.explorer.model;

public class LearningEvidence {
    private final String id;
    private final String claimId;
    private final String type;
    private final String content;
    
    public LearningEvidence(String id, String claimId, String type, String content) {
        this.id = id;
        this.claimId = claimId;
        this.type = type;
        this.content = content;
    }
    
    public String getId() { return id; }
    public String getClaimId() { return claimId; }
    public String getType() { return type; }
    public String getContent() { return content; }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String id, claimId, type, content;
        public Builder id(String id) { this.id = id; return this; }
        public Builder claimId(String claimId) { this.claimId = claimId; return this; }
        public Builder type(String type) { this.type = type; return this; }
        public Builder content(String content) { this.content = content; return this; }
        public LearningEvidence build() {
            return new LearningEvidence(id, claimId, type, content);
        }
    }
}
