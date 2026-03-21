/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.config;

import com.ghatana.aep.config.EnvConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Dynamic configuration overlay service for AEP.
 *
 * <p>Provides a mutable key→value overlay on top of the immutable {@link EnvConfig}
 * snapshot. Callers can push live configuration changes at runtime without restarting
 * the process. Every change is:
 * <ol>
 *   <li>Validated for type safety (numeric checks) before being applied.</li>
 *   <li>Atomically written to the overlay map.</li>
 *   <li>Broadcast to all registered {@link ChangeListener change listeners}.</li>
 * </ol>
 *
 * <h3>Resolution order</h3>
 * <ol>
 *   <li>Dynamic overlay (set via {@link #set(String, String)}).</li>
 *   <li>Underlying {@link EnvConfig} (environment variables at startup).</li>
 *   <li>Caller-supplied default.</li>
 * </ol>
 *
 * <h3>Usage example</h3>
 * <pre>{@code
 * AepDynamicConfigService cfg =
 *     new AepDynamicConfigService(EnvConfig.fromSystem(), meterRegistry);
 *
 * // Listen for KAFKA_BOOTSTRAP_SERVERS changes
 * cfg.addChangeListener((key, oldVal, newVal) -> {
 *     if (EnvConfig.KAFKA_BOOTSTRAP_SERVERS.equals(key)) {
 *         kafkaClient.reconnect(newVal);
 *     }
 * });
 *
 * // Runtime override (e.g. from a remote config push)
 * cfg.set(EnvConfig.KAFKA_BOOTSTRAP_SERVERS, "broker1:9092,broker2:9092");
 * }</pre>
 *
 * <p><b>Thread safety:</b> all methods are safe for concurrent access.
 *
 * @doc.type class
 * @doc.purpose Dynamic configuration overlay with listener-based hot-reload for AEP
 * @doc.layer product
 * @doc.pattern Configuration, Observer
 */
public final class AepDynamicConfigService {

    private static final Logger log = LoggerFactory.getLogger(AepDynamicConfigService.class);

    private final EnvConfig                    baseConfig;
    private final ConcurrentHashMap<String, String>   overlay   = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<ChangeListener> listeners = new CopyOnWriteArrayList<>();
    private final List<ConfigChange>           changeLog = new CopyOnWriteArrayList<>();
    private final Counter                      setCounter;
    private final Counter                      validationErrorCounter;

    // ─────────────────────────────────────────────────────────────────────────
    //  Constructor
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a dynamic config service with the given base configuration.
     *
     * @param baseConfig    startup environment config (immutable baseline)
     * @param meterRegistry Micrometer registry for operational metrics
     */
    public AepDynamicConfigService(EnvConfig baseConfig, MeterRegistry meterRegistry) {
        this.baseConfig = Objects.requireNonNull(baseConfig, "baseConfig must not be null");
        Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
        this.setCounter             = meterRegistry.counter("aep.config.set.total");
        this.validationErrorCounter = meterRegistry.counter("aep.config.set.errors");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Read API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the effective value for {@code key}.
     *
     * <p>Resolution order: overlay → env → {@code defaultValue}.
     *
     * @param key          configuration key (use {@link EnvConfig} constants)
     * @param defaultValue fallback when the key is absent in all sources
     * @return effective value
     */
    public String get(String key, String defaultValue) {
        Objects.requireNonNull(key, "key must not be null");
        String overridden = overlay.get(key);
        if (overridden != null) return overridden;
        return baseConfig.get(key, defaultValue);
    }

    /**
     * Returns the effectice integer value for {@code key}.
     *
     * @param key          configuration key
     * @param defaultValue fallback integer
     * @return effective integer
     * @throws IllegalStateException if the resolved value is not a valid integer
     */
    public int getInt(String key, int defaultValue) {
        Objects.requireNonNull(key, "key must not be null");
        String overridden = overlay.get(key);
        if (overridden != null) {
            try {
                return Integer.parseInt(overridden.trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException(
                        "Dynamic override for '" + key + "' must be an integer, but got: " + overridden, e);
            }
        }
        return baseConfig.getInt(key, defaultValue);
    }

    /**
     * Returns true if `key` has either an overlay or a non-blank env value.
     *
     * @param key configuration key
     * @return true if the key is present
     */
    public boolean isSet(String key) {
        return overlay.containsKey(key) || baseConfig.get(key, null) != null;
    }

    /**
     * Returns an immutable snapshot of all currently active overlay keys and values.
     *
     * @return overlay snapshot
     */
    public Map<String, String> overlaySnapshot() {
        return Map.copyOf(overlay);
    }

    /**
     * Returns all config changes applied since startup, newest first.
     *
     * @return unmodifiable list of config changes
     */
    public List<ConfigChange> changeHistory() {
        List<ConfigChange> copy = new ArrayList<>(changeLog);
        Collections.reverse(copy);
        return Collections.unmodifiableList(copy);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Write API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Applies a dynamic config override.
     *
     * <p>The previous overlay value (or base-config value) is captured, then
     * the new value is stored atomically. All registered {@link ChangeListener listeners}
     * are notified synchronously.
     *
     * @param key   configuration key; must not be blank
     * @param value new value; must not be blank
     * @throws IllegalArgumentException if key or value is blank
     * @throws IllegalArgumentException if the key is an integer key and {@code value} is not parseable
     */
    public void set(String key, String value) {
        Objects.requireNonNull(key, "key must not be null");
        if (key.isBlank()) throw new IllegalArgumentException("key must not be blank");
        if (value == null || value.isBlank()) throw new IllegalArgumentException("value must not be blank for key: " + key);

        // Validate known integer keys
        if (isKnownIntegerKey(key)) {
            try {
                Integer.parseInt(value.trim());
            } catch (NumberFormatException e) {
                validationErrorCounter.increment();
                throw new IllegalArgumentException(
                        "Key '" + key + "' expects an integer value, but got: " + value, e);
            }
        }

        String oldValue = overlay.put(key, value);
        if (oldValue == null) {
            // Fall back to base config for "previous value" in the change event
            oldValue = baseConfig.get(key, null);
        }

        setCounter.increment();
        ConfigChange change = new ConfigChange(key, oldValue, value, Instant.now());
        changeLog.add(change);

        log.info("Config override applied: key={} oldValue={} newValue={}", key, oldValue, value);
        notifyListeners(change);
    }

    /**
     * Applies multiple overrides atomically (fires listeners once per entry).
     *
     * @param overrides map of key→value pairs
     * @throws IllegalArgumentException if any key is blank, any value is blank, or any integer key
     *                                  receives a non-integer value
     */
    public void setAll(Map<String, String> overrides) {
        Objects.requireNonNull(overrides, "overrides must not be null");
        // Validate all first, then apply — fail-fast before partial writes
        overrides.forEach((k, v) -> {
            if (k == null || k.isBlank())   throw new IllegalArgumentException("key must not be blank");
            if (v == null || v.isBlank())   throw new IllegalArgumentException("value for key '" + k + "' must not be blank");
            if (isKnownIntegerKey(k)) {
                try { Integer.parseInt(v.trim()); }
                catch (NumberFormatException ex) {
                    throw new IllegalArgumentException(
                            "Key '" + k + "' expects an integer, but got: " + v, ex);
                }
            }
        });
        overrides.forEach(this::set);
    }

    /**
     * Removes a dynamic override, reverting that key to its environment/default value.
     *
     * @param key configuration key to clear
     * @return the removed override, or {@code null} if none was set
     */
    public String clear(String key) {
        Objects.requireNonNull(key, "key must not be null");
        String removed = overlay.remove(key);
        if (removed != null) {
            String nowValue = baseConfig.get(key, null);
            ConfigChange change = new ConfigChange(key, removed, nowValue, Instant.now());
            changeLog.add(change);
            log.info("Config override cleared: key={} (reverted to {})", key, nowValue);
            notifyListeners(change);
        }
        return removed;
    }

    /**
     * Clears all dynamic overrides, reverting to baseline environment config.
     */
    public void clearAll() {
        List<String> keys = new ArrayList<>(overlay.keySet());
        keys.forEach(this::clear);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Listeners
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Registers a change listener that is notified whenever a config key changes.
     *
     * @param listener the listener to register
     */
    public void addChangeListener(ChangeListener listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        listeners.add(listener);
    }

    /**
     * Removes a previously registered change listener.
     *
     * @param listener the listener to remove
     * @return true if the listener was found and removed
     */
    public boolean removeChangeListener(ChangeListener listener) {
        return listeners.remove(listener);
    }

    /**
     * Returns the number of registered listeners.
     *
     * @return listener count
     */
    public int listenerCount() {
        return listeners.size();
    }

    private void notifyListeners(ConfigChange change) {
        for (ChangeListener listener : listeners) {
            try {
                listener.onConfigChanged(change.key(), change.oldValue(), change.newValue());
            } catch (Exception ex) {
                log.error("ChangeListener threw exception for key={}: {}", change.key(), ex.getMessage(), ex);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Integer key detection
    // ─────────────────────────────────────────────────────────────────────────

    private static final Set<String> INTEGER_KEYS = Set.of(
            EnvConfig.RABBITMQ_PORT,
            EnvConfig.REDIS_PORT,
            EnvConfig.AEP_DB_POOL_SIZE,
            EnvConfig.AEP_CONSOLIDATION_INTERVAL_HOURS
    );

    private static boolean isKnownIntegerKey(String key) {
        return INTEGER_KEYS.contains(key);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Public API Types
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Callback interface for config change notifications.
     *
     * <p>Implementations must be non-blocking (called on the calling thread of {@link #set}).
     */
    @FunctionalInterface
    public interface ChangeListener {
        /**
         * Called when a config key's effective value changes.
         *
         * @param key      the configuration key that changed
         * @param oldValue previous effective value (may be {@code null} if first override)
         * @param newValue new effective value
         */
        void onConfigChanged(String key, String oldValue, String newValue);
    }

    /**
     * Immutable record of a single configuration change event.
     *
     * @param key       the configuration key that changed
     * @param oldValue  value before the change (may be {@code null})
     * @param newValue  value after the change (may be {@code null} if cleared)
     * @param changedAt when the change was applied
     */
    public record ConfigChange(
            String  key,
            String  oldValue,
            String  newValue,
            Instant changedAt) {}
}
