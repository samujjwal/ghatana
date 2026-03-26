package com.ghatana.tts.core.api;

import java.util.List;

/**
 * Metadata describing an available TTS voice.
 *
 * @doc.type record
 * @doc.purpose Carries voice capability and availability metadata
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record VoiceInfo(
        String voiceId,
        String name,
        String description,
        List<String> languages,
        String gender,
        long sizeBytes,
        boolean isLoaded,
        boolean isCloned
) {}
