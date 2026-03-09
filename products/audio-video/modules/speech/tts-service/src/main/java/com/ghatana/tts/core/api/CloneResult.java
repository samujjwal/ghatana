package com.ghatana.tts.core.api;

/**
 * Result of a voice cloning operation.
 * 
 * @doc.type record
 * @doc.purpose Voice clone result container
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record CloneResult(
    boolean success,
    String message,
    String voiceId,
    float similarityScore,
    VoiceInfo voice
) {
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public String getVoiceId() { return voiceId; }
    public float getSimilarityScore() { return similarityScore; }
    public VoiceInfo getVoice() { return voice; }
}
