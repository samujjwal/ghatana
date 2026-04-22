/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.http.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link HealthController}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for HealthController dependency-probe and status aggregation
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("HealthController")
class HealthControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HealthController controller;

    @BeforeEach
    void setUp() {
        controller = new HealthController("1.0.0-test");
    }

    private Map<String, Object> getHealth() throws Exception {
        HttpRequest request = mock(HttpRequest.class);
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        Promise<HttpResponse> promise = controller.handle(request, "health");
        HttpResponse response = promise.getResult();
        String body = response.getBody().getString(java.nio.charset.StandardCharsets.UTF_8);
        return MAPPER.readValue(body, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private Map<String, Object> getDeepHealth() throws Exception {
        HttpRequest request = mock(HttpRequest.class);
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        Promise<HttpResponse> promise = controller.handle(request, "health/deep");
        HttpResponse response = promise.getResult();
        String body = response.getBody().getString(java.nio.charset.StandardCharsets.UTF_8);
        return MAPPER.readValue(body, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    @Nested
    @DisplayName("handleHealth with no checks")
    class NoChecks {

        @Test
        @DisplayName("returns 'healthy' with version when no checks registered")
        void returnsHealthyWithVersion() throws Exception {
            Map<String, Object> body = getHealth();
            assertThat(body.get("status")).isEqualTo("healthy");
            assertThat(body.get("version")).isEqualTo("1.0.0-test");
            assertThat(body).containsKey("timestamp");
            assertThat(body).doesNotContainKey("components");
        }
    }

    @Nested
    @DisplayName("handleHealth with dependency checks")
    class WithChecks {

        @Test
        @DisplayName("reports 'healthy' when all checks return 'ok'")
        void reportsHealthyWhenAllOk() throws Exception {
            controller.addDependencyCheck("data-cloud", () -> "ok");
            controller.addDependencyCheck("review-queue", () -> "ok");

            Map<String, Object> body = getHealth();
            assertThat(body.get("status")).isEqualTo("healthy");
            @SuppressWarnings("unchecked")
            Map<String, Object> components = (Map<String, Object>) body.get("components");
            assertThat(components).containsEntry("data-cloud", "ok");
            assertThat(components).containsEntry("review-queue", "ok");
        }

        @Test
        @DisplayName("reports 'degraded' when one check returns non-ok status")
        void reportsDegradedWhenOneNotOk() throws Exception {
            controller.addDependencyCheck("data-cloud", () -> "disabled");
            controller.addDependencyCheck("review-queue", () -> "ok");

            Map<String, Object> body = getHealth();
            assertThat(body.get("status")).isEqualTo("degraded");
            @SuppressWarnings("unchecked")
            Map<String, Object> components = (Map<String, Object>) body.get("components");
            assertThat(components).containsEntry("data-cloud", "disabled");
        }

        @Test
        @DisplayName("reports 'degraded' and captures error message when check throws")
        void capturesErrorFromThrowingCheck() throws Exception {
            controller.addDependencyCheck("bad-dep", () -> {
                throw new RuntimeException("connection refused");
            });

            Map<String, Object> body = getHealth();
            assertThat(body.get("status")).isEqualTo("degraded");
            @SuppressWarnings("unchecked")
            Map<String, Object> components = (Map<String, Object>) body.get("components");
            assertThat(components.get("bad-dep").toString()).contains("connection refused");
        }

        @Test
        @DisplayName("multiple checks all ok → healthy")
        void multipleChecksAllOkIsHealthy() throws Exception {
            controller.addDependencyCheck("dep-a", () -> "ok");
            controller.addDependencyCheck("dep-b", () -> "ok");
            controller.addDependencyCheck("dep-c", () -> "ok");

            assertThat(getHealth().get("status")).isEqualTo("healthy");
        }

        @Test
        @DisplayName("deep probe includes deep-only checks and probe marker")
        void deepProbeIncludesDeepChecks() throws Exception {
            controller.addDependencyCheck("dep-a", () -> "ok");
            controller.addDeepDependencyCheck("dep-deep", () -> "misconfigured");

            Map<String, Object> parsed = getDeepHealth();

            assertThat(parsed.get("status")).isEqualTo("degraded");
            assertThat(parsed.get("probe")).isEqualTo("deep");
            @SuppressWarnings("unchecked")
            Map<String, Object> components = (Map<String, Object>) parsed.get("components");
            assertThat(components).containsEntry("dep-a", "ok");
            assertThat(components).containsEntry("dep-deep", "misconfigured");
        }

        @Test
        @DisplayName("deep probe includes async dependency checks")
        void deepProbeIncludesAsyncChecks() throws Exception {
            controller.addDependencyCheck("dep-a", () -> "ok");
            controller.addAsyncDeepDependencyCheck("dep-connectivity", () -> Promise.of("ok"));

            Map<String, Object> parsed = getDeepHealth();

            assertThat(parsed.get("status")).isEqualTo("healthy");
            assertThat(parsed.get("probe")).isEqualTo("deep");
            @SuppressWarnings("unchecked")
            Map<String, Object> components = (Map<String, Object>) parsed.get("components");
            assertThat(components).containsEntry("dep-a", "ok");
            assertThat(components).containsEntry("dep-connectivity", "ok");
        }

        @Test
        @DisplayName("deep probe includes structured runtime metadata when supplied")
        void deepProbeIncludesStructuredRuntimeMetadata() throws Exception {
            controller.addDependencyCheck("dep-a", () -> "ok");
            controller.setDeepResponseMetadataSupplier(() -> Map.of(
                "durability",
                Map.of(
                    "mode", "ephemeral",
                    "title", "Ephemeral runtime state"
                )
            ));

            Map<String, Object> parsed = getDeepHealth();

            @SuppressWarnings("unchecked")
            Map<String, Object> durability = (Map<String, Object>) parsed.get("durability");
            assertThat(durability).containsEntry("mode", "ephemeral");
            assertThat(durability).containsEntry("title", "Ephemeral runtime state");
        }
    }

    @Nested
    @DisplayName("markReady / markNotReady")
    class Readiness {

        @Test
        @DisplayName("handleReady returns not-ready before markReady")
        void returnsNotReadyInitially() throws Exception {
            HttpRequest request = mock(HttpRequest.class);
            when(request.getMethod()).thenReturn(HttpMethod.GET);
            Promise<HttpResponse> promise = controller.handle(request, "ready");
            HttpResponse response = promise.getResult();
            String body = response.getBody().getString(java.nio.charset.StandardCharsets.UTF_8);
            Map<String, Object> parsed = MAPPER.readValue(body,
                new com.fasterxml.jackson.core.type.TypeReference<>() {});
            assertThat(parsed.get("ready")).isEqualTo(false);
        }

        @Test
        @DisplayName("handleReady returns ready after markReady")
        void returnsReadyAfterMark() throws Exception {
            controller.markReady();
            HttpRequest request = mock(HttpRequest.class);
            when(request.getMethod()).thenReturn(HttpMethod.GET);
            Promise<HttpResponse> promise = controller.handle(request, "ready");
            HttpResponse response = promise.getResult();
            String body = response.getBody().getString(java.nio.charset.StandardCharsets.UTF_8);
            Map<String, Object> parsed = MAPPER.readValue(body,
                new com.fasterxml.jackson.core.type.TypeReference<>() {});
            assertThat(parsed.get("ready")).isEqualTo(true);
        }
    }
}
