package com.ghatana.media.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Text-to-Speech engine configuration.
 *
 * @doc.type record
 * @doc.purpose Configuration for embedded TTS engine instances
 * @doc.layer platform
 * @doc.pattern Configuration
 */
public record TtsConfig(
    Path voiceModelPath,
    String defaultVoiceId,
    List<String> availableVoices,
    int maxConcurrentRequests,
    Duration timeout,
    boolean useGpu,
    int sampleRate,
    int maxTextLength,
    boolean enableProsody,
    boolean enableVoiceCloning,
    boolean enableStreaming,
    Path profileStoragePath,
    Path clonedVoicesPath,
    Optional<CloudFallbackConfig> cloudFallback,
    long maxMemoryBytes
) {
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Path voiceModelPath;
        private String defaultVoiceId = "piper-en";
        private List<String> availableVoices = List.of();
        private int maxConcurrentRequests = 10;
        private Duration timeout = Duration.ofSeconds(30);
        private boolean useGpu = false;
        private int sampleRate = 22050;
        private int maxTextLength = 5000;
        private boolean enableProsody = true;
        private boolean enableVoiceCloning = false;
        private boolean enableStreaming = true;
        private Path profileStoragePath;
        private Path clonedVoicesPath;
        private CloudFallbackConfig cloudFallback;
        private long maxMemoryBytes = 256L * 1024 * 1024;

        public Builder voiceModelPath(Path value) { this.voiceModelPath = value; return this; }
        public Builder defaultVoiceId(String value) { this.defaultVoiceId = value; return this; }
        public Builder availableVoices(List<String> value) { this.availableVoices = value; return this; }
        public Builder maxConcurrentRequests(int value) { this.maxConcurrentRequests = value; return this; }
        public Builder timeout(Duration value) { this.timeout = value; return this; }
        public Builder useGpu(boolean value) { this.useGpu = value; return this; }
        public Builder sampleRate(int value) { this.sampleRate = value; return this; }
        public Builder maxTextLength(int value) { this.maxTextLength = value; return this; }
        public Builder enableProsody(boolean value) { this.enableProsody = value; return this; }
        public Builder enableVoiceCloning(boolean value) { this.enableVoiceCloning = value; return this; }
        public Builder enableStreaming(boolean value) { this.enableStreaming = value; return this; }
        public Builder profileStoragePath(Path value) { this.profileStoragePath = value; return this; }
        public Builder clonedVoicesPath(Path value) { this.clonedVoicesPath = value; return this; }
        public Builder cloudFallback(CloudFallbackConfig value) { this.cloudFallback = value; return this; }
        public Builder maxMemoryBytes(long value) { this.maxMemoryBytes = value; return this; }

        public TtsConfig build() {
            return new TtsConfig(
                voiceModelPath,
                defaultVoiceId,
                availableVoices,
                maxConcurrentRequests,
                timeout,
                useGpu,
                sampleRate,
                maxTextLength,
                enableProsody,
                enableVoiceCloning,
                enableStreaming,
                profileStoragePath,
                clonedVoicesPath,
                Optional.ofNullable(cloudFallback),
                maxMemoryBytes
            );
        }
    }
}