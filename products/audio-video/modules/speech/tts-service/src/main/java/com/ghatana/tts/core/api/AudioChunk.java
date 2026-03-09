package com.ghatana.tts.core.api;

/**
 * Audio chunk for streaming synthesis.
 * 
 * @doc.type record
 * @doc.purpose Streaming audio chunk
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record AudioChunk(
    byte[] audioData,
    int sampleRate,
    long timestampMs,
    boolean isFinal
) {
    public byte[] getAudioData() { return audioData; }
    public int getSampleRate() { return sampleRate; }
    public long getTimestampMs() { return timestampMs; }
    public boolean isFinal() { return isFinal; }
}
