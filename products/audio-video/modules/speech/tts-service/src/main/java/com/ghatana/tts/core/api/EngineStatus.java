package com.ghatana.tts.core.api;

/**
 * TTS engine status.
 * 
 * @doc.type record
 * @doc.purpose Engine status container
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record EngineStatus(
    EngineState state,
    String activeVoice,
    String errorMessage,
    int activeSessions
) {
    public EngineState getState() { return state; }
    public String getActiveVoice() { return activeVoice; }
    public String getErrorMessage() { return errorMessage; }
    public int getActiveSessions() { return activeSessions; }
}
