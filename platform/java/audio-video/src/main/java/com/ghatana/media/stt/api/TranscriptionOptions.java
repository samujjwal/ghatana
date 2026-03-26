package com.ghatana.media.stt.api;

import java.time.Duration;
import java.util.Locale;

/**
 * Transcription options.
 *
 * @doc.type record
 * @doc.purpose Immutable STT transcription options
 * @doc.layer platform
 * @doc.pattern ValueObject
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
        return new TranscriptionOptions(Locale.getDefault(), true, false, 1, false, null, Duration.ofSeconds(30));
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

        public Builder language(Locale value) { this.language = value; return this; }
        public Builder enablePunctuation(boolean value) { this.enablePunctuation = value; return this; }
        public Builder enableTimestamps(boolean value) { this.enableTimestamps = value; return this; }
        public Builder maxAlternatives(int value) { this.maxAlternatives = value; return this; }
        public Builder profanityFilter(boolean value) { this.profanityFilter = value; return this; }
        public Builder vocabulary(String value) { this.vocabulary = value; return this; }
        public Builder timeout(Duration value) { this.timeout = value; return this; }

        public TranscriptionOptions build() {
            return new TranscriptionOptions(language, enablePunctuation, enableTimestamps, maxAlternatives, profanityFilter, vocabulary, timeout);
        }
    }
}