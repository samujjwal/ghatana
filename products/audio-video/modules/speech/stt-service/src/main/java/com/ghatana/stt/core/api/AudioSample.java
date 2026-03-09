package com.ghatana.stt.core.api;

/**
 * Audio sample for enrollment or training.
 * 
 * @doc.type record
 * @doc.purpose Audio sample with transcript for training
 * @doc.layer api
 */
public record AudioSample(
    /** Audio data */
    AudioData audio,
    
    /** Ground truth transcript */
    String transcript,
    
    /** Sample identifier */
    String sampleId
) {
    /**
     * Create a sample from audio and transcript.
     */
    public static AudioSample of(AudioData audio, String transcript) {
        return new AudioSample(audio, transcript, null);
    }
}
