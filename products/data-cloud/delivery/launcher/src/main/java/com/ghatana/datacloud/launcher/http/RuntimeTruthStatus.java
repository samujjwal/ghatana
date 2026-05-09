/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

/**
 * Canonical Runtime Truth status taxonomy for Data Cloud surfaces and subsystems.
 *
 * <p>DC-BE-001: Centralized enum for status values to prevent drift and provide
 * compile-time type safety. All Runtime Truth responses must use these status values.
 *
 * <h2>Status values</h2>
 * <ul>
 *   <li>{@code LIVE} — Surface/subsystem is fully operational and healthy</li>
 *   <li>{@code DEGRADED} — Surface/subsystem is operational but with limitations</li>
 *   <li>{@code DISABLED} — Surface/subsystem is explicitly disabled (not due to health)</li>
 *   <li>{@code PREVIEW} — Surface/subsystem is in preview/early access mode</li>
 *   <li>{@code UNAVAILABLE} — Surface/subsystem is not configured or health check failed</li>
 *   <li>{@code MISCONFIGURED} — Surface/subsystem is configured but has invalid settings</li>
 * </ul>
 *
 * <h2>Mapping to legacy contract values</h2>
 * <ul>
 *   <li>{@code LIVE} → legacy {@code ACTIVE}</li>
 *   <li>{@code DEGRADED} → legacy {@code DEGRADED}</li>
 *   <li>{@code DISABLED} → legacy {@code NOT_CONFIGURED}</li>
 *   <li>{@code UNAVAILABLE} → legacy {@code NOT_CONFIGURED}</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * RuntimeTruthStatus status = RuntimeTruthStatus.LIVE;
 * String jsonValue = status.toJsonValue(); // "live"
 * RuntimeTruthStatus fromJson = RuntimeTruthStatus.fromJsonValue("live");
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Centralized Runtime Truth status taxonomy
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum RuntimeTruthStatus {

    /**
     * Surface/subsystem is fully operational and healthy.
     * Maps to legacy contract value {@code ACTIVE}.
     */
    LIVE("live", "ACTIVE"),

    /**
     * Surface/subsystem is operational but with limitations.
     * Maps to legacy contract value {@code DEGRADED}.
     */
    DEGRADED("degraded", "DEGRADED"),

    /**
     * Surface/subsystem is explicitly disabled (not due to health).
     * Maps to legacy contract value {@code NOT_CONFIGURED}.
     */
    DISABLED("disabled", "NOT_CONFIGURED"),

    /**
     * Surface/subsystem is in preview/early access mode.
     * Maps to legacy contract value {@code ACTIVE} (with maturity=preview).
     */
    PREVIEW("preview", "ACTIVE"),

    /**
     * Surface/subsystem is not configured or health check failed.
     * Maps to legacy contract value {@code NOT_CONFIGURED}.
     */
    UNAVAILABLE("unavailable", "NOT_CONFIGURED"),

    /**
     * Surface/subsystem is configured but has invalid settings.
     * Maps to legacy contract value {@code DEGRADED}.
     */
    MISCONFIGURED("misconfigured", "DEGRADED");

    private final String jsonValue;
    private final String legacyValue;

    RuntimeTruthStatus(String jsonValue, String legacyValue) {
        this.jsonValue = jsonValue;
        this.legacyValue = legacyValue;
    }

    /**
     * Returns the JSON value used in Runtime Truth responses.
     *
     * @return lowercase string value (e.g., "live", "degraded")
     */
    public String toJsonValue() {
        return jsonValue;
    }

    /**
     * Returns the legacy contract value for backward compatibility.
     *
     * @return uppercase legacy value (e.g., "ACTIVE", "DEGRADED")
     */
    public String toLegacyValue() {
        return legacyValue;
    }

    /**
     * Parses a JSON value string to a RuntimeTruthStatus enum.
     *
     * @param value the JSON value string (case-insensitive)
     * @return corresponding RuntimeTruthStatus
     * @throws IllegalArgumentException if value is not recognized
     */
    public static RuntimeTruthStatus fromJsonValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("RuntimeTruthStatus value cannot be null");
        }
        for (RuntimeTruthStatus status : values()) {
            if (status.jsonValue.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown RuntimeTruthStatus value: " + value);
    }

    /**
     * Parses a legacy contract value string to a RuntimeTruthStatus enum.
     *
     * @param value the legacy value string (case-insensitive)
     * @return corresponding RuntimeTruthStatus
     * @throws IllegalArgumentException if value is not recognized
     */
    public static RuntimeTruthStatus fromLegacyValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("RuntimeTruthStatus legacy value cannot be null");
        }
        for (RuntimeTruthStatus status : values()) {
            if (status.legacyValue.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown RuntimeTruthStatus legacy value: " + value);
    }

    /**
     * Determines the appropriate RuntimeTruthStatus based on configuration and subsystem health.
     *
     * <p>This is the canonical mapping logic for converting runtime state to status taxonomy.
     *
     * @param configured whether the surface/subsystem is configured
     * @param subsystemStatus health check status string (e.g., "UP", "DOWN", "DEGRADED")
     * @return appropriate RuntimeTruthStatus
     */
    public static RuntimeTruthStatus fromRuntimeState(boolean configured, String subsystemStatus) {
        if (!configured) {
            return DISABLED;
        }
        if (subsystemStatus == null || subsystemStatus.isBlank() || "UP".equalsIgnoreCase(subsystemStatus)) {
            return LIVE;
        }
        if ("DEGRADED".equalsIgnoreCase(subsystemStatus)) {
            return DEGRADED;
        }
        if ("DOWN".equalsIgnoreCase(subsystemStatus)) {
            return UNAVAILABLE;
        }
        if ("NOT_CONFIGURED".equalsIgnoreCase(subsystemStatus)) {
            return UNAVAILABLE;
        }
        // Default to LIVE for unknown health states to avoid false negatives
        return LIVE;
    }
}
