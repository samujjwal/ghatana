package com.ghatana.stt.core.api;

import java.util.Objects;

/**
 * Options for transcription operations.
 * 
 * @doc.type record
 * @doc.purpose Configuration for transcription behavior
 * @doc.layer api
 */
public record TranscriptionOptions(
    /** Language code (e.g., "en-US", "es-ES") */
    String language,
    
    /** Whether to add punctuation to output */
    boolean enablePunctuation,
    
    /** Whether to include word-level timings */
    boolean enableWordTimings,
    
    /** Context ID for domain-specific adaptation */
    String contextId,
    
    /** Maximum number of alternative transcriptions */
    int maxAlternatives,
    
    /** Minimum confidence threshold (0.0 to 1.0) */
    float confidenceThreshold,
    
    /** User profile ID for personalized transcription */
    String profileId
) {
    public TranscriptionOptions {
        language = language != null ? language : "en-US";
        maxAlternatives = Math.max(1, maxAlternatives);
        confidenceThreshold = Math.max(0.0f, Math.min(1.0f, confidenceThreshold));
    }

    /**
     * Create default options for English transcription.
     */
    public static TranscriptionOptions defaults() {
        return new TranscriptionOptions(
            "en-US",
            true,
            false,
            null,
            1,
            0.0f,
            null
        );
    }

    /**
     * Builder for creating TranscriptionOptions.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String language = "en-US";
        private boolean enablePunctuation = true;
        private boolean enableWordTimings = false;
        private String contextId = null;
        private int maxAlternatives = 1;
        private float confidenceThreshold = 0.0f;
        private String profileId = null;

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public Builder enablePunctuation(boolean enable) {
            this.enablePunctuation = enable;
            return this;
        }

        public Builder enableWordTimings(boolean enable) {
            this.enableWordTimings = enable;
            return this;
        }

        public Builder contextId(String contextId) {
            this.contextId = contextId;
            return this;
        }

        public Builder maxAlternatives(int max) {
            this.maxAlternatives = max;
            return this;
        }

        public Builder confidenceThreshold(float threshold) {
            this.confidenceThreshold = threshold;
            return this;
        }

        public Builder profileId(String profileId) {
            this.profileId = profileId;
            return this;
        }

        public TranscriptionOptions build() {
            return new TranscriptionOptions(
                language,
                enablePunctuation,
                enableWordTimings,
                contextId,
                maxAlternatives,
                confidenceThreshold,
                profileId
            );
        }
    }
}
