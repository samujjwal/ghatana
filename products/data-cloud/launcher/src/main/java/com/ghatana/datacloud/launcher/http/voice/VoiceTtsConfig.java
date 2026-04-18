package com.ghatana.datacloud.launcher.http.voice;

/**
 * Immutable configuration for the {@link HttpSpeechTtsAdapter}.
 *
 * <p>Loaded from environment variables by
 * {@link com.ghatana.datacloud.launcher.http.DataCloudHttpServer}.
 *
 * @param enabled whether the adapter is enabled
 * @param endpointUrl base URL of the TTS provider
 * @param apiKey bearer API key, optional for local endpoints
 * @param model model name, optional
 * @param voice voice identifier, optional
 * @param responseFormat output audio format, optional
 *
 * @doc.type record
 * @doc.purpose Immutable config for HTTP TTS adapter
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record VoiceTtsConfig(
        boolean enabled,
        String endpointUrl,
        String apiKey,
        String model,
        String voice,
        String responseFormat) {

    public static VoiceTtsConfig fromEnv() {
        String url = System.getenv("DC_TTS_URL");
        if (url == null || url.isBlank()) {
            return new VoiceTtsConfig(false, "", null, "gpt-4o-mini-tts", "alloy", "wav");
        }

        return new VoiceTtsConfig(
            true,
            url.strip(),
            System.getenv("DC_TTS_API_KEY"),
            defaultIfBlank(System.getenv("DC_TTS_MODEL"), "gpt-4o-mini-tts"),
            defaultIfBlank(System.getenv("DC_TTS_VOICE"), "alloy"),
            defaultIfBlank(System.getenv("DC_TTS_FORMAT"), "wav")
        );
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }
}