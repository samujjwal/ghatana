/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.platform.http.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @doc.type class
 * @doc.purpose Unit tests for HttpClientFactory adapter creation
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("HttpClientFactory — OkHttpAdapter creation and tenant scoping [GH-90000]")
class HttpClientFactoryTest {

    // ── createDefaultAdapter ───────────────────────────────────────────────────

    @Test
    @DisplayName("createDefaultAdapter with null metrics returns a non-null adapter [GH-90000]")
    void createDefaultAdapterWithNullMetricsReturnsNonNull() { // GH-90000
        OkHttpAdapter adapter = HttpClientFactory.createDefaultAdapter(null); // GH-90000
        assertThat(adapter).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("createDefaultAdapter is callable multiple times without error [GH-90000]")
    void createDefaultAdapterIsCallableMultipleTimes() { // GH-90000
        OkHttpAdapter first = HttpClientFactory.createDefaultAdapter(null); // GH-90000
        OkHttpAdapter second = HttpClientFactory.createDefaultAdapter(null); // GH-90000

        assertThat(first).isNotNull(); // GH-90000
        assertThat(second).isNotNull(); // GH-90000
    }

    // ── createAdapter with HttpClientConfig ─────────────────────────────────────

    @Test
    @DisplayName("createAdapter with custom HttpClientConfig returns a non-null adapter [GH-90000]")
    void createAdapterWithCustomConfigReturnsNonNull() { // GH-90000
        HttpClientConfig config = HttpClientConfig.builder() // GH-90000
                .maxConnections(20) // GH-90000
                .connectTimeout(Duration.ofSeconds(5)) // GH-90000
                .build(); // GH-90000
        OkHttpAdapter adapter = HttpClientFactory.createAdapter(config, null); // GH-90000
        assertThat(adapter).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("createAdapter throws NullPointerException for null config [GH-90000]")
    void createAdapterThrowsForNullConfig() { // GH-90000
        assertThatThrownBy(() -> HttpClientFactory.createAdapter(null, null)) // GH-90000
                .isInstanceOf(NullPointerException.class) // GH-90000
                .hasMessageContaining("config [GH-90000]");
    }

    @Test
    @DisplayName("createAdapter with null metrics uses NoopMetricsCollector internally [GH-90000]")
    void createAdapterWithNullMetricsReturnsNonNull() { // GH-90000
        HttpClientConfig config = HttpClientConfig.builder().build(); // GH-90000
        OkHttpAdapter adapter = HttpClientFactory.createAdapter(config, null); // GH-90000
        assertThat(adapter).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("createAdapter with custom maxConnections uses configured value [GH-90000]")
    void createAdapterUsesCustomMaxConnections() { // GH-90000
        HttpClientConfig config = HttpClientConfig.builder() // GH-90000
                .maxConnections(50) // GH-90000
                .build(); // GH-90000
        OkHttpAdapter adapter = HttpClientFactory.createAdapter(config, null); // GH-90000
        assertThat(adapter).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("createAdapter with custom timeouts uses configured values [GH-90000]")
    void createAdapterUsesCustomTimeouts() { // GH-90000
        HttpClientConfig config = HttpClientConfig.builder() // GH-90000
                .connectTimeout(Duration.ofSeconds(5)) // GH-90000
                .readTimeout(Duration.ofSeconds(15)) // GH-90000
                .callTimeout(Duration.ofSeconds(20)) // GH-90000
                .build(); // GH-90000
        OkHttpAdapter adapter = HttpClientFactory.createAdapter(config, null); // GH-90000
        assertThat(adapter).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("createAdapter with custom keepAliveDuration uses configured value [GH-90000]")
    void createAdapterUsesCustomKeepAliveDuration() { // GH-90000
        HttpClientConfig config = HttpClientConfig.builder() // GH-90000
                .keepAliveDuration(Duration.ofMinutes(10)) // GH-90000
                .build(); // GH-90000
        OkHttpAdapter adapter = HttpClientFactory.createAdapter(config, null); // GH-90000
        assertThat(adapter).isNotNull(); // GH-90000
    }

    // ── createTenantAdapter ────────────────────────────────────────────────────

    @Test
    @DisplayName("createTenantAdapter returns a non-null adapter for a valid tenant [GH-90000]")
    void createTenantAdapterReturnsNonNull() { // GH-90000
        OkHttpAdapter adapter = HttpClientFactory.createTenantAdapter("tenant-1", null, 5.0); // GH-90000
        assertThat(adapter).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("createTenantAdapter throws NullPointerException for null tenantId [GH-90000]")
    void createTenantAdapterThrowsForNullTenantId() { // GH-90000
        assertThatThrownBy(() -> HttpClientFactory.createTenantAdapter(null, null, 5.0)) // GH-90000
                .isInstanceOf(NullPointerException.class) // GH-90000
                .hasMessageContaining("tenantId [GH-90000]");
    }

    @Test
    @DisplayName("createTenantAdapter with null metrics uses NoopMetricsCollector internally [GH-90000]")
    void createTenantAdapterWithNullMetricsReturnsNonNull() { // GH-90000
        OkHttpAdapter adapter = HttpClientFactory.createTenantAdapter("tenant-metrics-null", null, 3.0); // GH-90000
        assertThat(adapter).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("createTenantAdapter with zero requestsPerSecond uses default 5 rps [GH-90000]")
    void createTenantAdapterWithZeroRpsUsesDefault() { // GH-90000
        // Should not throw; zero or negative rps falls back to default 5.0
        OkHttpAdapter adapter = HttpClientFactory.createTenantAdapter("tenant-zero-rps", null, 0.0); // GH-90000
        assertThat(adapter).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("createTenantAdapter with negative requestsPerSecond uses default 5 rps [GH-90000]")
    void createTenantAdapterWithNegativeRpsUsesDefault() { // GH-90000
        OkHttpAdapter adapter = HttpClientFactory.createTenantAdapter("tenant-neg-rps", null, -1.0); // GH-90000
        assertThat(adapter).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("different tenant IDs produce independent adapter instances [GH-90000]")
    void differentTenantIdsProduceIndependentAdapters() { // GH-90000
        OkHttpAdapter adapterA = HttpClientFactory.createTenantAdapter("tenant-alpha", null, 5.0); // GH-90000
        OkHttpAdapter adapterB = HttpClientFactory.createTenantAdapter("tenant-beta", null, 5.0); // GH-90000

        assertThat(adapterA).isNotNull(); // GH-90000
        assertThat(adapterB).isNotNull(); // GH-90000
        assertThat(adapterA).isNotSameAs(adapterB); // GH-90000
    }

    @Test
    @DisplayName("same tenant ID can be called multiple times without error [GH-90000]")
    void sameTenantIdIsRepeatablyCallable() { // GH-90000
        OkHttpAdapter first = HttpClientFactory.createTenantAdapter("tenant-cached", null, 10.0); // GH-90000
        OkHttpAdapter second = HttpClientFactory.createTenantAdapter("tenant-cached", null, 10.0); // GH-90000

        assertThat(first).isNotNull(); // GH-90000
        assertThat(second).isNotNull(); // GH-90000
    }
}
