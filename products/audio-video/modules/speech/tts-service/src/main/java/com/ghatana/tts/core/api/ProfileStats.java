package com.ghatana.tts.core.api;

/**
 * Profile statistics for TTS.
 * 
 * @doc.type record
 * @doc.purpose Profile statistics container
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ProfileStats(
    long totalSynthesisTimeMs,
    int totalCharactersSynthesized,
    long createdAtMs,
    long lastUsedAtMs
) {
    public long getTotalSynthesisTimeMs() { return totalSynthesisTimeMs; }
    public int getTotalCharactersSynthesized() { return totalCharactersSynthesized; }
    public long getCreatedAtMs() { return createdAtMs; }
    public long getLastUsedAtMs() { return lastUsedAtMs; }
}
