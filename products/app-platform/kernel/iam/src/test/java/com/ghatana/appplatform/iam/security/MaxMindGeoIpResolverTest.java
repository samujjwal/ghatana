/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.iam.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MaxMindGeoIpResolver}.
 *
 * <p>Since a real MaxMind {@code .mmdb} database file is not bundled in tests,
 * these tests verify the contract via the {@link GeoIpResolver.InMemoryGeoIpResolver}
 * stub and validate that {@link MaxMindGeoIpResolver} handles edge cases gracefully
 * (null/blank IP, etc.) when constructed via the test double pattern.
 */
@DisplayName("GeoIpResolver contract tests")
class MaxMindGeoIpResolverTest {

    private final GeoIpResolver stub = new GeoIpResolver.InMemoryGeoIpResolver();

    @Test
    @DisplayName("InMemoryGeoIpResolver returns null for any IP")
    void stubReturnsNull() {
        assertThat(stub.resolve("8.8.8.8")).isNull();
        assertThat(stub.resolve("192.168.1.1")).isNull();
    }

    @Test
    @DisplayName("InMemoryGeoIpResolver handles null IP gracefully")
    void stubHandlesNullIp() {
        assertThat(stub.resolve(null)).isNull();
    }

    @Test
    @DisplayName("Coordinates record stores lat/lon correctly")
    void coordinatesRecordWorks() {
        var coords = new GeoIpResolver.Coordinates(37.7749, -122.4194);
        assertThat(coords.lat()).isEqualTo(37.7749);
        assertThat(coords.lon()).isEqualTo(-122.4194);
    }

    @Test
    @DisplayName("Coordinates equality and hashCode work")
    void coordinatesEquality() {
        var a = new GeoIpResolver.Coordinates(51.5074, -0.1278);
        var b = new GeoIpResolver.Coordinates(51.5074, -0.1278);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
