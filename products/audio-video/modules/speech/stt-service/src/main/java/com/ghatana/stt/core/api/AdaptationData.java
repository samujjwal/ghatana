package com.ghatana.stt.core.api;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Data container for profile adaptation updates.
 * 
 * @doc.type record
 * @doc.purpose Adaptation data for profile updates
 * @doc.layer api
 */
public record AdaptationData(
    /** Updated speaker embedding (optional) */
    float[] speakerEmbedding,
    
    /** New MLLR transform to add (optional) */
    float[] mllrTransform,
    
    /** New vocabulary terms to add */
    Set<String> newVocabulary,
    
    /** Updated word frequencies */
    Map<String, Float> wordFrequencies,
    
    /** New named entities to add */
    Set<String> namedEntities,
    
    /** Acoustic statistics update */
    UserProfile.AcousticStats acousticStats,
    
    /** LoRA adapter update */
    UserProfile.LoRAParameters loraUpdate
) {
    /**
     * Create adaptation data with just vocabulary updates.
     */
    public static AdaptationData vocabularyOnly(Set<String> terms) {
        return new AdaptationData(null, null, terms, null, null, null, null);
    }

    /**
     * Create adaptation data with acoustic updates.
     */
    public static AdaptationData acousticOnly(float[] embedding, UserProfile.AcousticStats stats) {
        return new AdaptationData(embedding, null, null, null, null, stats, null);
    }

    /**
     * Builder for creating AdaptationData.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private float[] speakerEmbedding;
        private float[] mllrTransform;
        private Set<String> newVocabulary;
        private Map<String, Float> wordFrequencies;
        private Set<String> namedEntities;
        private UserProfile.AcousticStats acousticStats;
        private UserProfile.LoRAParameters loraUpdate;

        public Builder speakerEmbedding(float[] embedding) {
            this.speakerEmbedding = embedding;
            return this;
        }

        public Builder mllrTransform(float[] transform) {
            this.mllrTransform = transform;
            return this;
        }

        public Builder newVocabulary(Set<String> vocabulary) {
            this.newVocabulary = vocabulary;
            return this;
        }

        public Builder wordFrequencies(Map<String, Float> frequencies) {
            this.wordFrequencies = frequencies;
            return this;
        }

        public Builder namedEntities(Set<String> entities) {
            this.namedEntities = entities;
            return this;
        }

        public Builder acousticStats(UserProfile.AcousticStats stats) {
            this.acousticStats = stats;
            return this;
        }

        public Builder loraUpdate(UserProfile.LoRAParameters lora) {
            this.loraUpdate = lora;
            return this;
        }

        public AdaptationData build() {
            return new AdaptationData(
                speakerEmbedding,
                mllrTransform,
                newVocabulary,
                wordFrequencies,
                namedEntities,
                acousticStats,
                loraUpdate
            );
        }
    }
}
