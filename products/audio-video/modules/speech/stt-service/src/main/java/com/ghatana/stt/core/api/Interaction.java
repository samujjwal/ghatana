package com.ghatana.stt.core.api;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a user interaction for adaptation learning.
 * 
 * <p>An interaction captures the original transcription, any user corrections,
 * and associated audio features for model adaptation.
 * 
 * @doc.type record
 * @doc.purpose User interaction data for adaptation
 * @doc.layer api
 */
public record Interaction(
    /** Original transcription produced by the engine */
    String originalTranscript,
    
    /** User-corrected transcript (null if no correction) */
    String correctedTranscript,
    
    /** Audio features for acoustic adaptation (optional) */
    float[] audioFeatures,
    
    /** Context ID where this interaction occurred */
    String contextId,
    
    /** Timestamp of the interaction */
    Instant timestamp,
    
    /** Duration of the audio in milliseconds */
    long audioDurationMs
) {
    public Interaction {
        Objects.requireNonNull(originalTranscript, "originalTranscript must not be null");
        timestamp = timestamp != null ? timestamp : Instant.now();
    }

    /**
     * Check if this interaction has a user correction.
     */
    public boolean hasCorrectedTranscript() {
        return correctedTranscript != null && !correctedTranscript.equals(originalTranscript);
    }

    /**
     * Check if this interaction has audio features for acoustic adaptation.
     */
    public boolean hasAudioFeatures() {
        return audioFeatures != null && audioFeatures.length > 0;
    }

    /**
     * Create an interaction from a correction.
     */
    public static Interaction fromCorrection(String original, String corrected) {
        return new Interaction(original, corrected, null, null, Instant.now(), 0);
    }

    /**
     * Create an interaction with audio features.
     */
    public static Interaction withAudio(String transcript, float[] features, long durationMs) {
        return new Interaction(transcript, null, features, null, Instant.now(), durationMs);
    }
}
