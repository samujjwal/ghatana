package com.ghatana.tts.core.api;

import java.util.List;
import java.util.function.Consumer;

/**
 * Core TTS Engine interface.
 * 
 * @doc.type interface
 * @doc.purpose TTS engine contract
 * @doc.layer product
 * @doc.pattern Service
 */
public interface TtsEngine extends AutoCloseable {

    /**
     * Synthesize text to audio.
     */
    SynthesisResult synthesize(String text, SynthesisOptions options);

    /**
     * Synthesize text with streaming output.
     */
    void synthesizeStreaming(String text, SynthesisOptions options, Consumer<AudioChunk> chunkConsumer);

    /**
     * Get engine status.
     */
    EngineStatus getStatus();

    /**
     * Get engine metrics.
     */
    EngineMetrics getMetrics();

    /**
     * Get available voices.
     */
    List<VoiceInfo> getAvailableVoices(String languageFilter);

    /**
     * Load a voice model.
     */
    VoiceInfo loadVoice(String voiceId);

    /**
     * Create a user profile.
     */
    UserProfile createProfile(String profileId, String displayName, ProfileSettings settings);

    /**
     * Get a user profile.
     */
    UserProfile getProfile(String profileId);

    /**
     * Update a user profile.
     */
    UserProfile updateProfile(String profileId, ProfileSettings settings);

    /**
     * Clone a voice from audio samples.
     */
    CloneResult cloneVoice(String voiceName, List<byte[]> audioSamples, int epochs, float learningRate);

    /**
     * Submit feedback for adaptation.
     */
    void submitFeedback(String profileId, String synthesisId, String feedbackType, String comment);
}
