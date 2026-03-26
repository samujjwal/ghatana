package com.ghatana.tts.core.api;

/**
 * Represents the operational state of the TTS engine.
 *
 * @doc.type enum
 * @doc.purpose Domain enum for TTS engine lifecycle state
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum EngineState {
    /** Engine is starting up. */
    INITIALIZING,
    /** Engine is ready to process requests. */
    READY,
    /** Engine is currently processing one or more requests. */
    BUSY,
    /** Engine encountered an unrecoverable error. */
    ERROR,
    /** Engine has been shut down. */
    SHUTDOWN
}
