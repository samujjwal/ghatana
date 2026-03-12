package com.ghatana.tutorputor.explorer.model;

public class ContentGenerationResponse {
    private final String content;
    private final String domain;
    private final ContentType contentType;
    private final String gradeLevel;
    
    public ContentGenerationResponse(String content, String domain, ContentType contentType, String gradeLevel) {
        this.content = content; this.domain = domain; this.contentType = contentType; this.gradeLevel = gradeLevel;
    }
    
    public String getContent() { return content; }
    public String getDomain() { return domain; }
    public ContentType getContentType() { return contentType; }
    public String getGradeLevel() { return gradeLevel; }
    
    public static Builder builder() { return new Builder(); }
    
    public static class Builder {
        private String content, domain, gradeLevel;
        private ContentType contentType;
        public Builder content(String content) { this.content = content; return this; }
        public Builder domain(String domain) { this.domain = domain; return this; }
        public Builder contentType(ContentType contentType) { this.contentType = contentType; return this; }
        public Builder gradeLevel(String gradeLevel) { this.gradeLevel = gradeLevel; return this; }
        public ContentGenerationResponse build() { return new ContentGenerationResponse(content, domain, contentType, gradeLevel); }
    }
}
