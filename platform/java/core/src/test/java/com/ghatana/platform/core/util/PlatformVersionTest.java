/*
 * Copyright (c) 2026 Ghatana Inc.
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
@DisplayName("PlatformVersion")
class PlatformVersionTest {

    @Test
    @DisplayName("should load platform version from properties file")
    void shouldLoadPlatformVersion() {
        PlatformVersion version = PlatformVersion.get();
        assertThat(version.platformVersion()).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("should load SDK version from properties file")
    void shouldLoadSdkVersion() {
        PlatformVersion version = PlatformVersion.get();
        assertThat(version.sdkVersion()).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("should load instrumentation version from properties file")
    void shouldLoadInstrumentationVersion() {
        PlatformVersion version = PlatformVersion.get();
        assertThat(version.instrumentationVersion()).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("should return singleton instance")
    void shouldReturnSingleton() {
        assertThat(PlatformVersion.get()).isSameAs(PlatformVersion.get());
    }
}
