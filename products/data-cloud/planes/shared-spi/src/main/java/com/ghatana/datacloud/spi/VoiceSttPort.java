/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.spi;

import io.activej.promise.Promise;

/**
 * Port interface for Speech-to-Text transcription.
 *
 * <p>WS3-11: Moved from launcher to shared-spi to enable MediaProcessorPort adapters
 * to access STT functionality across module boundaries. This port decouples media
 * processing from specific STT providers (Whisper, cloud API, etc.) using the
 * port/adapter pattern.
 *
 * <p>Implementations must return a {@link Promise} for non-blocking integration
 * with the ActiveJ event loop via {@code Promise.ofBlocking}.
 *
 * @doc.type interface
 * @doc.purpose STT port — decouples media processing from STT providers
 * @doc.layer product
 * @doc.pattern Port (Hexagonal Architecture)
 */
public interface VoiceSttPort {

    /**
     * Transcribes raw audio bytes into text.
     *
     * @param audioData   raw audio bytes (PCM / WAV / MP3 / OPUS — provider-dependent)
     * @param audioFormat MIME hint, e.g. "audio/wav", "audio/mpeg", "audio/opus"; may be null
     * @param languageHint BCP-47 language tag, e.g. "en", "es"; null for auto-detect
     * @return promise resolving to the transcription result; never null
     */
    Promise<SttTranscription> transcribe(byte[] audioData, String audioFormat, String languageHint);

    /**
     * Returns whether this adapter can actually perform transcription.
     * A {@code false} result means the no-op adapter is active and callers
     * should prompt users to submit text utterances instead.
     */
    default boolean isAvailable() {
        return true;
    }
}
