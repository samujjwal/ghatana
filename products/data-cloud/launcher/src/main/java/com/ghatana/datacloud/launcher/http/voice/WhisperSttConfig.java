package com.ghatana.datacloud.launcher.http.voice;

/**
 * Immutable configuration for the {@link HttpWhisperSttAdapter}.
 *
 * <p>Loaded from environment variables by {@link
 * com.ghatana.datacloud.launcher.http.DataCloudHttpServer} to keep credentials
 * out of source code and configuration files.
 *
 * @param enabled       whether the STT adapter is active; if false a no-op is used
 * @param endpointUrl   base URL of the Whisper-compatible endpoint (no trailing slash)
 * @param apiKey        Bearer API key; may be null for unauthenticated local endpoints
 * @param model         Whisper model name, e.g. {@code whisper-1}; null → default
 * @param maxAudioBytes max bytes accepted; 0 → use {@link HttpWhisperSttAdapter#DEFAULT_MAX_AUDIO_BYTES}
 *
 * @doc.type record
 * @doc.purpose Immutable config value object for Whisper STT adapter
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record WhisperSttConfig(
        boolean enabled,
        String endpointUrl,
        String apiKey,
        String model,
        int maxAudioBytes) {

    /**
     * Reads config from environment variables:
     * <ul>
     *   <li>{@code DC_STT_URL}     — base URL (required for STT to be enabled)</li>
     *   <li>{@code DC_STT_API_KEY} — API key (optional)</li>
     *   <li>{@code DC_STT_MODEL}   — model name (optional, default whisper-1)</li>
     *   <li>{@code DC_STT_MAX_BYTES} — max audio bytes (optional, default 25 MB)</li>
     * </ul>
     */
    public static WhisperSttConfig fromEnv() {
        String url = System.getenv("DC_STT_URL");
        if (url == null || url.isBlank()) {
            return new WhisperSttConfig(false, "", null, "whisper-1", 0);
        }
        String key      = System.getenv("DC_STT_API_KEY");
        String model    = System.getenv("DC_STT_MODEL");
        String maxBytes = System.getenv("DC_STT_MAX_BYTES");
        int maxBytesInt = 0;
        try {
            if (maxBytes != null) maxBytesInt = Integer.parseInt(maxBytes.trim());
        } catch (NumberFormatException ignored) { /* use default */ }

        return new WhisperSttConfig(true, url.strip(), key, model, maxBytesInt);
    }
}
