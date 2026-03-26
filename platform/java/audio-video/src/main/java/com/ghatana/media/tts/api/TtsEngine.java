/**
 * @doc.type interface
 * @doc.purpose Text-to-Speech Engine API for embedded library usage
 * @doc.layer platform
 * @doc.pattern ServiceInterface
 */
package com.ghatana.media.tts.api;

import com.ghatana.media.common.*;
import io.activej.promise.Promise;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Text-to-Speech Engine interface for synthesis and voice management.
 *
 * <p>Usage:
 * <pre>{@code
 * try (TtsEngine engine = library.getTtsEngine()) {
 *     // Simple synthesis
 *     AudioData audio = engine.synthesize("Hello, world!");
 *
 *     // Synthesis with options
 *     AudioData audio = engine.synthesize(text, SynthesisOptions.builder()
 *         .speed(1.2)
 *         .pitch(1.0)
 *         .build());
 *
 *     // Streaming synthesis
 *     engine.synthesizeStreaming(text, options, chunk -> {
 *         playAudio(chunk);
 *     });
 * }
 * }</pre>
 */
public interface TtsEngine extends AutoCloseable {

    // ====================================================================================
    // Core Synthesis
    // ====================================================================================

    /**
     * Synthesize text to audio synchronously.
     *
     * @param text text to synthesize
     * @return audio data
     * @throws ValidationError if text is invalid
     * @throws InferenceError if synthesis fails
     */
    default AudioData synthesize(String text) {
        return synthesize(text, SynthesisOptions.defaults());
    }

    /**
     * Synthesize text with options.
     *
     * @param text text to synthesize
     * @param options synthesis options
     * @return audio data
     */
    AudioData synthesize(String text, SynthesisOptions options);

    /**
     * Synthesize text asynchronously.
     *
     * @param text text to synthesize
     * @param options synthesis options
     * @return promise of audio data
     */
    default Promise<AudioData> synthesizeAsync(String text, SynthesisOptions options) {
        try {
            return Promise.of(synthesize(text, options));
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }

    /**
     * Synthesize asynchronously with default options.
     */
    default Promise<AudioData> synthesizeAsync(String text) {
        return synthesizeAsync(text, SynthesisOptions.defaults());
    }

    // ====================================================================================
    // Streaming Synthesis
    // ====================================================================================

    /**
     * Synthesize with streaming output.
     *
     * @param text text to synthesize
     * @param options synthesis options
     * @param chunkConsumer callback for audio chunks
     */
    void synthesizeStreaming(String text, SynthesisOptions options, Consumer<AudioChunk> chunkConsumer);

    /**
     * Stream synthesis with default options.
     */
    default void synthesizeStreaming(String text, Consumer<AudioChunk> chunkConsumer) {
        synthesizeStreaming(text, SynthesisOptions.defaults(), chunkConsumer);
    }

    // ====================================================================================
    // Voice Management
    // ====================================================================================

    /**
     * Get available voices.
     *
     * @return list of available voices
     */
    List<VoiceInfo> getAvailableVoices();

    /**
     * Get voices filtered by language.
     *
     * @param language language filter
     * @return filtered voice list
     */
    List<VoiceInfo> getAvailableVoices(Locale language);

    /**
     * Load a voice.
     *
     * @param voiceId voice identifier
     * @return loaded voice info
     * @throws ModelLoadingError if voice cannot be loaded
     */
    VoiceInfo loadVoice(String voiceId);

    /**
     * Get the currently active voice.
     */
    VoiceInfo getActiveVoice();

    /**
     * Set the active voice.
     *
     * @param voiceId voice identifier
     */
    void setActiveVoice(String voiceId);

    // ====================================================================================
    // Voice Cloning
    // ====================================================================================

    /**
     * Clone a voice from audio samples.
     *
     * @param voiceName name for the cloned voice
     * @param audioSamples audio samples of the target voice
     * @param options cloning options
     * @return cloned voice info
     */
    VoiceInfo cloneVoice(String voiceName, List<AudioData> audioSamples, CloneOptions options);

    /**
     * Clone voice with default options.
     */
    default VoiceInfo cloneVoice(String voiceName, List<AudioData> audioSamples) {
        return cloneVoice(voiceName, audioSamples, CloneOptions.defaults());
    }

    // ====================================================================================
    // Profile Management
    // ====================================================================================

    /**
     * Create a user profile.
     *
     * @param profileId unique profile identifier
     * @param displayName user display name
     * @param settings profile settings
     * @return created profile
     */
    TtsProfile createProfile(String profileId, String displayName, ProfileSettings settings);

    /**
     * Load a profile.
     *
     * @param profileId profile identifier
     * @return optional containing profile if found
     */
    Optional<TtsProfile> loadProfile(String profileId);

    /**
     * Save a profile.
     *
     * @param profile profile to save
     */
    void saveProfile(TtsProfile profile);

    /**
     * Delete a profile.
     *
     * @param profileId profile identifier
     * @return true if deleted
     */
    boolean deleteProfile(String profileId);

    // ====================================================================================
    // Lifecycle
    // ====================================================================================

    /**
     * Warm up the engine (pre-load models for lower latency).
     */
    void warmup();

    @Override
    void close();

    /**
     * Get engine status.
     */
    EngineStatus getStatus();

    /**
     * Get engine metrics.
     */
    EngineMetrics getMetrics();
}
