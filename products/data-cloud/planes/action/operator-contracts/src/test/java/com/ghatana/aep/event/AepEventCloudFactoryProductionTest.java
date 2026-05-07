/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.event;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * AEP-P1-003: AEP EventCloud factory production fail-closed tests
 *
 * Tests invoke the real {@link AepEventCloudFactory#createDefault(Map)} to verify
 * fail-closed behavior. In the test classpath there is no durable EventCloud SPI
 * provider, so ServiceLoader always returns empty — the factory logic is the SUT.
 *
 * @doc.type class
 * @doc.purpose Production tests for EventCloud factory fail-closed behavior
 * @doc.layer product
 * @doc.pattern Test
 */
class AepEventCloudFactoryProductionTest {

    @Test
    void createDefaultThrowsInProductionProfileWhenNoProvider() {
        // No EventCloud SPI provider on test classpath → production profile → must throw
        Map<String, String> productionEnv = Map.of("AEP_PROFILE", "production");

        IllegalStateException ex = assertThrows(
            IllegalStateException.class,
            () -> AepEventCloudFactory.createDefault(productionEnv)
        );

        assertThat(ex.getMessage())
            .contains("No durable EventCloud SPI provider")
            .contains("AEP_DEV_MODE=true");
    }

    @Test
    void createDefaultThrowsWhenLegacyAepEnvIsProduction() {
        // Legacy AEP_ENV=production should also fail closed
        Map<String, String> productionEnv = Map.of("AEP_ENV", "production");

        assertThrows(
            IllegalStateException.class,
            () -> AepEventCloudFactory.createDefault(productionEnv)
        );
    }

    @Test
    void createDefaultThrowsWhenNoEnvVarsSet() {
        // Default (no env vars) must fail closed — production-safe default
        Map<String, String> emptyEnv = Map.of();

        assertThrows(
            IllegalStateException.class,
            () -> AepEventCloudFactory.createDefault(emptyEnv)
        );
    }

    @Test
    void devModeTrueAllowsInMemoryFallback() {
        // AEP_DEV_MODE=true must allow in-memory fallback with no exception
        Map<String, String> devEnv = Map.of("AEP_DEV_MODE", "true");

        EventCloud result = assertDoesNotThrow(
            () -> AepEventCloudFactory.createDefault(devEnv)
        );

        assertThat(result).isInstanceOf(InMemoryEventCloud.class);
    }

    @Test
    void devModeTrueOverridesProductionProfile() {
        // AEP_DEV_MODE=true must override AEP_PROFILE=production
        Map<String, String> devEnv = Map.of(
            "AEP_PROFILE", "production",
            "AEP_DEV_MODE", "true"
        );

        EventCloud result = assertDoesNotThrow(
            () -> AepEventCloudFactory.createDefault(devEnv)
        );

        assertThat(result).isInstanceOf(InMemoryEventCloud.class);
    }

    @Test
    void stagingProfileAllowsInMemoryFallbackWithoutException() {
        // Non-production, non-dev-mode: must warn and fall back without throwing
        Map<String, String> stagingEnv = Map.of("AEP_PROFILE", "staging");

        EventCloud result = assertDoesNotThrow(
            () -> AepEventCloudFactory.createDefault(stagingEnv)
        );

        assertThat(result).isInstanceOf(InMemoryEventCloud.class);
    }
}
