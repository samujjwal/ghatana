package com.ghatana.appplatform.resilience.timeout;

import java.time.Duration;
import java.util.Objects;

/**
 * Per-route timeout configuration that integrates with the K-02 config engine.
 *
 * <p>Each route can have its own baseline timeout. A caller can request a longer
 * timeout via the {@value TimeoutBudgetPropagator#HEADER_NAME} header, but the
 * effective timeout is always capped at {@link #maxOverride} to prevent abuse.
 *
 * <h2>Scenario examples</h2>
 * <ul>
 *   <li>Risk-check route: baseline 5 ms, max override 10 ms</li>
 *   <li>Reporting route: baseline 30 s, max override 120 s</li>
 *   <li>Default: 5 s baseline, 30 s max override</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * RouteTimeoutConfig cfg = RouteTimeoutConfig.of("/risk/check", Duration.ofMillis(5),
 *     Duration.ofMillis(10));
 *
 * Duration effective = cfg.resolveTimeout("50");  // Request asked for 50ms → capped to 10ms
 * }</pre>
 *
 * @param routePattern  route or endpoint pattern this config applies to (glob / prefix)
 * @param baselineTimeout  default timeout for this route when no override is requested
 * @param maxOverride   maximum timeout that a caller may override up to
 *
 * @doc.type record
 * @doc.purpose Per-route timeout configuration with caller-override cap (K18-009)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record RouteTimeoutConfig(
    String   routePattern,
    Duration baselineTimeout,
    Duration maxOverride
) {

    // ── Defaults ───────────────────────────────────────────────────────────────

    /** Baseline applied when no route-specific config is found. */
    public static final Duration DEFAULT_BASELINE = Duration.ofSeconds(5);
    /** Maximum override accepted from callers by default. */
    public static final Duration DEFAULT_MAX_OVERRIDE = Duration.ofSeconds(30);

    // ── Compact constructor ─────────────────────────────────────────────────────

    public RouteTimeoutConfig {
        Objects.requireNonNull(routePattern, "routePattern");
        Objects.requireNonNull(baselineTimeout, "baselineTimeout");
        Objects.requireNonNull(maxOverride, "maxOverride");
        if (routePattern.isBlank()) throw new IllegalArgumentException("routePattern must not be blank");
        if (baselineTimeout.isNegative() || baselineTimeout.isZero())
            throw new IllegalArgumentException("baselineTimeout must be positive");
        if (maxOverride.isNegative() || maxOverride.isZero())
            throw new IllegalArgumentException("maxOverride must be positive");
        if (maxOverride.compareTo(baselineTimeout) < 0)
            throw new IllegalArgumentException("maxOverride must be >= baselineTimeout");
    }

    // ── Factory helpers ─────────────────────────────────────────────────────────

    /** Creates a config with {@link #DEFAULT_BASELINE} and {@link #DEFAULT_MAX_OVERRIDE}. */
    public static RouteTimeoutConfig defaultFor(String routePattern) {
        return new RouteTimeoutConfig(routePattern, DEFAULT_BASELINE, DEFAULT_MAX_OVERRIDE);
    }

    /** Creates a route config with an explicit baseline and max override. */
    public static RouteTimeoutConfig of(String routePattern, Duration baseline, Duration max) {
        return new RouteTimeoutConfig(routePattern, baseline, max);
    }

    // ── Timeout resolution ──────────────────────────────────────────────────────

    /**
     * Resolves the effective timeout for an incoming request.
     *
     * <ul>
     *   <li>If {@code requestedOverrideMs} is null / blank / not a valid long, return
     *       {@link #baselineTimeout}.</li>
     *   <li>If the requested override is within the cap ({@code <= maxOverride}),
     *       honour it.</li>
     *   <li>Otherwise, cap at {@link #maxOverride} and return that.</li>
     * </ul>
     *
     * @param requestedOverrideMs caller-supplied timeout request in milliseconds as string
     *                            (from a query param or header); may be null
     * @return the effective timeout to apply for this request
     */
    public Duration resolveTimeout(String requestedOverrideMs) {
        if (requestedOverrideMs == null || requestedOverrideMs.isBlank()) {
            return baselineTimeout;
        }
        long requested;
        try {
            requested = Long.parseLong(requestedOverrideMs.trim());
        } catch (NumberFormatException e) {
            return baselineTimeout;
        }
        if (requested <= 0) {
            return baselineTimeout;
        }
        Duration requestedDuration = Duration.ofMillis(requested);
        if (requestedDuration.compareTo(maxOverride) <= 0) {
            return requestedDuration;
        }
        return maxOverride;
    }

    /**
     * Resolves the effective timeout using a {@link Duration} directly.
     * Useful when the caller already parsed the value.
     */
    public Duration resolveTimeout(Duration requested) {
        if (requested == null || requested.isNegative() || requested.isZero()) {
            return baselineTimeout;
        }
        if (requested.compareTo(maxOverride) <= 0) {
            return requested;
        }
        return maxOverride;
    }

    /**
     * Returns a Micrometer-compatible route tag value derived from the route pattern.
     * Used in metrics labels: {@code timeout_triggered_total{route="..."}} .
     */
    public String metricTagValue() {
        // Normalise to lowercase, replace non-alphanumeric with underscores
        return routePattern.toLowerCase()
            .replaceAll("[^a-z0-9/]", "_")
            .replaceAll("/+", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_|_$", "");
    }
}
