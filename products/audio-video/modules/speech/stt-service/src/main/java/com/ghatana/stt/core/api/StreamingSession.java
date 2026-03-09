package com.ghatana.stt.core.api;

import java.util.function.Consumer;

/**
 * A streaming transcription session for real-time audio processing.
 * 
 * <p>Usage:
 * <pre>{@code
 * StreamingSession session = engine.createStreamingSession(profile);
 * 
 * session.onTranscription(result -> {
 *     if (result.isFinal()) {
 *         System.out.println("Final: " + result.text());
 *     } else {
 *         System.out.println("Interim: " + result.text());
 *     }
 * });
 * 
 * session.onError(error -> System.err.println("Error: " + error.getMessage()));
 * 
 * session.start();
 * 
 * // Feed audio chunks as they arrive
 * while (hasMoreAudio) {
 *     session.feedAudio(audioChunk);
 * }
 * 
 * session.stop();
 * }</pre>
 * 
 * @doc.type interface
 * @doc.purpose Real-time streaming transcription session
 * @doc.layer api
 */
public interface StreamingSession extends AutoCloseable {

    /**
     * Get the unique session ID.
     */
    String getSessionId();

    /**
     * Start the streaming session.
     */
    void start();

    /**
     * Stop the streaming session and finalize any pending transcription.
     */
    void stop();

    /**
     * Feed an audio chunk to the session.
     * 
     * @param chunk Audio data chunk
     */
    void feedAudio(AudioChunk chunk);

    /**
     * Register a callback for transcription results.
     * 
     * @param callback Called when transcription results are available
     */
    void onTranscription(Consumer<TranscriptionResult> callback);

    /**
     * Register a callback for errors.
     * 
     * @param callback Called when an error occurs
     */
    void onError(Consumer<Throwable> callback);

    /**
     * Register a callback for session state changes.
     * 
     * @param callback Called when session state changes
     */
    void onStateChange(Consumer<SessionState> callback);

    /**
     * Get the current session state.
     */
    SessionState getState();

    /**
     * Get session statistics.
     */
    SessionStats getStats();

    /**
     * Audio chunk for streaming input.
     */
    record AudioChunk(
        byte[] data,
        int sampleRate,
        long timestampMs
    ) {
        public static AudioChunk of(byte[] data, int sampleRate) {
            return new AudioChunk(data, sampleRate, System.currentTimeMillis());
        }
    }

    /**
     * Session state enumeration.
     */
    enum SessionState {
        CREATED,
        STARTING,
        ACTIVE,
        STOPPING,
        STOPPED,
        ERROR
    }

    /**
     * Session statistics.
     */
    record SessionStats(
        long totalAudioMs,
        int chunksProcessed,
        int transcriptionsEmitted,
        float averageConfidence,
        float realTimeFactor
    ) {}
}
