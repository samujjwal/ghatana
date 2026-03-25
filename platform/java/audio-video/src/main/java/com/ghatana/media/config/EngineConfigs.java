/**
 * @doc.type config
 * @doc.purpose Configuration classes for all audio-video engines
 * @doc.layer config
 */
package com.ghatana.media.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Speech-to-Text engine configuration.
 */
public record SttConfig(
    // Model configuration
    Path modelPath,
    String modelId,
    boolean useGpu,

    // Performance settings
    int maxConcurrentRequests,
    Duration timeout,
    int beamSize,

    // Feature toggles
    boolean enableAdaptation,
    boolean enablePunctuation,
    boolean enableTimestamps,

    // Storage settings
    Path profileStoragePath,
    String encryptionKeyId,

    // Fallback configuration
    Optional<CloudFallbackConfig> cloudFallback,

    // Resource limits
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
        private int maxAudioLengthSeconds = 300; // 5 minutes
        private long maxMemoryBytes = 512 * 1024 * 1024; // 512MB

        public Builder modelPath(Path path) {
            this.modelPath = path;
            return this;
        }

        public Builder modelId(String modelId) {
            this.modelId = modelId;
            return this;
        }

        public Builder useGpu(boolean use) {
            this.useGpu = use;
            return this;
        }

        public Builder maxConcurrentRequests(int max) {
            this.maxConcurrentRequests = max;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder beamSize(int beamSize) {
            this.beamSize = beamSize;
            return this;
        }

        public Builder enableAdaptation(boolean enable) {
            this.enableAdaptation = enable;
            return this;
        }

        public Builder enablePunctuation(boolean enable) {
            this.enablePunctuation = enable;
            return this;
        }

        public Builder enableTimestamps(boolean enable) {
            this.enableTimestamps = enable;
            return this;
        }

        public Builder profileStoragePath(Path path) {
            this.profileStoragePath = path;
            return this;
        }

        public Builder encryptionKeyId(String keyId) {
            this.encryptionKeyId = keyId;
            return this;
        }

        public Builder cloudFallback(CloudFallbackConfig config) {
            this.cloudFallback = config;
            return this;
        }

        public Builder maxAudioLengthSeconds(int seconds) {
            this.maxAudioLengthSeconds = seconds;
            return this;
        }

        public Builder maxMemoryBytes(long bytes) {
            this.maxMemoryBytes = bytes;
            return this;
        }

        public SttConfig build() {
            return new SttConfig(
                modelPath, modelId, useGpu, maxConcurrentRequests, timeout,
                beamSize, enableAdaptation, enablePunctuation, enableTimestamps,
                profileStoragePath, encryptionKeyId, Optional.ofNullable(cloudFallback),
                maxAudioLengthSeconds, maxMemoryBytes
            );
        }
    }
}

/**
 * Text-to-Speech engine configuration.
 */
public record TtsConfig(
    // Voice configuration
    Path voiceModelPath,
    String defaultVoiceId,
    List<String> availableVoices,

    // Performance settings
    int maxConcurrentRequests,
    Duration timeout,
    boolean useGpu,

    // Audio settings
    int sampleRate,
    int maxTextLength,

    // Feature toggles
    boolean enableProsody,
    boolean enableVoiceCloning,
    boolean enableStreaming,

    // Storage settings
    Path profileStoragePath,
    Path clonedVoicesPath,

    // Fallback configuration
    Optional<CloudFallbackConfig> cloudFallback,

    // Resource limits
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
        private long maxMemoryBytes = 256 * 1024 * 1024; // 256MB

        public Builder voiceModelPath(Path path) {
            this.voiceModelPath = path;
            return this;
        }

        public Builder defaultVoiceId(String voiceId) {
            this.defaultVoiceId = voiceId;
            return this;
        }

        public Builder availableVoices(List<String> voices) {
            this.availableVoices = voices;
            return this;
        }

        public Builder maxConcurrentRequests(int max) {
            this.maxConcurrentRequests = max;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder useGpu(boolean use) {
            this.useGpu = use;
            return this;
        }

        public Builder sampleRate(int rate) {
            this.sampleRate = rate;
            return this;
        }

        public Builder maxTextLength(int length) {
            this.maxTextLength = length;
            return this;
        }

        public Builder enableProsody(boolean enable) {
            this.enableProsody = enable;
            return this;
        }

        public Builder enableVoiceCloning(boolean enable) {
            this.enableVoiceCloning = enable;
            return this;
        }

        public Builder enableStreaming(boolean enable) {
            this.enableStreaming = enable;
            return this;
        }

        public Builder profileStoragePath(Path path) {
            this.profileStoragePath = path;
            return this;
        }

        public Builder clonedVoicesPath(Path path) {
            this.clonedVoicesPath = path;
            return this;
        }

        public Builder cloudFallback(CloudFallbackConfig config) {
            this.cloudFallback = config;
            return this;
        }

        public Builder maxMemoryBytes(long bytes) {
            this.maxMemoryBytes = bytes;
            return this;
        }

        public TtsConfig build() {
            return new TtsConfig(
                voiceModelPath, defaultVoiceId, availableVoices, maxConcurrentRequests,
                timeout, useGpu, sampleRate, maxTextLength, enableProsody,
                enableVoiceCloning, enableStreaming, profileStoragePath, clonedVoicesPath,
                Optional.ofNullable(cloudFallback), maxMemoryBytes
            );
        }
    }
}

/**
 * Vision engine configuration.
 */
public record VisionConfig(
    // Model configuration
    Path modelPath,
    String modelId,
    String modelType,
    boolean useGpu,

    // Performance settings
    int maxConcurrentRequests,
    Duration timeout,
    int batchSize,

    // Detection settings
    double defaultConfidenceThreshold,
    int defaultMaxDetections,
    int inputSize,

    // Feature toggles
    boolean enableTracking,
    boolean enableSegmentation,
    boolean enableClassification,

    // Resource limits
    long maxMemoryBytes
) {
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Path modelPath;
        private String modelId = "yolov8n";
        private String modelType = "yolo";
        private boolean useGpu = false;
        private int maxConcurrentRequests = 10;
        private Duration timeout = Duration.ofSeconds(10);
        private int batchSize = 1;
        private double defaultConfidenceThreshold = 0.5;
        private int defaultMaxDetections = 100;
        private int inputSize = 640;
        private boolean enableTracking = false;
        private boolean enableSegmentation = false;
        private boolean enableClassification = true;
        private long maxMemoryBytes = 512 * 1024 * 1024; // 512MB

        public Builder modelPath(Path path) {
            this.modelPath = path;
            return this;
        }

        public Builder modelId(String modelId) {
            this.modelId = modelId;
            return this;
        }

        public Builder modelType(String type) {
            this.modelType = type;
            return this;
        }

        public Builder useGpu(boolean use) {
            this.useGpu = use;
            return this;
        }

        public Builder maxConcurrentRequests(int max) {
            this.maxConcurrentRequests = max;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder batchSize(int size) {
            this.batchSize = size;
            return this;
        }

        public Builder defaultConfidenceThreshold(double threshold) {
            this.defaultConfidenceThreshold = threshold;
            return this;
        }

        public Builder defaultMaxDetections(int max) {
            this.defaultMaxDetections = max;
            return this;
        }

        public Builder inputSize(int size) {
            this.inputSize = size;
            return this;
        }

        public Builder enableTracking(boolean enable) {
            this.enableTracking = enable;
            return this;
        }

        public Builder enableSegmentation(boolean enable) {
            this.enableSegmentation = enable;
            return this;
        }

        public Builder enableClassification(boolean enable) {
            this.enableClassification = enable;
            return this;
        }

        public Builder maxMemoryBytes(long bytes) {
            this.maxMemoryBytes = bytes;
            return this;
        }

        public VisionConfig build() {
            return new VisionConfig(
                modelPath, modelId, modelType, useGpu, maxConcurrentRequests,
                timeout, batchSize, defaultConfidenceThreshold, defaultMaxDetections,
                inputSize, enableTracking, enableSegmentation, enableClassification,
                maxMemoryBytes
            );
        }
    }
}

/**
 * Cloud fallback configuration for when local models fail.
 */
public record CloudFallbackConfig(
    String endpoint,
    String apiKey,
    Duration timeout,
    int maxRetries,
    List<String> fallbackModels
) {
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String endpoint;
        private String apiKey;
        private Duration timeout = Duration.ofSeconds(30);
        private int maxRetries = 3;
        private List<String> fallbackModels = List.of();

        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder maxRetries(int retries) {
            this.maxRetries = retries;
            return this;
        }

        public Builder fallbackModels(List<String> models) {
            this.fallbackModels = models;
            return this;
        }

        public CloudFallbackConfig build() {
            return new CloudFallbackConfig(endpoint, apiKey, timeout, maxRetries, fallbackModels);
        }
    }
}
