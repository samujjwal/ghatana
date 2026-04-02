/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.services.aiinference;

import com.ghatana.core.state.HybridStateStore;
import com.ghatana.platform.security.ratelimit.RateLimiter;
import com.ghatana.platform.security.port.JwtTokenProvider;
import io.activej.eventloop.Eventloop;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies failure paths and defensive initialization in AIInferenceServiceLauncher.
 *
 * <p>These tests call the {@code @Provides} factory methods directly on a launcher
 * instance to verify startup guards without launching the full ActiveJ service graph.</p>
 *
 * @doc.type class
 * @doc.purpose Tests for AIInferenceServiceLauncher startup failure paths and defaults
 * @doc.layer application
 * @doc.pattern Test
 */
@DisplayName("AIInferenceServiceLauncher — startup failure paths and defensive defaults")
class AIInferenceServiceFailureTest {

    // ── meterRegistry() ───────────────────────────────────────────────────────

    @Test
    @DisplayName("meterRegistry() returns a non-null SimpleMeterRegistry")
    void meterRegistryReturnsNonNull() {
        AIInferenceServiceLauncher launcher = new AIInferenceServiceLauncher();
        MeterRegistry registry = launcher.meterRegistry();
        assertThat(registry).isNotNull().isInstanceOf(SimpleMeterRegistry.class);
    }

    // ── eventloop() ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("eventloop() returns a non-null Eventloop")
    void eventloopReturnsNonNull() {
        AIInferenceServiceLauncher launcher = new AIInferenceServiceLauncher();
        Eventloop eventloop = launcher.eventloop();
        assertThat(eventloop).isNotNull();
    }

    // ── llmConfig() — requires OPENAI_API_KEY ─────────────────────────────────

    @Test
    @DisplayName("llmConfig() throws IllegalStateException when OPENAI_API_KEY is not set")
    void llmConfigThrowsWhenApiKeyMissing() {
        // This test relies on OPENAI_API_KEY not being set in the test environment.
        // If the env var is present, the method would succeed instead (integration scenario).
        if (System.getenv("OPENAI_API_KEY") != null) {
            // Skip: OPENAI_API_KEY is set in this environment — failure path not exercisable
            return;
        }

        AIInferenceServiceLauncher launcher = new AIInferenceServiceLauncher();

        assertThatThrownBy(launcher::llmConfig)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OPENAI_API_KEY");
    }

    // ── jwtTokenProvider() — falls back to dev secret when PLATFORM_JWT_SECRET missing ──

    @Test
    @DisplayName("jwtTokenProvider() returns a non-null provider even when PLATFORM_JWT_SECRET is absent")
    void jwtTokenProviderReturnsFallbackWhenSecretMissing() {
        AIInferenceServiceLauncher launcher = new AIInferenceServiceLauncher();
        // Should not throw — uses dev-mode fallback when secret is short or missing
        JwtTokenProvider provider = launcher.jwtTokenProvider();
        assertThat(provider).isNotNull();
    }

    // ── cacheStore() ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("cacheStore() returns a non-null HybridStateStore")
    void cacheStoreReturnsNonNull() {
        AIInferenceServiceLauncher launcher = new AIInferenceServiceLauncher();
        HybridStateStore<String, byte[]> store = launcher.cacheStore();
        assertThat(store).isNotNull();
    }

    // ── rateLimiter() ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("rateLimiter() returns a non-null RateLimiter with default config")
    void rateLimiterReturnsNonNull() {
        AIInferenceServiceLauncher launcher = new AIInferenceServiceLauncher();
        RateLimiter limiter = launcher.rateLimiter();
        assertThat(limiter).isNotNull();
    }

    // ── metricsCollector() ─────────────────────────────────────────────────────

    @Test
    @DisplayName("metricsCollector() returns a non-null MetricsCollector")
    void metricsCollectorReturnsNonNull() {
        AIInferenceServiceLauncher launcher = new AIInferenceServiceLauncher();
        MeterRegistry registry = new SimpleMeterRegistry();
        assertThat(launcher.metricsCollector(registry)).isNotNull();
    }
}
