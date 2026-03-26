package com.ghatana.tts.core.api;

import java.util.List;

/**
 * Core interface for all TTS engine implementations.
 *
 * <p>Implementations may delegate to local ONNX/Coqui models, cloud APIs, or both.
 * All methods are synchronous and must be thread-safe.
 *
 * @doc.type interface
 * @doc.purpose Port definition for TTS engine — all engine impls must satisfy this contract
 * @doc.layer product
 * @doc.pattern Service
 */
public interface TtsEngine {

    /**
     * Synthesise {@code text} using the given options.
     *
     * @param text    the text to synthesise (non-null, non-empty)
     * @param options per-request synthesis tuning
     * @return synthesised audio with metadata
     */
    SynthesisResult synthesize(String text, SynthesisOptions options);

    /**
     * Returns the current engine operational status.
     */
    EngineStatus getStatus();

    /**
     * Returns runtime performance metrics.
     */
    EngineMetrics getMetrics();

    /**
     * Returns all voices available for the given language tag.
     *
     * @param languageFilter BCP-47 language tag, or {@code null} / empty for all voices
     * @return list of available voices (never null)
     */
    List<VoiceInfo> getAvailableVoices(String languageFilter);

    /**
     * Creates a new user profile.
     *
     * @param profileId   externally-generated profile identifier
     * @param displayName human-readable name for the profile
     * @param settings    initial profile settings
     * @return the newly created profile
     */
    UserProfile createProfile(String profileId, String displayName, ProfileSettings settings);

    /**
     * Retrieves an existing user profile by ID.
     *
     * @param profileId the profile to retrieve
     * @return the profile, or {@code null} if not found
     */
    UserProfile getProfile(String profileId);

    /**
     * Updates settings for an existing profile.
     *
     * @param profileId the profile to update
     * @param settings  new settings to apply
     * @return the updated profile
     */
    UserProfile updateProfile(String profileId, ProfileSettings settings);
}
