package com.ghatana.tts.core.api;

/**
 * TTS engine state.
 * 
 * @doc.type enum
 * @doc.purpose Engine state enumeration
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum EngineState {
    INITIALIZING,
    READY,
    BUSY,
    ERROR,
    SHUTDOWN
}
