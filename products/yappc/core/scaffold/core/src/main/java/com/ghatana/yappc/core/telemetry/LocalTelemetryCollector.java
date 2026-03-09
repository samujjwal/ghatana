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
package com.ghatana.yappc.core.telemetry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.yappc.core.telemetry.model.LocalTelemetryConfiguration;
import com.ghatana.yappc.core.telemetry.model.TelemetryEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import io.activej.promise.Promise;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Privacy-first telemetry collector with explicit user consent. Collects usage
 * data only when opted-in and stores locally for privacy compliance.
 *
 * <p>
 * Features: - Explicit opt-in required before any data collection - Data
 * anonymization removing personally identifiable information - Local-only
 * storage in .yappc/telemetry/ directory - Complete data clearing on opt-out -
 * No external data transmission
 *
 * @doc.type class
 * @doc.purpose Privacy-first telemetry collector with explicit user consent.
 * Collects usage data only when
 * @doc.layer platform
 * @doc.pattern Component
 */
public class LocalTelemetryCollector
        implements com.ghatana.yappc.framework.api.services.TelemetryCollector {

    private static final Logger log = LoggerFactory.getLogger(LocalTelemetryCollector.class);

    private static final Executor BLOCKING_EXECUTOR = Executors.newCachedThreadPool();


    private static final String CONFIG_FILE = "telemetry-config.json";

    private final ObjectMapper objectMapper;
    private final Path telemetryDir;
    private final ExecutorService executorService;
    private LocalTelemetryConfiguration configuration;
    private final Map<String, Instant> activeTraces;

    public LocalTelemetryCollector() {
        this.objectMapper = JsonUtils.getDefaultMapper().registerModule(new JavaTimeModule());
        this.telemetryDir
                = Path.of(System.getProperty("user.home")).resolve(".yappc").resolve("telemetry");

        this.executorService
                = Executors.newSingleThreadExecutor(
                        r -> {
                            Thread t = new Thread(r, "telemetry-collector");
                            t.setDaemon(true);
                            return t;
                        });

        // Ensure telemetry directory exists
        try {
            Files.createDirectories(telemetryDir);
            this.configuration = loadConfiguration();
        } catch (IOException e) {
            log.error("Warning: Could not create telemetry directory: {}", e.getMessage());
            this.configuration = LocalTelemetryConfiguration.builder().optedIn(false).build();
        }
        this.activeTraces = new ConcurrentHashMap<>();
    }

    /**
     * Records a telemetry event if user has opted in. Data is anonymized and
     * stored locally only.
     */
    @Override
    public void recordEvent(String eventName, Map<String, Object> properties) {
        if (properties == null) {
            properties = Map.of();
        }

        TelemetryEvent.Builder builder
                = TelemetryEvent.builder()
                        .eventType(eventName)
                        .command((String) properties.getOrDefault("command", eventName))
                        .success(
                                properties.containsKey("success")
                                ? Boolean.TRUE.equals(properties.get("success"))
                                : true)
                        .durationMs(
                                ((Number) properties.getOrDefault("durationMs", 0L)).longValue())
                        .projectType((String) properties.get("projectType"))
                        .language((String) properties.get("language"))
                        .framework((String) properties.get("framework"))
                        .packName((String) properties.get("packName"))
                        .errorType((String) properties.get("errorType"));

        recordEvent(builder.build());
    }

    public Promise<Void> recordEvent(TelemetryEvent event) {
        if (!isOptedIn()) {
            return Promise.of(null);
        }

        return Promise.ofBlocking(BLOCKING_EXECUTOR, 
                () -> {
                    try {
                        // Anonymize event data
                        TelemetryEvent anonymizedEvent = anonymizeEvent(event);

                        // Store locally
                        storeEventLocally(anonymizedEvent);

                    } catch (Exception e) {
                        log.error("Error recording telemetry event: {}", e.getMessage());
                    }
                },
                executorService);
    }

    @Override
    public void recordMetric(String metricName, double value, Map<String, String> tags) {
        Map<String, String> tagMap = tags != null ? tags : Map.of();
        TelemetryEvent.Builder builder
                = TelemetryEvent.builder()
                        .eventType("metric")
                        .command(metricName)
                        .success(true)
                        .durationMs(0)
                        .framework(tagMap.get("framework"))
                        .projectType(tagMap.get("projectType"))
                        .language(tagMap.get("language"))
                        .packName(String.valueOf(value));

        recordEvent(builder.build());
    }

    @Override
    public void startTrace(String operationName) {
        if (!isOptedIn()) {
            return;
        }
        activeTraces.put(operationName, Instant.now());
    }

    @Override
    public void endTrace(String operationName) {
        Instant start = activeTraces.remove(operationName);
        if (start == null) {
            return;
        }
        long duration = Duration.between(start, Instant.now()).toMillis();
        recordEvent(
                TelemetryEvent.builder()
                        .eventType("trace")
                        .command(operationName)
                        .success(true)
                        .durationMs(duration)
                        .build());
    }

    @Override
    public void close() {
        executorService.shutdown();
        activeTraces.clear();
    }

    /**
     * Opts the user in to telemetry collection.
     */
    public void optIn() throws IOException {
        configuration = configuration.withOptedIn(true);
        saveConfiguration();
        log.info("✅ Telemetry collection enabled");
        log.info("📊 Usage data will be collected locally to help improve YAPPC");
    }

    /**
     * Opts the user out and clears all collected data.
     */
    public void optOut() throws IOException {
        configuration = configuration.withOptedIn(false);
        saveConfiguration();

        // Clear all collected telemetry data
        clearAllData();

        log.info("❌ Telemetry collection disabled");
        log.info("🗑️  All collected data has been removed");
    }

    /**
     * Checks if user has opted in to telemetry collection.
     */
    public boolean isOptedIn() {
        return configuration.isOptedIn();
    }

    /**
     * Gets current telemetry configuration.
     */
    public LocalTelemetryConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Anonymizes telemetry event data to remove PII.
     */
    private TelemetryEvent anonymizeEvent(TelemetryEvent event) {
        return TelemetryEvent.builder()
                .eventType(event.getEventType())
                .command(event.getCommand())
                .success(event.isSuccess())
                .durationMs(event.getDurationMs())
                .timestamp(event.getTimestamp())
                .projectType(event.getProjectType())
                .language(event.getLanguage())
                .framework(event.getFramework())
                .packName(event.getPackName())
                .errorType(event.getErrorType())
                // Remove any potentially identifying information
                .userId(null) // Clear user ID for privacy
                .workspacePath(null) // Clear workspace path for privacy
                .build();
    }

    /**
     * Stores telemetry event locally as JSONL.
     */
    private void storeEventLocally(TelemetryEvent event) throws IOException {
        LocalDate today = LocalDate.now();
        Path dailyLogFile = telemetryDir.resolve("events-" + today + ".jsonl");

        String eventJson = objectMapper.writeValueAsString(event);
        Files.writeString(
                dailyLogFile,
                eventJson + "\n",
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
    }

    /**
     * Loads telemetry configuration from disk.
     */
    private LocalTelemetryConfiguration loadConfiguration() {
        Path configPath = telemetryDir.resolve(CONFIG_FILE);
        try {
            if (Files.exists(configPath)) {
                return objectMapper.readValue(configPath.toFile(), LocalTelemetryConfiguration.class);
            }
        } catch (IOException e) {
            log.error("Warning: Could not load telemetry configuration: {}", e.getMessage());
        }

        // Return default configuration (opted out)
        return LocalTelemetryConfiguration.builder()
                .optedIn(false)
                .version("1.0.0")
                .consentVersion("1")
                .createdAt(Instant.now())
                .build();
    }

    /**
     * Saves telemetry configuration to disk.
     */
    private void saveConfiguration() throws IOException {
        Path configPath = telemetryDir.resolve(CONFIG_FILE);

        // Create new configuration with updated timestamp
        LocalTelemetryConfiguration updatedConfig
                = LocalTelemetryConfiguration.builder()
                        .optedIn(configuration.isOptedIn())
                        .version(configuration.getVersion())
                        .createdAt(configuration.getCreatedAt())
                        .updatedAt(Instant.now())
                        .consentVersion(configuration.getConsentVersion())
                        .build();

        this.configuration = updatedConfig;

        String configJson
                = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(configuration);

        Files.writeString(
                configPath,
                configJson,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
    }

    /**
     * Clears all collected telemetry data.
     */
    private void clearAllData() throws IOException {
        if (Files.exists(telemetryDir)) {
            Files.walk(telemetryDir)
                    .filter(path -> path.getFileName().toString().startsWith("events-"))
                    .forEach(
                            path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException e) {
                                    log.error("Warning: Could not delete {}: {}", path, e.getMessage());
                                }
                            });
        }
    }

    /**
     * Shuts down the telemetry collector.
     */
    public void shutdown() {
        executorService.shutdown();
    }
}
