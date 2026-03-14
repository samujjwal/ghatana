/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.iam.security;

/**
 * Port for IP-to-geolocation resolution used by {@link LoginAnomalyDetector}.
 *
 * <p>The default stub ({@link InMemoryGeoIpResolver}) always returns {@code null};
 * production deployments should bind a real MaxMind or ipinfo.io adapter.
 *
 * @doc.type interface
 * @doc.purpose Port for IP-to-latitude/longitude geolocation resolution (K01-023)
 * @doc.layer product
 * @doc.pattern Port
 */
public interface GeoIpResolver {

    /**
     * Resolves an IPv4/IPv6 address to approximate geographic coordinates.
     *
     * @param ipAddress source IP (never null, never blank)
     * @return coordinates, or {@code null} if resolution is unavailable
     */
    Coordinates resolve(String ipAddress);

    // ─── Value type ───────────────────────────────────────────────────────────

    /**
     * Decimal latitude/longitude pair (WGS-84).
     *
     * @param lat latitude  (-90  to 90)
     * @param lon longitude (-180 to 180)
     */
    record Coordinates(double lat, double lon) {}

    // ─── Stub implementation ──────────────────────────────────────────────────

    /**
     * No-op resolver — always returns {@code null}.
     * Suitable for tests and deployments where GeoIP is not configured.
     *
     * @doc.type class
     * @doc.purpose No-op GeoIP resolver stub for tests / unconfigured environments
     * @doc.layer product
     * @doc.pattern NullObject
     */
    final class InMemoryGeoIpResolver implements GeoIpResolver {
        @Override
        public Coordinates resolve(String ipAddress) {
            return null;
        }
    }
}
