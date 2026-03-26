package com.ghatana.tts.core.api;

/**
 * Snapshot of TTS engine operational status.
 *
 * @doc.type record
 * @doc.purpose Carries current engine health and active-voice information
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record EngineStatus(
        EngineState state,
        String activeVoice,
        String errorMessage,
        int activeSessions
) {}
