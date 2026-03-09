package com.ghatana.stt.core.config;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configuration for the STT engine.
 * 
 * @doc.type record
 * @doc.purpose Engine configuration
 * @doc.layer config
 */
public record EngineConfig(
    /** Path to model directory */
    Path modelPath,
    
    /** Path to user data directory */
    Path dataPath,
    
    /** Default model to load on startup */
    String defaultModel,
    
    /** Model loading options */
    ModelConfig modelConfig,
    
    /** Adaptation configuration */
    AdaptationConfig adaptationConfig,
    
    /** Privacy configuration */
    PrivacyConfig privacyConfig,
    
    /** Performance configuration */
    PerformanceConfig performanceConfig
) {
    public EngineConfig {
        if (modelPath == null) {
            modelPath = Paths.get(System.getProperty("user.home"), ".speechtotext", "models");
        }
        if (dataPath == null) {
            dataPath = Paths.get(System.getProperty("user.home"), ".speechtotext", "data");
        }
        if (defaultModel == null) {
            defaultModel = "whisper-tiny";
        }
        if (modelConfig == null) {
            modelConfig = ModelConfig.defaults();
        }
        if (adaptationConfig == null) {
            adaptationConfig = AdaptationConfig.defaults();
        }
        if (privacyConfig == null) {
            privacyConfig = PrivacyConfig.defaults();
        }
        if (performanceConfig == null) {
            performanceConfig = PerformanceConfig.defaults();
        }
    }

    public static EngineConfig defaults() {
        return new EngineConfig(null, null, null, null, null, null, null);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Path modelPath;
        private Path dataPath;
        private String defaultModel;
        private ModelConfig modelConfig;
        private AdaptationConfig adaptationConfig;
        private PrivacyConfig privacyConfig;
        private PerformanceConfig performanceConfig;

        public Builder modelPath(Path path) {
            this.modelPath = path;
            return this;
        }

        public Builder modelPath(String path) {
            this.modelPath = Paths.get(path);
            return this;
        }

        public Builder dataPath(Path path) {
            this.dataPath = path;
            return this;
        }

        public Builder defaultModel(String model) {
            this.defaultModel = model;
            return this;
        }

        public Builder modelConfig(ModelConfig config) {
            this.modelConfig = config;
            return this;
        }

        public Builder adaptationConfig(AdaptationConfig config) {
            this.adaptationConfig = config;
            return this;
        }

        public Builder privacyConfig(PrivacyConfig config) {
            this.privacyConfig = config;
            return this;
        }

        public Builder performanceConfig(PerformanceConfig config) {
            this.performanceConfig = config;
            return this;
        }

        public EngineConfig build() {
            return new EngineConfig(
                modelPath,
                dataPath,
                defaultModel,
                modelConfig,
                adaptationConfig,
                privacyConfig,
                performanceConfig
            );
        }
    }
}
