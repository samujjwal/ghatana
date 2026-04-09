/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.version;

import com.ghatana.aep.AepEngine;
import com.ghatana.aep.error.AepVersionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Event schema version compatibility checking and migration utilities (AEP-005).
 *
 * <p>AEP supports evolving event schemas via a semantic version string in the event
 * {@code version} field (e.g. {@code "1.0"}, {@code "2.1"}). This class provides:
 * <ul>
 *   <li><b>Compatibility checking</b> — rejects events whose version falls outside the
 *       engine's supported range, surfacing clear diagnostics.</li>
 *   <li><b>Migration</b> — transforms events from an older schema version into the
 *       current canonical form before processing. Migrations are registered per
 *       ({@code eventType}, {@code fromVersion}) pair and run automatically.</li>
 *   <li><b>Passthrough</b> — events already at the current version pass through unmodified.</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * EventVersionCompatibility compat = EventVersionCompatibility.builder()
 *     .currentVersion("2.0")
 *     .minSupportedVersion("1.0")
 *     .registerMigration("user.login", "1.0", event -> {
 *         // transform v1.0 user.login events to v2.0
 *         Map<String, Object> newPayload = new HashMap<>(event.payload());
 *         newPayload.put("loginMethod", newPayload.remove("method"));
 *         return event.withVersion("2.0");
 *     })
 *     .build();
 *
 * AepEngine.Event migrated = compat.migrate(tenantId, event);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Event schema version compatibility checking and migration for AEP-005
 * @doc.layer product
 * @doc.pattern Strategy
 * @since 1.2.0
 */
public final class EventVersionCompatibility {

    private static final Logger log = LoggerFactory.getLogger(EventVersionCompatibility.class);

    /** Current engine-supported event schema version. */
    public static final String DEFAULT_CURRENT_VERSION = "1.0";

    /** Minimum version this engine will accept without a migration path. */
    public static final String DEFAULT_MIN_VERSION = "1.0";

    private static final Pattern VERSION_PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)$");

    private final String currentVersion;
    private final int currentMajor;
    private final int currentMinor;
    private final int minMajor;
    private final int minMinor;

    /**
     * Migration functions keyed by {@code eventType + ":" + fromVersion}.
     * Value is a function that transforms an older event to the current version.
     */
    private final Map<String, Function<AepEngine.Event, AepEngine.Event>> migrations;

    private EventVersionCompatibility(Builder builder) {
        this.currentVersion = builder.currentVersion;
        int[] current = parseVersion(builder.currentVersion);
        int[] min = parseVersion(builder.minVersion);
        this.currentMajor = current[0];
        this.currentMinor = current[1];
        this.minMajor     = min[0];
        this.minMinor     = min[1];
        this.migrations   = Map.copyOf(builder.migrations);
    }

    /**
     * Checks that the event version is within the supported range, then applies any
     * registered migration to bring it up to the current version.
     *
     * @param tenantId context for error reporting; must not be {@code null}
     * @param event    the inbound event; must not be {@code null}
     * @return the event at {@link #DEFAULT_CURRENT_VERSION}, either unchanged or migrated
     * @throws AepVersionException if the event version is outside the supported range and
     *                             no migration is registered for it
     */
    public AepEngine.Event migrate(String tenantId, AepEngine.Event event) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(event, "event must not be null");

        String versionStr = event.version();
        int[] v;
        try {
            v = parseVersion(versionStr);
        } catch (IllegalArgumentException e) {
            throw new AepVersionException(tenantId, event.type(),
                -1, minMajor * 100 + minMinor, currentMajor * 100 + currentMinor);
        }

        int major = v[0];
        int minor = v[1];

        // Already at current version — pass through
        if (major == currentMajor && minor == currentMinor) {
            return event;
        }

        // Below minimum supported — reject
        if (major < minMajor || (major == minMajor && minor < minMinor)) {
            throw new AepVersionException(tenantId, event.type(), major * 100 + minor,
                minMajor * 100 + minMinor, currentMajor * 100 + currentMinor);
        }

        // Above current — pass through (forward-compatible) with a warning
        if (major > currentMajor || (major == currentMajor && minor > currentMinor)) {
            log.warn("Event type='{}' version='{}' is newer than current engine version '{}'; "
                + "processing as-is (forward-compatible). tenantId={}",
                event.type(), versionStr, currentVersion, tenantId);
            return event;
        }

        // Attempt migration
        String key = migrationKey(event.type(), versionStr);
        Function<AepEngine.Event, AepEngine.Event> migrator = migrations.get(key);
        if (migrator != null) {
            log.debug("Migrating event type='{}' from version='{}' to '{}' tenantId={}",
                event.type(), versionStr, currentVersion, tenantId);
            AepEngine.Event migrated = migrator.apply(event);
            return migrated.withVersion(currentVersion);
        }

        // No migration registered — try wildcard (type-independent)
        String wildcardKey = migrationKey("*", versionStr);
        Function<AepEngine.Event, AepEngine.Event> wildcardMigrator = migrations.get(wildcardKey);
        if (wildcardMigrator != null) {
            log.debug("Applying wildcard migration for version='{}' to event type='{}' tenantId={}",
                versionStr, event.type(), tenantId);
            AepEngine.Event migrated = wildcardMigrator.apply(event);
            return migrated.withVersion(currentVersion);
        }

        // Within supported range but no migration registered — accept with warning
        log.warn("No migration registered for event type='{}' from version='{}'; accepting as-is. tenantId={}",
            event.type(), versionStr, tenantId);
        return event;
    }

    /**
     * Returns whether the given version string is within the supported range.
     *
     * @param versionStr version string (e.g. {@code "1.0"})
     * @return {@code true} if accepted without a migration
     */
    public boolean isSupported(String versionStr) {
        if (versionStr == null) return false;
        try {
            int[] v = parseVersion(versionStr);
            if (v[0] < minMajor || (v[0] == minMajor && v[1] < minMinor)) return false;
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Returns the current version this instance targets.
     *
     * @return current version string (e.g. {@code "1.0"})
     */
    public String currentVersion() {
        return currentVersion;
    }

    // ── Static helpers ────────────────────────────────────────────────────────

    private static String migrationKey(String eventType, String fromVersion) {
        return eventType + ":" + fromVersion;
    }

    private static int[] parseVersion(String version) {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("Blank or null version string");
        }
        var m = VERSION_PATTERN.matcher(version.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid version format: '" + version
                + "'; expected 'major.minor' (e.g. '1.0' or '2.3')");
        }
        return new int[]{ Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)) };
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    /**
     * @return a new builder for {@link EventVersionCompatibility}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a default instance that accepts only the current default version.
     *
     * @return passthrough-only compatibility instance
     */
    public static EventVersionCompatibility defaults() {
        return builder().build();
    }

    /**
     * Builder for {@link EventVersionCompatibility}.
     *
     * @doc.type class
     * @doc.purpose Fluent builder for EventVersionCompatibility
     * @doc.layer product
     * @doc.pattern Builder
     */
    public static final class Builder {

        private String currentVersion = DEFAULT_CURRENT_VERSION;
        private String minVersion     = DEFAULT_MIN_VERSION;
        private final Map<String, Function<AepEngine.Event, AepEngine.Event>> migrations = new HashMap<>();

        private Builder() {}

        /**
         * Sets the current target version (events are migrated to this version).
         *
         * @param version version string, e.g. {@code "2.0"}; must not be blank
         */
        public Builder currentVersion(String version) {
            this.currentVersion = Objects.requireNonNull(version, "version must not be null");
            return this;
        }

        /**
         * Sets the minimum version this engine will accept.
         *
         * @param version version string, e.g. {@code "1.5"}; must not be blank
         */
        public Builder minSupportedVersion(String version) {
            this.minVersion = Objects.requireNonNull(version, "version must not be null");
            return this;
        }

        /**
         * Registers a migration function for a specific ({@code eventType}, {@code fromVersion}) pair.
         *
         * <p>Use {@code "*"} as {@code eventType} to register a wildcard migration that applies
         * to any event type at that version when no type-specific migration is registered.
         *
         * @param eventType   event type to migrate (e.g. {@code "user.login"} or {@code "*"})
         * @param fromVersion version to migrate from (e.g. {@code "1.0"})
         * @param migrator    function receiving the old event and returning the new event payload;
         *                    the {@code version} field will be updated automatically after migration
         */
        public Builder registerMigration(String eventType, String fromVersion,
                                         Function<AepEngine.Event, AepEngine.Event> migrator) {
            Objects.requireNonNull(eventType, "eventType must not be null");
            Objects.requireNonNull(fromVersion, "fromVersion must not be null");
            Objects.requireNonNull(migrator, "migrator must not be null");
            migrations.put(migrationKey(eventType, fromVersion), migrator);
            return this;
        }

        /**
         * Builds the {@link EventVersionCompatibility} instance.
         *
         * @return new immutable instance
         * @throws IllegalArgumentException if any version string is malformed
         */
        public EventVersionCompatibility build() {
            // Validate version strings early
            parseVersion(currentVersion);
            parseVersion(minVersion);
            return new EventVersionCompatibility(this);
        }
    }
}
