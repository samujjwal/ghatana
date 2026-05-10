package com.ghatana.datacloud.launcher.http;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable result of probing a single dependency for a {@link SurfaceRecord}.
 *
 * <p>DC-P1-5: Every {@link RuntimeTruthStatus#LIVE LIVE} surface must have at least
 * one {@code DependencyProbeResult} to prevent the runtime truth registry from
 * reporting surfaces as operational without any verified evidence.
 *
 * @param dependencyName  logical name of the dependency (e.g. {@code audit-service})
 * @param passed          whether the probe succeeded
 * @param status          canonical status string (e.g. {@code UP}, {@code DOWN}, {@code DEGRADED})
 * @param reason          human-readable explanation, particularly when {@code passed=false}
 * @param probedAt        when this probe result was collected
 *
 * @doc.type class
 * @doc.purpose Typed probe result for a single surface dependency
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record DependencyProbeResult(
    String dependencyName,
    boolean passed,
    String status,
    String reason,
    Instant probedAt
) {

    /** Validation compact constructor. */
    public DependencyProbeResult {
        Objects.requireNonNull(dependencyName, "dependencyName must not be null");
        if (dependencyName.isBlank()) {
            throw new IllegalArgumentException("dependencyName must not be blank");
        }
        Objects.requireNonNull(status, "status must not be null");
        probedAt = probedAt == null ? Instant.now() : probedAt;
    }

    /**
     * Creates a passing probe result indicating the dependency is healthy.
     *
     * @param dependencyName logical dependency name
     * @return a passing probe result
     */
    public static DependencyProbeResult pass(String dependencyName) {
        return new DependencyProbeResult(dependencyName, true, "UP", "Dependency is configured and healthy", Instant.now());
    }

    /**
     * Creates a passing probe result with a custom status detail.
     *
     * @param dependencyName logical dependency name
     * @param detail         implementation detail (e.g. mode, version)
     * @return a passing probe result
     */
    public static DependencyProbeResult pass(String dependencyName, String detail) {
        return new DependencyProbeResult(dependencyName, true, "UP", detail, Instant.now());
    }

    /**
     * Creates a failing probe result indicating the dependency is absent or misconfigured.
     *
     * @param dependencyName logical dependency name
     * @param reason         why the probe failed
     * @return a failing probe result
     */
    public static DependencyProbeResult fail(String dependencyName, String reason) {
        return new DependencyProbeResult(dependencyName, false, "NOT_CONFIGURED", reason, Instant.now());
    }

    /**
     * Creates a degraded probe result indicating the dependency is present but unhealthy.
     *
     * @param dependencyName logical dependency name
     * @param reason         what is degraded
     * @return a degraded probe result
     */
    public static DependencyProbeResult degraded(String dependencyName, String reason) {
        return new DependencyProbeResult(dependencyName, false, "DEGRADED", reason, Instant.now());
    }

    /**
     * Serialises this probe result to a plain {@link Map} suitable for JSON serialisation.
     *
     * @return a map representation
     */
    public Map<String, Object> toMap() {
        return Map.of(
            "dependencyName", dependencyName,
            "passed", passed,
            "status", status,
            "reason", reason != null ? reason : "",
            "probedAt", probedAt.toString()
        );
    }
}
