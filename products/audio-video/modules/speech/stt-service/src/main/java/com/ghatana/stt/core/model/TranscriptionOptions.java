package com.ghatana.stt.core.model;

import java.util.List;
import java.util.Map;

/**
 * Options for speech transcription processing.
 * 
 * @doc.type model
 * @doc.purpose Transcription configuration
 */
public class TranscriptionOptions {
    private final String language;
    private final boolean enableTimestamps;
    private final boolean enableWordTiming;
    private final boolean enableVAD;
    private final int maxAlternatives;
    private final boolean enableProfanityFilter;
    private final boolean enablePunctuation;
    private final boolean enableCapitalization;
    private final boolean enableNumberFormatting;
    private final Map<String, Object> customOptions;
    
    private TranscriptionOptions(Builder builder) {
        this.language = builder.language;
        this.enableTimestamps = builder.enableTimestamps;
        this.enableWordTiming = builder.enableWordTiming;
        this.enableVAD = builder.enableVAD;
        this.maxAlternatives = builder.maxAlternatives;
        this.enableProfanityFilter = builder.enableProfanityFilter;
        this.enablePunctuation = builder.enablePunctuation;
        this.enableCapitalization = builder.enableCapitalization;
        this.enableNumberFormatting = builder.enableNumberFormatting;
        this.customOptions = builder.customOptions;
    }
    
    public String language() {
        return language;
    }
    
    public boolean enableTimestamps() {
        return enableTimestamps;
    }
    
    public boolean enableWordTiming() {
        return enableWordTiming;
    }
    
    public boolean enableVAD() {
        return enableVAD;
    }
    
    public int maxAlternatives() {
        return maxAlternatives;
    }
    
    public boolean enableProfanityFilter() {
        return enableProfanityFilter;
    }
    
    public boolean enablePunctuation() {
        return enablePunctuation;
    }
    
    public boolean enableCapitalization() {
        return enableCapitalization;
    }
    
    public boolean enableNumberFormatting() {
        return enableNumberFormatting;
    }
    
    public Map<String, Object> customOptions() {
        return customOptions;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String language = "en-US";
        private boolean enableTimestamps = true;
        private boolean enableWordTiming = false;
        private boolean enableVAD = true;
        private int maxAlternatives = 1;
        private boolean enableProfanityFilter = false;
        private boolean enablePunctuation = true;
        private boolean enableCapitalization = true;
        private boolean enableNumberFormatting = true;
        private Map<String, Object> customOptions = Map.of();
        
        public Builder language(String language) {
            this.language = language;
            return this;
        }
        
        public Builder enableTimestamps(boolean enableTimestamps) {
            this.enableTimestamps = enableTimestamps;
            return this;
        }
        
        public Builder enableWordTiming(boolean enableWordTiming) {
            this.enableWordTiming = enableWordTiming;
            return this;
        }
        
        public Builder enableVAD(boolean enableVAD) {
            this.enableVAD = enableVAD;
            return this;
        }
        
        public Builder maxAlternatives(int maxAlternatives) {
            this.maxAlternatives = maxAlternatives;
            return this;
        }
        
        public Builder enableProfanityFilter(boolean enableProfanityFilter) {
            this.enableProfanityFilter = enableProfanityFilter;
            return this;
        }
        
        public Builder enablePunctuation(boolean enablePunctuation) {
            this.enablePunctuation = enablePunctuation;
            return this;
        }
        
        public Builder enableCapitalization(boolean enableCapitalization) {
            this.enableCapitalization = enableCapitalization;
            return this;
        }
        
        public Builder enableNumberFormatting(boolean enableNumberFormatting) {
            this.enableNumberFormatting = enableNumberFormatting;
            return this;
        }
        
        public Builder customOptions(Map<String, Object> customOptions) {
            this.customOptions = customOptions;
            return this;
        }
        
        public TranscriptionOptions build() {
            return new TranscriptionOptions(this);
        }
    }
}
