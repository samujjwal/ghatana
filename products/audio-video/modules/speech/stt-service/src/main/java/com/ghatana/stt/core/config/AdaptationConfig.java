package com.ghatana.stt.core.config;

import com.ghatana.stt.core.api.UserProfile.AdaptationMode;

/**
 * Configuration for the adaptation system.
 * 
 * @doc.type record
 * @doc.purpose Adaptation configuration
 * @doc.layer config
 */
public record AdaptationConfig(
    /** Whether adaptation is enabled */
    boolean enabled,
    
    /** Default adaptation mode */
    AdaptationMode defaultMode,
    
    /** Minimum training data before adaptation (in seconds) */
    int minTrainingDataSeconds,
    
    /** Maximum profile size in bytes */
    long maxProfileSize,
    
    /** Enable automatic background adaptation */
    boolean autoAdaptation,
    
    /** Adaptation frequency (interactions between adaptations) */
    int adaptationFrequency,
    
    /** Enable LoRA adapter training */
    boolean enableLoraAdaptation,
    
    /** Enable language model adaptation */
    boolean enableLmAdaptation
) {
    public AdaptationConfig {
        minTrainingDataSeconds = Math.max(60, minTrainingDataSeconds); // At least 1 minute
        maxProfileSize = maxProfileSize > 0 ? maxProfileSize : 500L * 1024 * 1024; // 500MB default
        adaptationFrequency = Math.max(1, adaptationFrequency);
    }

    public static AdaptationConfig defaults() {
        return new AdaptationConfig(
            true,
            AdaptationMode.BALANCED,
            300, // 5 minutes
            500L * 1024 * 1024,
            true,
            10,
            true,
            true
        );
    }

    public static AdaptationConfig disabled() {
        return new AdaptationConfig(
            false,
            AdaptationMode.CONSERVATIVE,
            300,
            100L * 1024 * 1024,
            false,
            100,
            false,
            false
        );
    }
}
