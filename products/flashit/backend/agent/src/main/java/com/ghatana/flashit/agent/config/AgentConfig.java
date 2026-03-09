package com.ghatana.flashit.agent.config;

/**
 * Agent service configuration loaded from environment variables.
 *
 * @doc.type class
 * @doc.purpose Centralizes all configuration for the FlashIt Agent Service
 * @doc.layer product
 * @doc.pattern Configuration
 */
public class AgentConfig {

    private final String openAiApiKey;
    private final String openAiModel;
    private final String embeddingModel;
    private final String whisperModel;
    private final int serverPort;
    private final int requestTimeoutMs;

    public AgentConfig() {
        this.openAiApiKey = env("OPENAI_API_KEY", "");
        this.openAiModel = env("OPENAI_MODEL", "gpt-4o");
        this.embeddingModel = env("OPENAI_EMBEDDING_MODEL", "text-embedding-3-small");
        this.whisperModel = env("WHISPER_MODEL", "whisper-1");
        this.serverPort = Integer.parseInt(env("AGENT_PORT", "8090"));
        this.requestTimeoutMs = Integer.parseInt(env("AGENT_REQUEST_TIMEOUT_MS", "30000"));
    }

    public String getOpenAiApiKey() {
        return openAiApiKey;
    }

    public String getOpenAiModel() {
        return openAiModel;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public String getWhisperModel() {
        return whisperModel;
    }

    public int getServerPort() {
        return serverPort;
    }

    public int getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    public boolean isOpenAiConfigured() {
        return openAiApiKey != null && !openAiApiKey.isBlank();
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }
}
