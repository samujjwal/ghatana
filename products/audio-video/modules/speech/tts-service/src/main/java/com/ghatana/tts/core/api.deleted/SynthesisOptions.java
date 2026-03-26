package com.ghatana.tts.core.api;

/**
 * Synthesis tuning options provided per-request.
 *
 * @doc.type record
 * @doc.purpose Domain value object carrying per-synthesis tuning parameters
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record SynthesisOptions(
        String voiceId,
        float speed,
        float pitch,
        float energy,
        String emotion,
        String language
) {
    public static SynthesisOptions defaults() {
        return new SynthesisOptions(null, 1.0f, 0.0f, 1.0f, "neutral", "en-US");
    }
}
