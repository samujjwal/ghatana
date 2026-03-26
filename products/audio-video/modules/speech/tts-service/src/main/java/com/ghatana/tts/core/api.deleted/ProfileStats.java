package com.ghatana.tts.core.api;

/**
 * Aggregate usage statistics for a user profile.
 *
 * @doc.type record
 * @doc.purpose Tracks lifetime TTS usage for a profile
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ProfileStats(
        long totalSynthesisTimeMs,
        int totalCharactersSynthesized,
        long createdAtMs,
        long lastUsedAtMs
) {}
