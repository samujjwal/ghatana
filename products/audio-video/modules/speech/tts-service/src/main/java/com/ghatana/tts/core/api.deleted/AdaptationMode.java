package com.ghatana.tts.core.api;

/**
 * Voice adaptation aggressiveness level.
 *
 * @doc.type enum
 * @doc.purpose Controls how aggressively the TTS engine adapts to user feedback
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum AdaptationMode {
    /** Minimal adaptation — preserves the original voice model. */
    CONSERVATIVE,
    /** Balanced adaptation — moderate learning from feedback. */
    BALANCED,
    /** Aggressive adaptation — fast learning, higher variance. */
    AGGRESSIVE
}
