/**
 * @doc.type interface
 * @doc.purpose Speech-to-Text Engine API for embedded library usage
 * @doc.layer api
 */
package com.ghatana.media.stt.api;

import com.ghatana.media.common.*;
import io.activej.promise.Promise;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Speech-to-Text Engine interface for transcription and adaptation.
 *
 * <p>Usage:
 * <pre>{@code
 * try (SttEngine engine = library.getSttEngine()) {
 *     // Synchronous transcription
 *     TranscriptionResult result = engine.transcribe(audioData);
 *
 *     // Streaming transcription
 *     StreamingSession session = engine.createStreamingSession(profile);
 *     session.onTranscription(text -> System.out.println(text));
 *     session.feedAudio(chunk);
 * }
 * }</pre>
 */
public interface SttEngine extends AutoCloseable {

    // ====================================================================================
    // Core Transcription
    // ====================================================================================

    /**
     * Transcribe audio data synchronously.
     *
     * @param audio the audio data to transcribe
     * @return transcription result
     * @throws ValidationError if audio format is invalid
     * @throws InferenceError if transcription fails
     */
    default TranscriptionResult transcribe(AudioData audio) {
        return transcribe(audio, TranscriptionOptions.defaults());
    }

    /**
     * Transcribe audio with options.
     *
     * @param audio the audio data
     * @param options transcription options
     * @return transcription result
     */
    TranscriptionResult transcribe(AudioData audio, TranscriptionOptions options);

    /**
     * Transcribe audio asynchronously.
     *
     * @param audio the audio data
     * @param options transcription options
     * @return promise of transcription result
     */
    default Promise<TranscriptionResult> transcribeAsync(AudioData audio, TranscriptionOptions options) {
        try {
            return Promise.of(transcribe(audio, options));
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }

    /**
     * Transcribe audio asynchronously with default options.
     */
    default Promise<TranscriptionResult> transcribeAsync(AudioData audio) {
        return transcribeAsync(audio, TranscriptionOptions.defaults());
    }

    // ====================================================================================
    // Streaming Transcription
    // ====================================================================================

    /**
     * Create a streaming transcription session.
     *
     * @return new streaming session
     */
    StreamingSession createStreamingSession();

    /**
     * Create a streaming session with user profile.
     *
     * @param profile user profile for personalized transcription
     * @return new streaming session
     */
    StreamingSession createStreamingSession(UserProfile profile);

    // ====================================================================================
    // Profile Management
    // ====================================================================================

    /**
     * Create a user profile from enrollment audio.
     *
     * @param profileId unique profile identifier
     * @param enrollmentAudio audio samples for speaker adaptation
     * @return created user profile
     */
    UserProfile createProfile(String profileId, List<AudioData> enrollmentAudio);

    /**
     * Load an existing profile.
     *
     * @param profileId profile identifier
     * @return optional containing profile if found
     */
    Optional<UserProfile> loadProfile(String profileId);

    /**
     * Save a profile to persistent storage.
     *
     * @param profile profile to save
     */
    void saveProfile(UserProfile profile);

    /**
     * Delete a profile.
     *
     * @param profileId profile identifier
     * @return true if deleted, false if not found
     */
    boolean deleteProfile(String profileId);

    /**
     * List all available profiles.
     */
    List<String> listProfiles();

    // ====================================================================================
    // Model Management
    // ====================================================================================

    /**
     * Get available models.
     */
    List<ModelInfo> getAvailableModels();

    /**
     * Load a specific model.
     *
     * @param modelId model identifier
     * @throws ModelLoadingError if model cannot be loaded
     */
    void loadModel(String modelId);

    /**
     * Get the currently active model.
     */
    ModelInfo getActiveModel();

    /**
     * Warm up the engine (pre-load models for lower latency).
     */
    void warmup();

    // ====================================================================================
    // Status & Metrics
    // ====================================================================================

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

/**
 * Streaming transcription session for real-time audio.
 */
public interface StreamingSession extends AutoCloseable {

    /**
     * Feed audio data to the streaming session.
     *
     * @param chunk audio chunk
     */
    void feedAudio(AudioChunk chunk);

    /**
     * Set callback for transcription results.
     *
     * @param callback called when transcription is available
     */
    void onTranscription(Consumer<StreamingTranscription> callback);

    /**
     * Set callback for errors.
     *
     * @param callback called when an error occurs
     */
    void onError(Consumer<ProcessingError> callback);

    /**
     * Signal end of audio stream.
     */
    void endStream();

    /**
     * Check if session is still active.
     */
    boolean isActive();

    @Override
    void close();
}

/**
 * Transcription options.
 */
public record TranscriptionOptions(
    Locale language,
    boolean enablePunctuation,
    boolean enableTimestamps,
    int maxAlternatives,
    boolean profanityFilter,
    String vocabulary,
    Duration timeout
) {
    public static TranscriptionOptions defaults() {
        return new TranscriptionOptions(
            Locale.getDefault(),
            true,   // punctuation
            false,  // timestamps
            1,      // alternatives
            false,  // profanity filter
            null,   // vocabulary
            Duration.ofSeconds(30)
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Locale language = Locale.getDefault();
        private boolean enablePunctuation = true;
        private boolean enableTimestamps = false;
        private int maxAlternatives = 1;
        private boolean profanityFilter = false;
        private String vocabulary;
        private Duration timeout = Duration.ofSeconds(30);

        public Builder language(Locale language) {
            this.language = language;
            return this;
        }

        public Builder enablePunctuation(boolean enable) {
            this.enablePunctuation = enable;
            return this;
        }

        public Builder enableTimestamps(boolean enable) {
            this.enableTimestamps = enable;
            return this;
        }

        public Builder maxAlternatives(int max) {
            this.maxAlternatives = max;
            return this;
        }

        public Builder profanityFilter(boolean enable) {
            this.profanityFilter = enable;
            return this;
        }

        public Builder vocabulary(String vocabulary) {
            this.vocabulary = vocabulary;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public TranscriptionOptions build() {
            return new TranscriptionOptions(
                language, enablePunctuation, enableTimestamps,
                maxAlternatives, profanityFilter, vocabulary, timeout
            );
        }
    }
}

/**
 * Transcription result.
 */
public record TranscriptionResult(
    String text,
    double confidence,
    List<WordTiming> words,
    List<Alternative> alternatives,
    Duration processingTime,
    String language,
    String modelId
) {
    /**
     * Get the transcribed text.
     */
    public String getText() {
        return text;
    }
}

/**
 * Word-level timing information.
 */
public record WordTiming(
    String word,
    double startSec,
    double endSec,
    double confidence
) {}

/**
 * Alternative transcription hypothesis.
 */
public record Alternative(
    String text,
    double confidence
) {}

/**
 * Streaming transcription output.
 */
public record StreamingTranscription(
    String text,
    boolean isFinal,
    double confidence,
    List<WordTiming> words
) {}

/**
 * User profile for personalized STT.
 */
public record UserProfile(
    String profileId,
    String displayName,
    Locale primaryLanguage,
    List<String> customVocabulary,
    byte[] speakerEmbedding
) {}

/**
 * Model information.
 */
public record ModelInfo(
    String modelId,
    String name,
    String version,
    Locale[] supportedLanguages,
    long sizeBytes,
    boolean supportsGpu
) {}
