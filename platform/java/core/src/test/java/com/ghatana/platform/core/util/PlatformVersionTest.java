/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.platform.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Tests for build-time platform version metadata
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("PlatformVersion [GH-90000]")
class PlatformVersionTest {

    @Test
    @DisplayName("should load platform version from properties file [GH-90000]")
    void shouldLoadPlatformVersion() { // GH-90000
        PlatformVersion version = PlatformVersion.get(); // GH-90000
        assertThat(version.platformVersion()).isNotNull().isNotEmpty(); // GH-90000
    }

    @Test
    @DisplayName("should load SDK version from properties file [GH-90000]")
    void shouldLoadSdkVersion() { // GH-90000
        PlatformVersion version = PlatformVersion.get(); // GH-90000
        assertThat(version.sdkVersion()).isNotNull().isNotEmpty(); // GH-90000
    }

    @Test
    @DisplayName("should load instrumentation version from properties file [GH-90000]")
    void shouldLoadInstrumentationVersion() { // GH-90000
        PlatformVersion version = PlatformVersion.get(); // GH-90000
        assertThat(version.instrumentationVersion()).isNotNull().isNotEmpty(); // GH-90000
    }

    @Test
    @DisplayName("should return singleton instance [GH-90000]")
    void shouldReturnSingleton() { // GH-90000
        assertThat(PlatformVersion.get()).isSameAs(PlatformVersion.get()); // GH-90000
    }
}
