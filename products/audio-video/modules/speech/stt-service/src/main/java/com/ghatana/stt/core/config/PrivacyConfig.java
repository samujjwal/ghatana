package com.ghatana.stt.core.config;

import com.ghatana.stt.core.api.UserProfile.PrivacyLevel;

/**
 * Configuration for privacy and security settings.
 * 
 * @doc.type record
 * @doc.purpose Privacy configuration
 * @doc.layer config
 */
public record PrivacyConfig(
    /** Default privacy level */
    PrivacyLevel defaultLevel,
    
    /** Store raw audio for adaptation */
    boolean storeAudio,
    
    /** Store transcripts for adaptation */
    boolean storeTranscripts,
    
    /** Encrypt user data at rest */
    boolean encryptUserData,
    
    /** Enable cloud sync (opt-in) */
    boolean cloudSyncEnabled,
    
    /** Apply differential privacy to adaptation */
    boolean differentialPrivacy,
    
    /** Differential privacy epsilon (lower = more private) */
    double dpEpsilon,
    
    /** Anonymize text before storage */
    boolean anonymizeText
) {
    public PrivacyConfig {
        dpEpsilon = Math.max(0.1, Math.min(10.0, dpEpsilon));
    }

    public static PrivacyConfig defaults() {
        return new PrivacyConfig(
            PrivacyLevel.HIGH,
            false,
            true,
            true,
            false,
            true,
            1.0,
            true
        );
    }

    public static PrivacyConfig maxPrivacy() {
        return new PrivacyConfig(
            PrivacyLevel.HIGH,
            false,
            false,
            true,
            false,
            true,
            0.1,
            true
        );
    }

    public static PrivacyConfig minPrivacy() {
        return new PrivacyConfig(
            PrivacyLevel.LOW,
            true,
            true,
            false,
            true,
            false,
            10.0,
            false
        );
    }
}
