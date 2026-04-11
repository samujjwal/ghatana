package com.ghatana.media.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/**
 * Speech-to-Text engine configuration.
 *
 * @doc.type record
 * @doc.purpose Configuration for embedded STT engine instances
 * @doc.layer platform
 * @doc.pattern Configuration
 */
public record SttConfig(
    Path modelPath,
    String modelId,
    boolean useGpu,
    int maxConcurrentRequests,
    Duration timeout,
    int beamSize,
    boolean enableAdaptation,
    boolean enablePunctuation,
    boolean enableTimestamps,
    Path profileStoragePath,
    String encryptionKeyId,
    Optional<CloudFallbackConfig> cloudFallback,
    int maxAudioLengthSeconds,
    long maxMemoryBytes
) {
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Path modelPath;
        private String modelId = "whisper-base";
        private boolean useGpu = false;
        private int maxConcurrentRequests = 10;
        private Duration timeout = Duration.ofSeconds(30);
        private int beamSize = 5;
        private boolean enableAdaptation = true;
        private boolean enablePunctuation = true;
        private boolean enableTimestamps = false;
        private Path profileStoragePath;
        private String encryptionKeyId;
        private CloudFallbackConfig cloudFallback;
        private int maxAudioLengthSeconds = 300;
        private long maxMemoryBytes = 512L * 1024 * 1024;

        public Builder modelPath(Path path) { this.modelPath = path; return this; }
        public Builder modelId(String value) { this.modelId = value; return this; }
        public Builder useGpu(boolean value) { this.useGpu = value; return this; }
        public Builder maxConcurrentRequests(int value) { this.maxConcurrentRequests = value; return this; }
        public Builder timeout(Duration value) { this.timeout = value; return this; }
        public Builder beamSize(int value) { this.beamSize = value; return this; }
        public Builder enableAdaptation(boolean value) { this.enableAdaptation = value; return this; }
        public Builder enablePunctuation(boolean value) { this.enablePunctuation = value; return this; }
        public Builder enableTimestamps(boolean value) { this.enableTimestamps = value; return this; }
        public Builder profileStoragePath(Path value) { this.profileStoragePath = value; return this; }
        public Builder encryptionKeyId(String value) { this.encryptionKeyId = value; return this; }
        public Builder cloudFallback(CloudFallbackConfig value) { this.cloudFallback = value; return this; }
        public Builder maxAudioLengthSeconds(int value) { this.maxAudioLengthSeconds = value; return this; }
        public Builder maxMemoryBytes(long value) { this.maxMemoryBytes = value; return this; }

        public SttConfig build() {
            return new SttConfig(
                modelPath,
                modelId,
                useGpu,
                maxConcurrentRequests,
                timeout,
                beamSize,
                enableAdaptation,
                enablePunctuation,
                enableTimestamps,
                profileStoragePath,
                encryptionKeyId,
                Optional.ofNullable(cloudFallback),
                maxAudioLengthSeconds,
                maxMemoryBytes
            );
        }
    }
}
