/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.config;

import com.ghatana.aep.Aep;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Centralised AEP configuration manager (AEP-006).
 *
 * <p>Provides hierarchical, path-based access to AEP configuration so that
 * components do not need to depend on the exact shape of {@link Aep.AepConfig}
 * or import connector-specific config classes directly.
 *
 * <p><b>Hierarchy:</b> global defaults → tenant overrides → per-path custom values.
 * Tenant overrides win over global values; custom values win over tenant values.
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * AepConfigurationManager mgr = AepConfigurationManager.from(config);
 * int threads  = mgr.getInt("engine.workerThreads", config.workerThreads());
 * double thr   = mgr.getDouble("engine.anomalyThreshold", config.anomalyThreshold());
 * String val   = mgr.getString("custom.myKey", "default");
 * }</pre>
 *
 * <p>Custom configuration entries defined in {@link Aep.AepConfig#customConfig()} are
 * accessible via their key without any special prefix.
 *
 * @doc.type class
 * @doc.purpose Unified hierarchical AEP configuration access
 * @doc.layer product
 * @doc.pattern Configuration
 * @since 1.1.0
 */
public final class AepConfigurationManager {

    private final Aep.AepConfig engineConfig;
    private final Map<String, Map<String, Object>> tenantOverrides;
    private final Map<String, Object> globalCustom;

    private AepConfigurationManager(Aep.AepConfig engineConfig,
                                    Map<String, Map<String, Object>> tenantOverrides) {
        this.engineConfig = Objects.requireNonNull(engineConfig, "engineConfig must not be null");
        this.tenantOverrides = Collections.unmodifiableMap(new HashMap<>(tenantOverrides));
        // Flatten custom config for quick lookup
        Map<String, Object> custom = new HashMap<>(engineConfig.customConfig());
        this.globalCustom = Collections.unmodifiableMap(custom);
    }

    /**
     * Creates a manager backed by the supplied engine config with no tenant overrides.
     *
     * @param config the validated {@link Aep.AepConfig}; must not be {@code null}
     * @return configuration manager
     */
    public static AepConfigurationManager from(Aep.AepConfig config) {
        return new AepConfigurationManager(config, Map.of());
    }

    /**
     * Returns a new {@link Builder}.
     */
    public static Builder builder(Aep.AepConfig config) {
        return new Builder(config);
    }

    // ─── Typed getters ────────────────────────────────────────────────────────

    /**
     * Gets a configuration value for the given path, cast to the requested type.
     *
     * <p>Resolution order: tenant override → global custom config → {@code defaultValue}.
     *
     * @param path         dot-separated path to the value
     * @param type         expected type; used for validation
     * @param tenantId     tenant ID for override lookup; may be {@code null}
     * @param defaultValue value to return when the path is not set
     * @param <T>          expected type
     * @return resolved value, or {@code defaultValue} if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String path, Class<T> type, String tenantId, T defaultValue) {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(type, "type must not be null");

        // Tenant-level override
        if (tenantId != null) {
            Map<String, Object> tenantMap = tenantOverrides.get(tenantId);
            if (tenantMap != null && tenantMap.containsKey(path)) {
                Object value = tenantMap.get(path);
                return type.isInstance(value) ? type.cast(value) : defaultValue;
            }
        }

        // Global custom config
        if (globalCustom.containsKey(path)) {
            Object value = globalCustom.get(path);
            return type.isInstance(value) ? type.cast(value) : defaultValue;
        }

        // Built-in engine config properties
        Optional<T> builtin = resolveBuiltin(path, type);
        return builtin.orElse(defaultValue);
    }

    /** Convenience: get a String value. */
    public String getString(String path, String defaultValue) {
        return get(path, String.class, null, defaultValue);
    }

    /** Convenience: get an Integer value. */
    public int getInt(String path, int defaultValue) {
        Number n = get(path, Number.class, null, null);
        return n != null ? n.intValue() : defaultValue;
    }

    /** Convenience: get a Double value. */
    public double getDouble(String path, double defaultValue) {
        Number n = get(path, Number.class, null, null);
        return n != null ? n.doubleValue() : defaultValue;
    }

    /** Returns the underlying engine config. */
    public Aep.AepConfig engineConfig() {
        return engineConfig;
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private <T> Optional<T> resolveBuiltin(String path, Class<T> type) {
        Object value = switch (path) {
            case "engine.workerThreads"         -> engineConfig.workerThreads();
            case "engine.anomalyThreshold"      -> engineConfig.anomalyThreshold();
            case "engine.maxPipelinesPerTenant" -> engineConfig.maxPipelinesPerTenant();
            case "engine.instanceId"            -> engineConfig.instanceId();
            case "engine.enableMetrics"         -> engineConfig.enableMetrics();
            case "engine.enableTracing"         -> engineConfig.enableTracing();
            default                             -> null;
        };
        if (value != null && type.isInstance(value)) {
            return Optional.of(type.cast(value));
        }
        return Optional.empty();
    }

    // ─── Builder ──────────────────────────────────────────────────────────────

    /**
     * Fluent builder for constructing an {@link AepConfigurationManager} with tenant overrides.
     */
    public static final class Builder {

        private final Aep.AepConfig engineConfig;
        private final Map<String, Map<String, Object>> tenantOverrides = new HashMap<>();

        private Builder(Aep.AepConfig engineConfig) {
            this.engineConfig = Objects.requireNonNull(engineConfig);
        }

        /**
         * Adds per-tenant configuration overrides.
         *
         * @param tenantId  tenant to override; must not be blank
         * @param overrides key-value pairs overriding global config for this tenant
         */
        public Builder withTenantOverrides(String tenantId, Map<String, Object> overrides) {
            Objects.requireNonNull(tenantId, "tenantId must not be null");
            Objects.requireNonNull(overrides, "overrides must not be null");
            tenantOverrides.computeIfAbsent(tenantId, k -> new HashMap<>()).putAll(overrides);
            return this;
        }

        /** Builds the {@link AepConfigurationManager}. */
        public AepConfigurationManager build() {
            return new AepConfigurationManager(engineConfig, tenantOverrides);
        }
    }
}
