package com.ghatana.datacloud.launcher.http.voice;

import io.activej.promise.Promise;

/**
 * No-op {@link VoiceTtsPort} that returns an empty audio byte array.
 *
 * <p>Used when no concrete TTS provider is configured.
 * The voice gateway detects {@link #isAvailable()} {@code false} and omits
 * the {@code audioBase64} field from its response, allowing the browser-side
 * {@code useSpeechSynthesis} hook to handle playback instead.
 *
 * @doc.type class
 * @doc.purpose No-op VoiceTtsPort fallback when server-side TTS is inactive
 * @doc.layer product
 * @doc.pattern Null Object
 */
public final class NopVoiceTtsAdapter implements VoiceTtsPort {

    /** Singleton — stateless, safe to share. */
    public static final NopVoiceTtsAdapter INSTANCE = new NopVoiceTtsAdapter();

    private NopVoiceTtsAdapter() {}

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public Promise<byte[]> synthesize(String text, String languageHint) {
        return Promise.of(new byte[0]);
    }
}
