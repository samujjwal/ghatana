package com.ghatana.stt.core.api;

/**
 * A personalized model variant created for a specific user profile.
 * 
 * @doc.type record
 * @doc.purpose Personalized model wrapper
 * @doc.layer api
 */
public record PersonalizedModel(
    /** The base model this is derived from */
    ModelInfo baseModel,
    
    /** The user profile this is personalized for */
    String profileId,
    
    /** Voice adapter parameters */
    UserProfile.LoRAParameters voiceAdapter,
    
    /** Language adapter parameters */
    LanguageAdapter languageAdapter,
    
    /** Whether the model is ready for use */
    boolean isReady
) {
    /**
     * Language adapter for personalized language model.
     */
    public record LanguageAdapter(
        /** N-gram probability adjustments */
        float[] ngramWeights,
        
        /** Vocabulary boost weights */
        float[] vocabularyBoosts,
        
        /** Context-specific weights */
        float[] contextWeights
    ) {}
}
