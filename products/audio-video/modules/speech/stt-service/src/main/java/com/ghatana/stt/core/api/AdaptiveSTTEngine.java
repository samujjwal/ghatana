package com.ghatana.stt.core.api;

import java.util.List;
import io.activej.promise.Promise;

/**
 * Core interface for the Adaptive Speech-to-Text Engine.
 * 
 * <p>This is the primary API for all STT operations. Implementations handle
 * model loading, transcription, adaptation, and profile management.
 * 
 * <p>Usage:
 * <pre>{@code
 * AdaptiveSTTEngine engine = AdaptiveSTTEngineFactory.create(config);
 * 
 * // Synchronous transcription
 * TranscriptionResult result = engine.transcribe(audioData, options);
 * 
 * // Streaming session
 * StreamingSession session = engine.createStreamingSession(profile);
 * session.onTranscription(transcript -> System.out.println(transcript.getText()));
 * session.feedAudio(audioChunk);
 * }</pre>
 * 
 * @doc.type interface
 * @doc.purpose Core STT engine API for transcription and adaptation
 * @doc.layer api
 * @doc.pattern Strategy
 */
public interface AdaptiveSTTEngine extends AutoCloseable {

    // ========================================================================
    // Core Transcription
    // ========================================================================

    /**
     * Transcribe audio data synchronously.
     * 
     * @param audio The audio data to transcribe
     * @param options Transcription options (language, punctuation, etc.)
     * @return The transcription result with text, confidence, and timings
     */
    TranscriptionResult transcribe(AudioData audio, TranscriptionOptions options);

    /**
     * Transcribe audio data asynchronously.
     * 
     * @param audio The audio data to transcribe
     * @param options Transcription options
     * @return A Promise that completes with the transcription result
     */
    Promise<TranscriptionResult> transcribeAsync(AudioData audio, TranscriptionOptions options);

    /**
     * Create a streaming transcription session for real-time audio.
     * 
     * @param profile The user profile for personalized transcription (may be null)
     * @return A new streaming session
     */
    StreamingSession createStreamingSession(UserProfile profile);

    // ========================================================================
    // Adaptation
    // ========================================================================

    /**
     * Adapt the model from a user interaction (correction, new vocabulary, etc.).
     * 
     * @param interaction The interaction data for adaptation
     * @return The result of the adaptation operation
     */
    AdaptationResult adaptFromInteraction(Interaction interaction);

    /**
     * Batch adapt from multiple interactions.
     * 
     * @param interactions List of interactions to learn from
     * @return Aggregated adaptation result
     */
    AdaptationResult adaptFromInteractions(List<Interaction> interactions);

    // ========================================================================
    // Profile Management
    // ========================================================================

    /**
     * Create a new user profile from enrollment audio samples.
     * 
     * @param enrollmentData Audio samples for speaker enrollment
     * @return The created user profile
     */
    UserProfile createUserProfile(List<AudioSample> enrollmentData);

    /**
     * Load an existing user profile by ID.
     * 
     * @param profileId The profile identifier
     * @return The loaded profile, or null if not found
     */
    UserProfile loadUserProfile(String profileId);

    /**
     * Update a user profile with new adaptation data.
     * 
     * @param profile The profile to update
     * @param data The adaptation data to apply
     */
    void updateUserProfile(UserProfile profile, AdaptationData data);

    /**
     * Save a user profile to persistent storage.
     * 
     * @param profile The profile to save
     */
    void saveUserProfile(UserProfile profile);

    // ========================================================================
    // Model Management
    // ========================================================================

    /**
     * Get information about all available models.
     * 
     * @return List of available model information
     */
    List<ModelInfo> getAvailableModels();

    /**
     * Load a specific model by ID.
     * 
     * @param modelId The model identifier
     * @param options Model loading options
     */
    void loadModel(String modelId, ModelOptions options);

    /**
     * Get the currently active model.
     * 
     * @return Information about the active model
     */
    ModelInfo getActiveModel();

    /**
     * Create a personalized model variant for a user profile.
     * 
     * @param profile The user profile to personalize for
     * @return The personalized model
     */
    PersonalizedModel createPersonalizedModel(UserProfile profile);

    // ========================================================================
    // Status & Metrics
    // ========================================================================

    /**
     * Get the current engine status.
     * 
     * @return The engine status
     */
    EngineStatus getStatus();

    /**
     * Get current engine metrics.
     * 
     * @return The engine metrics
     */
    EngineMetrics getMetrics();

    /**
     * Check if the engine is ready to process requests.
     * 
     * @return true if ready, false otherwise
     */
    boolean isReady();
}
