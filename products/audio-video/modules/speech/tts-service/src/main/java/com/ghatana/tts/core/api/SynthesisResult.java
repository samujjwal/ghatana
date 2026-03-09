package com.ghatana.tts.core.api;

/**
 * Result of a synthesis operation.
 * 
 * @doc.type record
 * @doc.purpose Synthesis result container
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record SynthesisResult(
    byte[] audioData,
    int sampleRate,
    long durationMs,
    String voiceUsed
) {
    public byte[] getAudioData() { return audioData; }
    public int getSampleRate() { return sampleRate; }
    public long getDurationMs() { return durationMs; }
    public String getVoiceUsed() { return voiceUsed; }
}
