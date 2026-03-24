package com.ghatana.datacloud.launcher.http.voice;

import io.activej.promise.Promise;

/**
 * No-op implementation of {@link VoiceSttPort} used when no STT provider is configured.
 *
 * <p>Returning {@link SttTranscription#unavailable()} lets the {@link
 * com.ghatana.datacloud.launcher.http.handlers.VoiceGatewayHandler} emit a
 * structured error instructing callers to submit a pre-transcribed {@code utterance}
 * field instead, rather than failing silently or throwing.
 *
 * @doc.type class
 * @doc.purpose No-op STT adapter for deployments without an STT provider
 * @doc.layer product
 * @doc.pattern Adapter (null object)
 */
public final class NopVoiceSttAdapter implements VoiceSttPort {

    public static final NopVoiceSttAdapter INSTANCE = new NopVoiceSttAdapter();

    private NopVoiceSttAdapter() {}

    @Override
    public Promise<SttTranscription> transcribe(byte[] audioData, String audioFormat, String languageHint) {
        return Promise.of(SttTranscription.unavailable());
    }

    @Override
    public boolean isAvailable() {
        return false;
    }
}
