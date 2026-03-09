package com.ghatana.stt.core.api;

/**
 * Current status of the STT engine.
 * 
 * @doc.type record
 * @doc.purpose Engine status information
 * @doc.layer api
 */
public record EngineStatus(
    /** Current engine state */
    State state,
    
    /** Active model ID */
    String activeModelId,
    
    /** Number of active streaming sessions */
    int activeSessions,
    
    /** Error message if in ERROR state */
    String errorMessage
) {
    public enum State {
        INITIALIZING,
        READY,
        BUSY,
        ERROR,
        SHUTDOWN
    }

    public boolean isReady() {
        return state == State.READY || state == State.BUSY;
    }

    public boolean hasError() {
        return state == State.ERROR;
    }

    public static EngineStatus ready(String modelId) {
        return new EngineStatus(State.READY, modelId, 0, null);
    }

    public static EngineStatus initializing() {
        return new EngineStatus(State.INITIALIZING, null, 0, null);
    }

    public static EngineStatus error(String message) {
        return new EngineStatus(State.ERROR, null, 0, message);
    }
}
