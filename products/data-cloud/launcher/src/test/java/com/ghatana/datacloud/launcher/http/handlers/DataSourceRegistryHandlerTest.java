/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import io.activej.eventloop.Eventloop;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

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
class DataSourceRegistryHandlerTest {

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
            "Content-Type,Authorization"
        );
        handler = new DataSourceRegistryHandler(client, http, null);
        originalProfile = System.getenv("DATACLOUD_PROFILE");
    }

    @AfterEach
    void tearDown() {
        if (originalProfile != null) {
            System.setProperty("DATACLOUD_PROFILE", originalProfile);
        } else {
            System.clearProperty("DATACLOUD_PROFILE");
        }
    }

    @Test
    @DisplayName("Fabric metrics disabled in production profile")
    void fabricMetricsDisabledInProductionProfile() {
        // Set production profile
        System.setProperty("DATACLOUD_PROFILE", "production");

        HttpRequest request = HttpRequest.get("/api/v1/data-fabric/metrics")
            .withHeader("X-Tenant-Id", "test-tenant");

        HttpResponse response = handler.handleGetFabricMetrics(request).join();

        assertThat(response.getCode()).isEqualTo(200);
        
        Map<String, Object> body = parseJsonBody(response);
        assertThat(body.get("disabled")).isEqualTo(true);
        assertThat(body.get("preview")).isEqualTo(true);
        assertThat(body.get("tiers")).isEqualTo(List.of());
        assertThat(body.get("totalEventsPerSec")).isEqualTo(0.0);
        assertThat(body.get("totalStorageGb")).isEqualTo(0.0);
        assertThat(body.get("message")).isNotNull();
    }

    @Test
    @DisplayName("Fabric metrics disabled in staging profile")
    void fabricMetricsDisabledInStagingProfile() {
        // Set staging profile
        System.setProperty("DATACLOUD_PROFILE", "staging");

        HttpRequest request = HttpRequest.get("/api/v1/data-fabric/metrics")
            .withHeader("X-Tenant-Id", "test-tenant");

        HttpResponse response = handler.handleGetFabricMetrics(request).join();

        assertThat(response.getCode()).isEqualTo(200);
        
        Map<String, Object> body = parseJsonBody(response);
        assertThat(body.get("disabled")).isEqualTo(true);
        assertThat(body.get("preview")).isEqualTo(true);
        assertThat(body.get("tiers")).isEqualTo(List.of());
    }

    @Test
    @DisplayName("Fabric metrics return empty when no storage profiles configured in local profile")
    void fabricMetricsEmptyWhenNoStorageProfilesInLocalProfile() {
        // Set local profile (development)
        System.setProperty("DATACLOUD_PROFILE", "local");

        // Mock empty query result
        lenient().when(client.query(anyString(), anyString(), any()))
            .thenReturn(Promise.of(List.of()));

        HttpRequest request = HttpRequest.get("/api/v1/data-fabric/metrics")
            .withHeader("X-Tenant-Id", "test-tenant");

        HttpResponse response = handler.handleGetFabricMetrics(request).join();

        assertThat(response.getCode()).isEqualTo(200);
        
        Map<String, Object> body = parseJsonBody(response);
        assertThat(body.get("preview")).isEqualTo(true);
        assertThat(body.get("tiers")).isEqualTo(List.of());
        assertThat(body.get("totalEventsPerSec")).isEqualTo(0.0);
        assertThat(body.get("totalStorageGb")).isEqualTo(0.0);
        assertThat(body.get("message")).isNotNull();
        // Should NOT have disabled flag in local profile
        assertThat(body.containsKey("disabled")).isFalse();
    }

    @Test
    @DisplayName("Fabric metrics return real data from storage profiles when configured")
    void fabricMetricsReturnRealDataFromStorageProfiles() {
        // Set local profile (development)
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
            .thenReturn(Promise.of(List.of(hotEntity, warmEntity)));

        HttpRequest request = HttpRequest.get("/api/v1/data-fabric/metrics")
            .withHeader("X-Tenant-Id", "test-tenant");

        HttpResponse response = handler.handleGetFabricMetrics(request).join();

        assertThat(response.getCode()).isEqualTo(200);
        
        Map<String, Object> body = parseJsonBody(response);
        assertThat(body.get("preview")).isEqualTo(true);
        assertThat(body.get("disabled")).isFalse(); // Should not be disabled in local profile
        
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
        HttpRequest request = HttpRequest.get("/api/v1/data-fabric/metrics");
        // No X-Tenant-Id header

        HttpResponse response = handler.handleGetFabricMetrics(request).join();

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("Fabric metrics return degraded response on error")
    void fabricMetricsReturnDegradedResponseOnError() {
        System.setProperty("DATACLOUD_PROFILE", "local");

        // Mock query failure
        lenient().when(client.query(anyString(), anyString(), any()))
            .thenReturn(Promise.ofException(new RuntimeException("Database connection failed")));

        HttpRequest request = HttpRequest.get("/api/v1/data-fabric/metrics")
            .withHeader("X-Tenant-Id", "test-tenant");

        HttpResponse response = handler.handleGetFabricMetrics(request).join();

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
            String body = response.loadBody().get().asString();
            return http.objectMapper().readValue(body, Map.class);
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

    // Simple Promise implementation for testing
    private static class Promise<T> {
        private final T value;
        private final Throwable error;

        private Promise(T value) {
            this.value = value;
            this.error = null;
        }

        private Promise(Throwable error) {
            this.value = null;
            this.error = error;
        }

        static <T> Promise<T> of(T value) {
            return new Promise<>(value);
        }

        static <T> Promise<T> ofException(Throwable error) {
            return new Promise<>(error);
        }

        T join() {
            if (error != null) {
                throw new RuntimeException(error);
            }
            return value;
        }

        <R> Promise<R> map(java.util.function.Function<T, R> mapper) {
            if (error != null) {
                return new Promise<>(error);
            }
            try {
                return new Promise<>(mapper.apply(value));
            } catch (Exception e) {
                return new Promise<>(e);
            }
        }

        <R> Promise<R> then(java.util.function.Function<T, Promise<R>> mapper) {
            if (error != null) {
                return new Promise<>(error);
            }
            try {
                return mapper.apply(value);
            } catch (Exception e) {
                return new Promise<>(e);
            }
        }
    }
}
