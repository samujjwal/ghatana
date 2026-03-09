package com.ghatana.stt.core.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Result of a transcription operation.
 * 
 * <p>Contains the transcribed text along with confidence scores,
 * word-level timings, and metadata about the transcription.
 * 
 * @doc.type record
 * @doc.purpose Immutable transcription result data
 * @doc.layer api
 */
public record TranscriptionResult(
    /** The transcribed text */
    String text,
    
    /** Overall confidence score (0.0 to 1.0) */
    float confidence,
    
    /** Word-level timing information */
    List<WordTiming> wordTimings,
    
    /** Processing time in milliseconds */
    long processingTimeMs,
    
    /** The model used for transcription */
    String modelUsed,
    
    /** Language detected or used */
    String language,
    
    /** Whether this is a final result (vs interim) */
    boolean isFinal
) {
    public TranscriptionResult {
        Objects.requireNonNull(text, "text must not be null");
        wordTimings = wordTimings != null ? List.copyOf(wordTimings) : List.of();
    }

    /**
     * Create a simple result with just text and confidence.
     */
    public static TranscriptionResult of(String text, float confidence) {
        return new TranscriptionResult(text, confidence, List.of(), 0, null, null, true);
    }

    /**
     * Create an interim (non-final) result.
     */
    public static TranscriptionResult interim(String text, float confidence) {
        return new TranscriptionResult(text, confidence, List.of(), 0, null, null, false);
    }

    /**
     * Create a builder for constructing TranscriptionResult.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for TranscriptionResult.
     */
    public static class Builder {
        private String text = "";
        private float confidence = 0.0f;
        private List<WordTiming> wordTimings = new ArrayList<>();
        private long processingTimeMs = 0;
        private String modelUsed;
        private String language;
        private boolean isFinal = true;

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder confidence(float confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder wordTimings(List<WordTiming> wordTimings) {
            this.wordTimings = wordTimings;
            return this;
        }

        public Builder processingTimeMs(long processingTimeMs) {
            this.processingTimeMs = processingTimeMs;
            return this;
        }

        public Builder modelUsed(String modelUsed) {
            this.modelUsed = modelUsed;
            return this;
        }

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public Builder isFinal(boolean isFinal) {
            this.isFinal = isFinal;
            return this;
        }

        public TranscriptionResult build() {
            return new TranscriptionResult(text, confidence, wordTimings, processingTimeMs, modelUsed, language, isFinal);
        }
    }

    /**
     * Word-level timing information.
     */
    public record WordTiming(
        String word,
        long startMs,
        long endMs,
        float confidence
    ) {
        public long durationMs() {
            return endMs - startMs;
        }
    }
}
