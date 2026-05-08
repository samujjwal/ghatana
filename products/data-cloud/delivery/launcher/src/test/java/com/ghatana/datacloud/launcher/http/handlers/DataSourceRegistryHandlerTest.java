/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.feature.DataCloudFeature;
import com.ghatana.datacloud.feature.DataCloudFeatureFlags;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * Production-grade tests for DataSourceRegistryHandler.
 *
 * <p>Tests verify that Data Fabric metrics are properly disabled in production profiles
 * and that no hardcoded demo metrics are returned.
 *
 * @doc.type class
 * @doc.purpose Production-grade tests for DataSourceRegistryHandler with focus on fabric metrics
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataSourceRegistryHandler Production Tests")
@ExtendWith(MockitoExtension.class)
@Tag("production")
class DataSourceRegistryHandlerTest extends EventloopTestBase {

    @Mock
    private DataCloudClient client;

    private HttpHandlerSupport http;
    private DataSourceRegistryHandler handler;
    private String originalProfile;

    @BeforeEach
    void setUp() {
        http = new HttpHandlerSupport(
            new ObjectMapper(),
            "*",
            "GET,POST,PUT,DELETE,OPTIONS",
            "Content-Type,Authorization",
            true // strictTenantResolution — ensures missing X-Tenant-Id returns null (not a fallback)
        );
        handler = new DataSourceRegistryHandler(client, http, null, null /* no audit service needed for metrics tests */);
        originalProfile = System.getenv("DATACLOUD_PROFILE");
    }

    @AfterEach
    void tearDown() {
        if (originalProfile != null) {
            System.setProperty("DATACLOUD_PROFILE", originalProfile);
        } else {
            System.clearProperty("DATACLOUD_PROFILE");
        }
        DataCloudFeatureFlags.clearOverrides();
    }

    @Test
    @DisplayName("Fabric metrics return unavailable when DATA_CLOUD_DATA_FABRIC feature flag is disabled (default)")
    void fabricMetrics_featureFlagDisabled_returnsUnavailable() {
        // DC-P1-002: Feature flag is disabled by default; no profile override needed
        // DataCloudFeatureFlags.isEnabled(DATA_CLOUD_DATA_FABRIC) returns false (default)

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/data-fabric/metrics")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "test-tenant")
            .build();

        HttpResponse response = runPromise(() -> handler.handleGetFabricMetrics(request));

        assertThat(response.getCode()).isEqualTo(200);

        Map<String, Object> body = parseJsonBody(response);
        assertThat(body.get("disabled")).isEqualTo(true);
        assertThat(body.get("preview")).isEqualTo(true);
        assertThat(body.get("capability")).isEqualTo("unavailable");
        assertThat(body.get("tiers")).isEqualTo(List.of());
        assertThat(body.get("message")).asString().contains("/api/v1/surfaces");
    }

    @Test
    @DisplayName("Fabric metrics disabled in production profile")
    void fabricMetricsDisabledInProductionProfile() {
        DataCloudFeatureFlags.override(DataCloudFeature.DATA_CLOUD_DATA_FABRIC, true);
        System.setProperty("DATACLOUD_PROFILE", "production");

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/data-fabric/metrics")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "test-tenant")
            .build();

        HttpResponse response = runPromise(() -> handler.handleGetFabricMetrics(request));

        assertThat(response.getCode()).isEqualTo(200);

        Map<String, Object> body = parseJsonBody(response);
        assertThat(body.get("disabled")).isEqualTo(true);
        assertThat(body.get("preview")).isEqualTo(true);
        assertThat(body.get("capability")).isEqualTo("preview");
        assertThat(body.get("tiers")).isEqualTo(List.of());
        assertThat(body.get("totalEventsPerSec")).isEqualTo(0.0);
        assertThat(body.get("totalStorageGb")).isEqualTo(0.0);
        assertThat(body.get("message")).isNotNull();
    }

    @Test
    @DisplayName("Fabric metrics disabled in staging profile")
    void fabricMetricsDisabledInStagingProfile() {
        DataCloudFeatureFlags.override(DataCloudFeature.DATA_CLOUD_DATA_FABRIC, true);
        System.setProperty("DATACLOUD_PROFILE", "staging");

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/data-fabric/metrics")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "test-tenant")
            .build();

        HttpResponse response = runPromise(() -> handler.handleGetFabricMetrics(request));

        assertThat(response.getCode()).isEqualTo(200);

        Map<String, Object> body = parseJsonBody(response);
        assertThat(body.get("disabled")).isEqualTo(true);
        assertThat(body.get("preview")).isEqualTo(true);
        assertThat(body.get("tiers")).isEqualTo(List.of());
    }

    @Test
    @DisplayName("Fabric metrics return empty when no storage profiles configured in local profile")
    void fabricMetricsEmptyWhenNoStorageProfilesInLocalProfile() {
        DataCloudFeatureFlags.override(DataCloudFeature.DATA_CLOUD_DATA_FABRIC, true);
        System.setProperty("DATACLOUD_PROFILE", "local");

        lenient().when(client.query(anyString(), anyString(), any()))
            .thenReturn(io.activej.promise.Promise.of(List.of()));

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/data-fabric/metrics")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "test-tenant")
            .build();

        HttpResponse response = runPromise(() -> handler.handleGetFabricMetrics(request));

        assertThat(response.getCode()).isEqualTo(200);

        Map<String, Object> body = parseJsonBody(response);
        assertThat(body.get("preview")).isEqualTo(true);
        assertThat(body.get("tiers")).isEqualTo(List.of());
        assertThat(body.get("totalEventsPerSec")).isEqualTo(0.0);
        assertThat(body.get("totalStorageGb")).isEqualTo(0.0);
        assertThat(body.get("message")).isNotNull();
        // Should NOT have disabled flag in local profile
        assertThat(body.containsKey("disabled")).isEqualTo(false);
    }

    @Test
    @DisplayName("Fabric metrics return real data from storage profiles when configured")
    void fabricMetricsReturnRealDataFromStorageProfiles() {
        DataCloudFeatureFlags.override(DataCloudFeature.DATA_CLOUD_DATA_FABRIC, true);
        System.setProperty("DATACLOUD_PROFILE", "local");

        // Mock storage profile entities with real metrics
        DataCloudClient.Entity hotEntity = mockEntity("hot-connection", Map.of(
            "type", "REDIS",
            "tier", "HOT",
            "throughputEps", 1500.0,
            "latencyP99Ms", 2.5,
            "errorRate", 0.001,
            "queueDepth", 15,
            "healthStatus", "healthy"
        ));

        DataCloudClient.Entity warmEntity = mockEntity("warm-connection", Map.of(
            "type", "POSTGRESQL",
            "tier", "WARM",
            "throughputEps", 800.0,
            "latencyP99Ms", 10.0,
            "errorRate", 0.002,
            "queueDepth", 20,
            "healthStatus", "healthy",
            "storageGb", 50.0
        ));

        lenient().when(client.query(anyString(), anyString(), any()))
            .thenReturn(io.activej.promise.Promise.of(List.of(hotEntity, warmEntity)));

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/data-fabric/metrics")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "test-tenant")
            .build();

        HttpResponse response = runPromise(() -> handler.handleGetFabricMetrics(request));

        assertThat(response.getCode()).isEqualTo(200);

        Map<String, Object> body = parseJsonBody(response);
        assertThat(body.get("preview")).isEqualTo(true);
        assertThat(body.containsKey("disabled")).isEqualTo(false);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tiers = (List<Map<String, Object>>) body.get("tiers");
        assertThat(tiers).hasSize(2);

        // Verify HOT tier metrics
        Map<String, Object> hotTier = tiers.stream()
            .filter(t -> "HOT".equals(t.get("tier")))
            .findFirst()
            .orElseThrow();
        assertThat(hotTier.get("throughputEps")).isEqualTo(1500.0);
        assertThat(hotTier.get("latencyP99Ms")).isEqualTo(2.5);
        assertThat(hotTier.get("status")).isEqualTo("healthy");

        // Verify WARM tier metrics
        Map<String, Object> warmTier = tiers.stream()
            .filter(t -> "WARM".equals(t.get("tier")))
            .findFirst()
            .orElseThrow();
        assertThat(warmTier.get("throughputEps")).isEqualTo(800.0);
        assertThat(warmTier.get("storageGb")).isEqualTo(50.0);

        // Verify totals are calculated from real data
        assertThat(body.get("totalEventsPerSec")).isEqualTo(2300.0);
        assertThat(body.get("totalStorageGb")).isEqualTo(50.0);
    }

    @Test
    @DisplayName("Fabric metrics require tenant ID header")
    void fabricMetricsRequireTenantId() {
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/data-fabric/metrics")
            .build();

        HttpResponse response = runPromise(() -> handler.handleGetFabricMetrics(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("Fabric metrics return degraded response on error")
    void fabricMetricsReturnDegradedResponseOnError() {
        DataCloudFeatureFlags.override(DataCloudFeature.DATA_CLOUD_DATA_FABRIC, true);
        System.setProperty("DATACLOUD_PROFILE", "local");

        // Mock query failure
        lenient().when(client.query(anyString(), anyString(), any()))
            .thenReturn(io.activej.promise.Promise.ofException(
                new RuntimeException("Database connection failed")));

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/data-fabric/metrics")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "test-tenant")
            .build();

        HttpResponse response = runPromise(() -> handler.handleGetFabricMetrics(request));

        assertThat(response.getCode()).isEqualTo(200);

        Map<String, Object> body = parseJsonBody(response);
        assertThat(body.get("preview")).isEqualTo(true);
        assertThat(body.get("degraded")).isEqualTo(true);
        assertThat(body.get("error")).isNotNull();
        assertThat(body.get("tiers")).isEqualTo(List.of());
    }

    // Helper methods

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonBody(HttpResponse response) {
        try {
            String bodyStr = runPromise(() ->
                response.loadBody().map(buf -> buf.getString(java.nio.charset.StandardCharsets.UTF_8)));
            return http.objectMapper().readValue(bodyStr, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON response", e);
        }
    }

    private DataCloudClient.Entity mockEntity(String id, Map<String, Object> data) {
        DataCloudClient.Entity entity = mock(DataCloudClient.Entity.class);
        lenient().when(entity.id()).thenReturn(id);
        lenient().when(entity.data()).thenReturn(data);
        return entity;
    }
}
