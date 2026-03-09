// filepath: /Users/samujjwal/Development/ghatana/products/shared-services/text-to-speech/libs/tts-core-java/src/main/java/com/ghatana/tts/core/config/EngineConfig.java
package com.ghatana.tts.core.config;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * TTS engine configuration.
 *
 * @doc.type record
 * @doc.purpose Engine configuration container
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record EngineConfig(
    Path modelsDirectory,
    Path profilesDirectory,
    Path cacheDirectory,
    String defaultVoice,
    int defaultSampleRate,
    int maxConcurrentSessions,
    boolean enableOnnxOptimizations,
    boolean enableGpuAcceleration
) {
    public static Builder builder() {
        return new Builder();
    }

    public static EngineConfig defaults() {
        return builder().build();
    }

    public static class Builder {
        private Path modelsDirectory = Paths.get(System.getProperty("user.home"), ".ghatana", "speech", "tts", "models");
        private Path profilesDirectory = Paths.get(System.getProperty("user.home"), ".ghatana", "speech", "tts", "profiles");
        private Path cacheDirectory = Paths.get(System.getProperty("user.home"), ".ghatana", "speech", "tts", "cache");
        private String defaultVoice = "default-en";
        private int defaultSampleRate = 22050;
        private int maxConcurrentSessions = 4;
        private boolean enableOnnxOptimizations = true;
        private boolean enableGpuAcceleration = false;

        public Builder modelsDirectory(Path modelsDirectory) {
            this.modelsDirectory = modelsDirectory;
            return this;
        }

        public Builder profilesDirectory(Path profilesDirectory) {
            this.profilesDirectory = profilesDirectory;
            return this;
        }

        public Builder cacheDirectory(Path cacheDirectory) {
            this.cacheDirectory = cacheDirectory;
            return this;
        }

        public Builder defaultVoice(String defaultVoice) {
            this.defaultVoice = defaultVoice;
            return this;
        }

        public Builder defaultSampleRate(int defaultSampleRate) {
            this.defaultSampleRate = defaultSampleRate;
            return this;
        }

        public Builder maxConcurrentSessions(int maxConcurrentSessions) {
            this.maxConcurrentSessions = maxConcurrentSessions;
            return this;
        }

        public Builder enableOnnxOptimizations(boolean enableOnnxOptimizations) {
            this.enableOnnxOptimizations = enableOnnxOptimizations;
            return this;
        }

        public Builder enableGpuAcceleration(boolean enableGpuAcceleration) {
            this.enableGpuAcceleration = enableGpuAcceleration;
            return this;
        }

        public EngineConfig build() {
            return new EngineConfig(
                modelsDirectory,
                profilesDirectory,
                cacheDirectory,
                defaultVoice,
                defaultSampleRate,
                maxConcurrentSessions,
                enableOnnxOptimizations,
                enableGpuAcceleration
            );
        }
    }
}

