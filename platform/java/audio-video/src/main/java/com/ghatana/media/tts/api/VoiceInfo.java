package com.ghatana.media.tts.api;

import java.util.Locale;

/**
 * Information about a voice.
 *
 * @doc.type record
 * @doc.purpose Metadata for an available TTS voice
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record VoiceInfo(
    String voiceId,
    String name,
    String description,
    Locale language,
    Gender gender,
    int sampleRate,
    boolean isCloned,
    long modelSizeBytes
) {
    public enum Gender {
        MALE, FEMALE, NEUTRAL
    }
}
