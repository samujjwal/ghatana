/*
 * Copyright (c) 2025 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.yappc.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simplified Unified Configuration Manager for Phase 4 architectural improvements.
 *
 * <p>Provides centralized configuration management with YAML/JSON support and validation.
 * Hot-reloading capabilities will be added in a future iteration.
 *
 * <p>Week 10 Day 50: Phase 4 architectural improvements - Configuration Management Strategy
 *
 * @doc.type class
 * @doc.purpose Simplified Unified Configuration Manager for Phase 4 architectural improvements.
 * @doc.layer platform
 * @doc.pattern Manager
 */
public class SimpleUnifiedConfigurationManager implements AutoCloseable {

    private static final Logger log =
            LoggerFactory.getLogger(SimpleUnifiedConfigurationManager.class);

    private final ObjectMapper yamlMapper;
    private final ObjectMapper jsonMapper;
    private YappcConfiguration currentConfig;

    public SimpleUnifiedConfigurationManager() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory()).registerModule(new JavaTimeModule());
        this.jsonMapper = JsonUtils.getDefaultMapper().registerModule(new JavaTimeModule());

        try {
            this.currentConfig = loadConfiguration();
            log.info("Configuration manager initialized successfully");
        } catch (IOException e) {
            log.warn("Failed to load configuration, using defaults: {}", e.getMessage());
            this.currentConfig = createDefaultConfiguration();
        }
    }

    // Public accessors for configuration sections
    public TelemetryConfig getTelemetryConfig() {
        return currentConfig.telemetry;
    }

    public CacheConfig getCacheConfig() {
        return currentConfig.cache;
    }

    public SecurityConfig getSecurityConfig() {
        return currentConfig.security;
    }

    public ObservabilityConfig getObservabilityConfig() {
        return currentConfig.observability;
    }

    @Override
    public void close() throws Exception {
        // Cleanup resources if needed
        log.debug("Configuration manager closed");
    }

    // Configuration loading logic
    private YappcConfiguration loadConfiguration() throws IOException {
        Path configPath = Paths.get(".yappc", "config.yaml");
        if (Files.exists(configPath)) {
            log.info("Loading configuration from: {}", configPath);
            return yamlMapper.readValue(configPath.toFile(), YappcConfiguration.class);
        }

        // Try JSON format
        configPath = Paths.get(".yappc", "config.json");
        if (Files.exists(configPath)) {
            log.info("Loading configuration from: {}", configPath);
            return jsonMapper.readValue(configPath.toFile(), YappcConfiguration.class);
        }

        return createDefaultConfiguration();
    }

    private YappcConfiguration createDefaultConfiguration() {
        YappcConfiguration config = new YappcConfiguration();
        config.telemetry = new TelemetryConfig();
        config.cache = new CacheConfig();
        config.security = new SecurityConfig();
        config.observability = new ObservabilityConfig();
        return config;
    }

    // Configuration data classes
    public static class YappcConfiguration {
        public TelemetryConfig telemetry = new TelemetryConfig();
        public CacheConfig cache = new CacheConfig();
        public SecurityConfig security = new SecurityConfig();
        public ObservabilityConfig observability = new ObservabilityConfig();
    }

    public static class TelemetryConfig {
        public boolean enabled = false;
        public boolean metricsEnabled = false;
        public String serviceName = "yappc";
        public int exportTimeoutSeconds = 30;
    }

    public static class CacheConfig {
        public int maxSize = 1000;
        public String evictionPolicy = "LRU";
        public boolean metricsEnabled = true;
    }

    public static class SecurityConfig {
        public boolean enableCorrelationIds = true;
        public String tokenSecret = System.getenv().getOrDefault("YAPPC_TOKEN_SECRET", "");
        public boolean enableAuditLogging = false;
        public boolean enableInputValidation = true;
    }

    public static class ObservabilityConfig {
        public boolean tracingEnabled = false;
        public boolean metricsEnabled = false;
        public boolean loggingEnabled = true;
        public boolean healthChecksEnabled = true;
        public String otlpEndpoint = "";
    }
}
