package com.ghatana.datacloud.launcher.http.voice;

import io.activej.promise.Promise;

/**
 * Port interface for server-side text-to-speech synthesis.
 *
 * <p>Implementations bridge data-cloud's voice gateway to an optional TTS
 * provider and degrade gracefully via a no-op when no provider is wired.
 *
 * <p><b>Architecture note</b><br>
 * This port complements {@link VoiceSttPort}: whereas STT converts incoming
 * audio to text, TTS converts the server's {@code speechSummary} text back
 * to audio bytes that can be streamed to the caller.  Both ports are
 * optional — the voice gateway operates in text-only mode when the TTS port
 * reports {@link #isAvailable()} {@code false}.
 *
 * @doc.type interface
 * @doc.purpose Port interface for optional server-side TTS synthesis
 * @doc.layer product
 * @doc.pattern Port (hexagonal architecture)
 */
public interface VoiceTtsPort {

    /**
     * Returns {@code true} when a real TTS provider is wired and available.
     * When {@code false}, callers should omit audio from the response.
     */
    boolean isAvailable();

    /**
    * Synthesize the given text to audio bytes using the configured TTS provider.
     *
     * <p>Privacy: callers MUST pass only server-generated speech summaries — never
     * raw user input or tenant PII. The resulting audio bytes should be treated
     * as ephemeral; implementations must not persist them.
     *
     * @param text         text to synthesize (required, non-blank)
     * @param languageHint BCP-47 locale hint, e.g. {@code "en-US"} (may be null)
     * @return Promise resolving to raw audio bytes (WAV/PCM), or an empty array
     *         when synthesis fails
     */
    Promise<byte[]> synthesize(String text, String languageHint);
}
