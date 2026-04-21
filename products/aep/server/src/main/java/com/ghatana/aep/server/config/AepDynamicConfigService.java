/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.config;

import com.ghatana.aep.config.AepConfigurationValidator;
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
    private final List<ConfigAuditEntry>       auditLog = new CopyOnWriteArrayList<>();
    private final Counter                      setCounter;
    private final Counter                      validationErrorCounter;
    private final Counter                      rollbackCounter;
    private final Counter                      listenerFailureCounter;

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
        this.rollbackCounter        = meterRegistry.counter("aep.config.rollback.total");
        this.listenerFailureCounter = meterRegistry.counter("aep.config.listener.errors");
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

    /**
     * Returns config audit entries applied since startup, newest first.
     *
     * @return unmodifiable list of config audit entries
     */
    public List<ConfigAuditEntry> auditHistory() {
        List<ConfigAuditEntry> copy = new ArrayList<>(auditLog);
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
        applyOverrides(Map.of(key, value));
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
        applyOverrides(overrides);
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
            try {
                notifyListeners(change);
                changeLog.add(change);
                auditLog.add(new ConfigAuditEntry(key, removed, nowValue, AuditStatus.APPLIED, "override cleared", change.changedAt()));
                log.info("Config override cleared: key={} (reverted to {})", key, nowValue);
            } catch (RuntimeException exception) {
                overlay.put(key, removed);
                rollbackCounter.increment();
                auditLog.add(new ConfigAuditEntry(key, removed, nowValue, AuditStatus.ROLLED_BACK, exception.getMessage(), Instant.now()));
                throw exception;
            }
        }
        return removed;
    }

    /**
     * Clears all dynamic overrides, reverting to baseline environment config.
     */
    public void clearAll() {
        Map<String, String> snapshot = new LinkedHashMap<>(overlay);
        if (snapshot.isEmpty()) {
            return;
        }

        List<ConfigChange> changes = new ArrayList<>();
        Instant changedAt = Instant.now();
        snapshot.forEach((key, value) -> {
            overlay.remove(key);
            changes.add(new ConfigChange(key, value, baseConfig.get(key, null), changedAt));
        });

        try {
            for (ConfigChange change : changes) {
                notifyListeners(change);
                changeLog.add(change);
                auditLog.add(new ConfigAuditEntry(
                        change.key(),
                        change.oldValue(),
                        change.newValue(),
                        AuditStatus.APPLIED,
                        "override cleared",
                        change.changedAt()));
            }
        } catch (RuntimeException exception) {
            overlay.clear();
            overlay.putAll(snapshot);
            rollbackCounter.increment();
            changes.forEach(change -> auditLog.add(new ConfigAuditEntry(
                    change.key(),
                    change.oldValue(),
                    change.newValue(),
                    AuditStatus.ROLLED_BACK,
                    exception.getMessage(),
                    Instant.now())));
            throw exception;
        }
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
                listenerFailureCounter.increment();
                log.error("ChangeListener rejected config change for key={}: {}", change.key(), ex.getMessage(), ex);
                throw new IllegalStateException("Failed to apply config change for key '" + change.key() + "'", ex);
            }
        }
    }

    private void applyOverrides(Map<String, String> overrides) {
        LinkedHashMap<String, String> orderedOverrides = new LinkedHashMap<>(overrides);
        Instant changedAt = Instant.now();
        for (Map.Entry<String, String> entry : orderedOverrides.entrySet()) {
            validateOverride(entry.getKey(), entry.getValue(), changedAt);
        }

        Map<String, String> previousOverlayValues = new HashMap<>();
        List<ConfigChange> changes = new ArrayList<>();
        orderedOverrides.forEach((key, value) -> {
            previousOverlayValues.put(key, overlay.get(key));
            changes.add(new ConfigChange(key, get(key, null), value, changedAt));
            overlay.put(key, value);
        });

        try {
            for (ConfigChange change : changes) {
                notifyListeners(change);
            }
        } catch (RuntimeException exception) {
            restoreOverlay(previousOverlayValues);
            rollbackCounter.increment();
            changes.forEach(change -> auditLog.add(new ConfigAuditEntry(
                    change.key(),
                    change.oldValue(),
                    change.newValue(),
                    AuditStatus.ROLLED_BACK,
                    exception.getMessage(),
                    Instant.now())));
            throw exception;
        }

        for (ConfigChange change : changes) {
            setCounter.increment();
            changeLog.add(change);
            auditLog.add(new ConfigAuditEntry(
                    change.key(),
                    change.oldValue(),
                    change.newValue(),
                    AuditStatus.APPLIED,
                    null,
                    change.changedAt()));
            log.info("Config override applied: key={} oldValue={} newValue={}", change.key(), change.oldValue(), change.newValue());
        }
    }

    private void validateOverride(String key, String value, Instant validatedAt) {
        Objects.requireNonNull(key, "key must not be null");
        if (key.isBlank()) {
            validationErrorCounter.increment();
            throw new IllegalArgumentException("key must not be blank");
        }
        if (value == null || value.isBlank()) {
            validationErrorCounter.increment();
            auditLog.add(new ConfigAuditEntry(key, get(key, null), value, AuditStatus.REJECTED, "value must not be blank", validatedAt));
            throw new IllegalArgumentException("value must not be blank for key: " + key);
        }

        try {
            if (isKnownIntegerKey(key)) {
                parseInteger(key, value);
            }
            switch (key) {
                case EnvConfig.REDIS_PORT, EnvConfig.RABBITMQ_PORT -> validatePort(key, value);
                case EnvConfig.AEP_DB_POOL_SIZE, "AEP_DB_POOL_SIZE" -> validateRange(key, value, 1, 200);
                case EnvConfig.AEP_CONSOLIDATION_INTERVAL_HOURS, "AEP_CONSOLIDATION_INTERVAL_HOURS" -> validateRange(key, value, 1, Integer.MAX_VALUE);
                case EnvConfig.KAFKA_BOOTSTRAP_SERVERS -> validateKafkaBootstrapServers(value);
                case EnvConfig.APP_ENV -> validateAppEnvironment(value);
                default -> {
                    // no extra semantic validation for other runtime keys yet
                }
            }
        } catch (IllegalArgumentException exception) {
            validationErrorCounter.increment();
            auditLog.add(new ConfigAuditEntry(key, get(key, null), value, AuditStatus.REJECTED, exception.getMessage(), validatedAt));
            throw exception;
        }
    }

    private void validatePort(String key, String value) {
        validateRange(key, value, 1, 65_535);
    }

    private void validateRange(String key, String value, int minInclusive, int maxInclusive) {
        int parsed = parseInteger(key, value);
        if (parsed < minInclusive || parsed > maxInclusive) {
            throw new IllegalArgumentException(
                    "Key '" + key + "' must be between " + minInclusive + " and " + maxInclusive + ", but got: " + value);
        }
    }

    private int parseInteger(String key, String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Key '" + key + "' expects an integer value, but got: " + value, exception);
        }
    }

    private void validateKafkaBootstrapServers(String value) {
        String[] brokers = value.split(",");
        if (brokers.length == 0) {
            throw new IllegalArgumentException("KAFKA_BOOTSTRAP_SERVERS must include at least one host:port entry");
        }
        for (String broker : brokers) {
            String trimmed = broker.trim();
            if (!AepConfigurationValidator.isValidHostPort(trimmed)) {
                throw new IllegalArgumentException(
                        "KAFKA_BOOTSTRAP_SERVERS contains invalid broker address '" + trimmed + "'; expected host:port format");
            }
        }
    }

    private void validateAppEnvironment(String value) {
        if (!"development".equalsIgnoreCase(value)
                && !"staging".equalsIgnoreCase(value)
                && !"production".equalsIgnoreCase(value)) {
            throw new IllegalArgumentException(
                    "APP_ENV must be one of development, staging, production but was: " + value);
        }
    }

    private void restoreOverlay(Map<String, String> previousOverlayValues) {
        previousOverlayValues.forEach((key, previousValue) -> {
            if (previousValue == null) {
                overlay.remove(key);
            } else {
                overlay.put(key, previousValue);
            }
        });
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

    /**
     * Immutable record of an applied, rejected, or rolled-back config write.
     */
    public record ConfigAuditEntry(
            String key,
            String oldValue,
            String newValue,
            AuditStatus status,
            String detail,
            Instant changedAt) {}

    /**
     * Outcome for a dynamic configuration write attempt.
     */
    public enum AuditStatus {
        APPLIED,
        REJECTED,
        ROLLED_BACK
    }
}
