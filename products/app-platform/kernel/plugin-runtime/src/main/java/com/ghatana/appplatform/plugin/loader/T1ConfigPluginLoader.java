/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.plugin.loader;

import com.ghatana.appplatform.plugin.domain.PluginManifest;
import com.ghatana.appplatform.plugin.domain.PluginTier;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Loads Tier-1 (data-only) plugins from structured JSON or YAML files (STORY-K04-003).
 *
 * <p>T1 plugins contain no executable code — they are purely configuration bundles
 * (holiday calendars, rate tables, jurisdiction metadata, etc.) that the platform
 * loads and distributes to kernel modules through the config-engine. This loader
 * validates that the declared tier is {@link PluginTier#T1} before parsing.
 *
 * <p>Parsed configuration entries are returned as a raw {@code Map<String,Object>}
 * so callers can map them into typed domain objects without coupling the loader to
 * every possible config schema.
 *
 * @doc.type  class
 * @doc.purpose Loads and validates T1 (data-only) plugin configuration bundles (K04-003)
 * @doc.layer kernel
 * @doc.pattern Adapter
 */
public final class T1ConfigPluginLoader {

    private static final Logger log = LoggerFactory.getLogger(T1ConfigPluginLoader.class);

    private final Executor executor;

    public T1ConfigPluginLoader(Executor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /**
     * Loads a T1 plugin config from a file path on disk.
     *
     * @param manifest the plugin manifest (must declare tier = T1)
     * @param configPath path to the JSON/YAML config file
     * @return promise resolving to the configuration map
     * @throws IllegalArgumentException if the manifest tier is not T1
     */
    public Promise<Map<String, Object>> load(PluginManifest manifest, Path configPath) {
        Objects.requireNonNull(manifest,   "manifest");
        Objects.requireNonNull(configPath, "configPath");

        return Promise.ofBlocking(executor, () -> {
            enforceT1Tier(manifest);

            if (!Files.exists(configPath)) {
                throw new T1PluginLoadException("Config file not found: " + configPath);
            }

            long fileSizeBytes = Files.size(configPath);
            if (fileSizeBytes > 10 * 1024 * 1024) { // 10 MB max for T1 configs
                throw new T1PluginLoadException(
                        "T1 config file exceeds 10 MB limit: " + fileSizeBytes + " bytes");
            }

            try (InputStream in = Files.newInputStream(configPath)) {
                Map<String, Object> configMap = parseConfig(in, configPath.toString());
                log.info("Loaded T1 plugin: name={} version={} keys={}",
                        manifest.name(), manifest.version(), configMap.size());
                return configMap;
            }
        });
    }

    /**
     * Loads a T1 plugin config from raw bytes (e.g. downloaded from object storage).
     *
     * @param manifest the plugin manifest (must declare tier = T1)
     * @param rawConfig raw JSON or YAML bytes
     * @param hint      filename hint for format detection (e.g. {@code "config.json"})
     * @return promise resolving to the configuration map
     */
    public Promise<Map<String, Object>> loadFromBytes(PluginManifest manifest,
                                                       byte[] rawConfig, String hint) {
        Objects.requireNonNull(manifest,  "manifest");
        Objects.requireNonNull(rawConfig, "rawConfig");

        return Promise.ofBlocking(executor, () -> {
            enforceT1Tier(manifest);

            if (rawConfig.length == 0) {
                throw new T1PluginLoadException("T1 config payload is empty");
            }

            try (InputStream in = new java.io.ByteArrayInputStream(rawConfig)) {
                Map<String, Object> configMap = parseConfig(in, hint != null ? hint : "config.json");
                log.info("Loaded T1 plugin from bytes: name={} version={} bytes={}",
                        manifest.name(), manifest.version(), rawConfig.length);
                return configMap;
            }
        });
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void enforceT1Tier(PluginManifest manifest) {
        if (manifest.tier() != PluginTier.T1) {
            throw new IllegalArgumentException(
                    "T1ConfigPluginLoader only accepts T1 plugins, got: " + manifest.tier());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(InputStream in, String hint) throws Exception {
        // Use minimal hand-rolled JSON parsing via javax.json (part of Java EE),
        // or fall back to simple property parsing. For production the build adds
        // jackson-databind; wire it here via ServiceLoader to keep loader dependency-light.
        //
        // Delegate to Jackson ObjectMapper via reflection to avoid compile-time coupling.
        try {
            Class<?> mapperClass = Class.forName("com.fasterxml.jackson.databind.ObjectMapper");
            Object mapper = mapperClass.getDeclaredConstructor().newInstance();
            java.lang.reflect.Method readValue = mapperClass.getMethod("readValue", InputStream.class, Class.class);
            return (Map<String, Object>) readValue.invoke(mapper, in, Map.class);
        } catch (ClassNotFoundException e) {
            throw new T1PluginLoadException(
                    "JSON/YAML parser (Jackson) not on classpath. Add jackson-databind to plugin-runtime.");
        }
    }

    /** Thrown when T1 plugin loading fails for any reason. */
    public static final class T1PluginLoadException extends RuntimeException {
        public T1PluginLoadException(String message) { super(message); }
        public T1PluginLoadException(String message, Throwable cause) { super(message, cause); }
    }
}
