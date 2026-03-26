package com.ghatana.tts.core.api;

/**
 * Result of a TTS synthesis operation.
 *
 * @doc.type record
 * @doc.purpose Carries synthesised audio and associated metadata
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record SynthesisResult(
        byte[] audioData,
        int sampleRate,
        long durationMs,
        String voiceUsed
) {}
