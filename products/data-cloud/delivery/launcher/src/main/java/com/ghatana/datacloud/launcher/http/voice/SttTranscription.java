package com.ghatana.datacloud.launcher.http.voice;

/**
 * Immutable result from a Speech-to-Text transcription call.
 *
 * @param text       transcribed text; never null, may be empty if audio was silent
 * @param confidence 0.0–1.0 confidence score reported by the provider (0.0 if unavailable)
 * @param provider   identifier of the STT provider that produced this result
 * @param fallback   true when the result came from a no-op or fallback path
 *
 * @doc.type record
 * @doc.purpose STT transcription value object
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record SttTranscription(
        String text,
        double confidence,
        String provider,
        boolean fallback) {

    /**
     * Constructs a result from a real provider with a known confidence score.
     */
    public static SttTranscription of(String text, double confidence, String provider) {
        return new SttTranscription(text, confidence, provider, false);
    }

    /**
     * Constructs a fallback result returned when no STT provider is configured.
     */
    public static SttTranscription unavailable() {
        return new SttTranscription("", 0.0, "nop", true);
    }
}
