package com.ghatana.aep.versioning;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * AEP API version registry and negotiation utility (AEP-010).
 *
 * <p>Tracks the lifecycle of API versions (current, supported, deprecated, sunset)
 * and helps clients negotiate the appropriate version to use.
 *
 * <h3>Version lifecycle</h3>
 * <pre>
 *   CURRENT → DEPRECATED (with sunset date) → SUNSET (after sunset date)
 * </pre>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * AepApiVersionRegistry registry = AepApiVersionRegistry.builder()
 *     .register(ApiVersion.current("v3"))
 *     .register(ApiVersion.deprecated("v2", LocalDate.of(2026, 6, 1)))
 *     .register(ApiVersion.sunset("v1"))
 *     .build();
 *
 * // Client negotiation
 * Optional<String> best = registry.negotiate("v2");
 * best.ifPresent(v -> System.out.println("Using: " + v));
 *
 * // Deprecation check
 * boolean isDeprecated = registry.isDeprecated("v2");
 * Optional<LocalDate> sunsetDate = registry.sunsetDate("v2");
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose AEP API version lifecycle tracking and client negotiation
 * @doc.layer product
 * @doc.pattern Registry
 */
public final class AepApiVersionRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(AepApiVersionRegistry.class);

    private final Map<String, ApiVersion> versions;
    private final String currentVersion;

    private AepApiVersionRegistry(Map<String, ApiVersion> versions) {
        this.versions = Map.copyOf(versions);
        this.currentVersion = versions.values().stream()
                .filter(v -> v.status() == ApiVersion.Status.CURRENT)
                .map(ApiVersion::versionId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Registry must have exactly one CURRENT version"));
    }

    /**
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    // ─── query API ────────────────────────────────────────────────────────────

    /**
     * @return the current (latest recommended) version ID
     */
    public String currentVersion() {
        return currentVersion;
    }

    /**
     * Determines the best available version for a client requesting the given version.
     *
     * <ul>
     *   <li>If the requested version is CURRENT or DEPRECATED (not yet sunset), it is returned.</li>
     *   <li>If the requested version is SUNSET or unknown, the current version is returned.</li>
     * </ul>
     *
     * @param requestedVersion the version the client wants to use
     * @return the negotiated version ID, or empty if the registry has no CURRENT version
     * @throws NullPointerException if requestedVersion is null
     */
    public Optional<String> negotiate(String requestedVersion) {
        Objects.requireNonNull(requestedVersion, "requestedVersion must not be null");
        ApiVersion v = versions.get(requestedVersion);
        if (v == null || v.status() == ApiVersion.Status.SUNSET) {
            LOG.warn("Version {} is unknown or sunset; redirecting to current version {}", requestedVersion, currentVersion);
            return Optional.of(currentVersion);
        }
        if (v.status() == ApiVersion.Status.DEPRECATED) {
            LOG.warn("Version {} is deprecated and will be sunset on {}; consider upgrading to {}",
                    requestedVersion, v.sunsetDate().orElse(null), currentVersion);
        }
        return Optional.of(requestedVersion);
    }

    /**
     * @param versionId the version to check
     * @return true if this version is registered as DEPRECATED
     * @throws NullPointerException if versionId is null
     */
    public boolean isDeprecated(String versionId) {
        Objects.requireNonNull(versionId, "versionId must not be null");
        ApiVersion v = versions.get(versionId);
        return v != null && v.status() == ApiVersion.Status.DEPRECATED;
    }

    /**
     * @param versionId the version to check
     * @return true if this version is registered as SUNSET (no longer served)
     * @throws NullPointerException if versionId is null
     */
    public boolean isSunset(String versionId) {
        Objects.requireNonNull(versionId, "versionId must not be null");
        ApiVersion v = versions.get(versionId);
        return v != null && v.status() == ApiVersion.Status.SUNSET;
    }

    /**
     * @param versionId the version to look up
     * @return the sunset date if this version is deprecated, or empty otherwise
     * @throws NullPointerException if versionId is null
     */
    public Optional<LocalDate> sunsetDate(String versionId) {
        Objects.requireNonNull(versionId, "versionId must not be null");
        ApiVersion v = versions.get(versionId);
        return v != null ? v.sunsetDate() : Optional.empty();
    }

    /**
     * @return an unmodifiable view of all registered version IDs
     */
    public Set<String> registeredVersions() {
        return versions.keySet();
    }

    // ─── Builder ─────────────────────────────────────────────────────────────

    /** Builder for {@link AepApiVersionRegistry}. */
    public static final class Builder {
        private final Map<String, ApiVersion> versions = new LinkedHashMap<>();

        private Builder() {}

        /**
         * Registers an API version.
         *
         * @param version the version to register
         * @return this builder
         * @throws NullPointerException if version is null
         */
        public Builder register(ApiVersion version) {
            Objects.requireNonNull(version, "version must not be null");
            versions.put(version.versionId(), version);
            return this;
        }

        /**
         * Builds the registry.
         *
         * @return a new {@link AepApiVersionRegistry}
         * @throws IllegalStateException if no CURRENT version was registered
         */
        public AepApiVersionRegistry build() {
            return new AepApiVersionRegistry(versions);
        }
    }

    // ─── ApiVersion ───────────────────────────────────────────────────────────

    /**
     * An immutable API version descriptor.
     *
     * @param versionId   the version string (e.g. {@code "v3"})
     * @param status      lifecycle status
     * @param sunsetDate  the planned sunset date (only set for DEPRECATED versions)
     * @param description a human-readable description
     */
    public record ApiVersion(
            String versionId,
            Status status,
            Optional<LocalDate> sunsetDate,
            String description
    ) {
        /** API version lifecycle status. */
        public enum Status { CURRENT, DEPRECATED, SUNSET }

        /**
         * Creates a CURRENT (actively recommended) version.
         *
         * @param versionId the version string
         * @return a CURRENT ApiVersion
         */
        public static ApiVersion current(String versionId) {
            Objects.requireNonNull(versionId, "versionId must not be null");
            return new ApiVersion(versionId, Status.CURRENT, Optional.empty(),
                    "Current production version");
        }

        /**
         * Creates a DEPRECATED version with a known sunset date.
         *
         * @param versionId  the version string
         * @param sunsetDate the date after which this version will no longer be served
         * @return a DEPRECATED ApiVersion
         */
        public static ApiVersion deprecated(String versionId, LocalDate sunsetDate) {
            Objects.requireNonNull(versionId, "versionId must not be null");
            Objects.requireNonNull(sunsetDate, "sunsetDate must not be null");
            return new ApiVersion(versionId, Status.DEPRECATED, Optional.of(sunsetDate),
                    "Deprecated — will be sunset on " + sunsetDate);
        }

        /**
         * Creates a SUNSET version that is no longer served.
         *
         * @param versionId the version string
         * @return a SUNSET ApiVersion
         */
        public static ApiVersion sunset(String versionId) {
            Objects.requireNonNull(versionId, "versionId must not be null");
            return new ApiVersion(versionId, Status.SUNSET, Optional.empty(),
                    "Sunset — no longer served");
        }
    }
}
