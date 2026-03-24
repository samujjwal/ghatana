package com.ghatana.yappc.domain.intent;

import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Raw input for intent capture
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record IntentInput(
    String rawText,
    String format,
    Map<String, Object> structuredData,
    String tenantId,
    String userId
) {
    public static IntentInput of(String rawText) {
        return new IntentInput(rawText, "text", Map.of(), null, null);
    }
    
    public static IntentInput of(String rawText, String tenantId) {
        return new IntentInput(rawText, "text", Map.of(), tenantId, null);
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String rawText;
        private String format = "text";
        private Map<String, Object> structuredData = Map.of();
        private String tenantId;
        private String userId;
        
        public Builder rawText(String rawText) {
            this.rawText = rawText;
            return this;
        }
        
        public Builder format(String format) {
            this.format = format;
            return this;
        }
        
        public Builder structuredData(Map<String, Object> structuredData) {
            this.structuredData = structuredData;
            return this;
        }
        
        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }
        
        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }
        
        public IntentInput build() {
            return new IntentInput(rawText, format, structuredData, tenantId, userId);
        }
    }
}
