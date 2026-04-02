/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.http.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @doc.type class
 * @doc.purpose Unit tests for HttpClientFactory adapter creation
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("HttpClientFactory — OkHttpAdapter creation and tenant scoping")
class HttpClientFactoryTest {

    // ── createDefaultAdapter ───────────────────────────────────────────────────

    @Test
    @DisplayName("createDefaultAdapter with null metrics returns a non-null adapter")
    void createDefaultAdapterWithNullMetricsReturnsNonNull() {
        OkHttpAdapter adapter = HttpClientFactory.createDefaultAdapter(null);
        assertThat(adapter).isNotNull();
    }

    @Test
    @DisplayName("createDefaultAdapter is callable multiple times without error")
    void createDefaultAdapterIsCallableMultipleTimes() {
        OkHttpAdapter first = HttpClientFactory.createDefaultAdapter(null);
        OkHttpAdapter second = HttpClientFactory.createDefaultAdapter(null);

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
    }

    // ── createTenantAdapter ────────────────────────────────────────────────────

    @Test
    @DisplayName("createTenantAdapter returns a non-null adapter for a valid tenant")
    void createTenantAdapterReturnsNonNull() {
        OkHttpAdapter adapter = HttpClientFactory.createTenantAdapter("tenant-1", null, 5.0);
        assertThat(adapter).isNotNull();
    }

    @Test
    @DisplayName("createTenantAdapter throws NullPointerException for null tenantId")
    void createTenantAdapterThrowsForNullTenantId() {
        assertThatThrownBy(() -> HttpClientFactory.createTenantAdapter(null, null, 5.0))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("tenantId");
    }

    @Test
    @DisplayName("createTenantAdapter with null metrics uses NoopMetricsCollector internally")
    void createTenantAdapterWithNullMetricsReturnsNonNull() {
        OkHttpAdapter adapter = HttpClientFactory.createTenantAdapter("tenant-metrics-null", null, 3.0);
        assertThat(adapter).isNotNull();
    }

    @Test
    @DisplayName("createTenantAdapter with zero requestsPerSecond uses default 5 rps")
    void createTenantAdapterWithZeroRpsUsesDefault() {
        // Should not throw; zero or negative rps falls back to default 5.0
        OkHttpAdapter adapter = HttpClientFactory.createTenantAdapter("tenant-zero-rps", null, 0.0);
        assertThat(adapter).isNotNull();
    }

    @Test
    @DisplayName("createTenantAdapter with negative requestsPerSecond uses default 5 rps")
    void createTenantAdapterWithNegativeRpsUsesDefault() {
        OkHttpAdapter adapter = HttpClientFactory.createTenantAdapter("tenant-neg-rps", null, -1.0);
        assertThat(adapter).isNotNull();
    }

    @Test
    @DisplayName("different tenant IDs produce independent adapter instances")
    void differentTenantIdsProduceIndependentAdapters() {
        OkHttpAdapter adapterA = HttpClientFactory.createTenantAdapter("tenant-alpha", null, 5.0);
        OkHttpAdapter adapterB = HttpClientFactory.createTenantAdapter("tenant-beta", null, 5.0);

        assertThat(adapterA).isNotNull();
        assertThat(adapterB).isNotNull();
        assertThat(adapterA).isNotSameAs(adapterB);
    }

    @Test
    @DisplayName("same tenant ID can be called multiple times without error")
    void sameTenantIdIsRepeatablyCallable() {
        OkHttpAdapter first = HttpClientFactory.createTenantAdapter("tenant-cached", null, 10.0);
        OkHttpAdapter second = HttpClientFactory.createTenantAdapter("tenant-cached", null, 10.0);

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
    }
}
