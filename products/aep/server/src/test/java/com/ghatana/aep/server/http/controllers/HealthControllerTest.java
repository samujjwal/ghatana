/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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

    private static final ObjectMapper MAPPER = new ObjectMapper(); // GH-90000

    private HealthController controller;

    @BeforeEach
    void setUp() { // GH-90000
        controller = new HealthController("1.0.0-test");
    }

    private Map<String, Object> getHealth() throws Exception { // GH-90000
        HttpRequest request = mock(HttpRequest.class); // GH-90000
        when(request.getMethod()).thenReturn(HttpMethod.GET); // GH-90000
        Promise<HttpResponse> promise = controller.handle(request, "health"); // GH-90000
        HttpResponse response = promise.getResult(); // GH-90000
        String body = response.getBody().getString(java.nio.charset.StandardCharsets.UTF_8); // GH-90000
        return MAPPER.readValue(body, new com.fasterxml.jackson.core.type.TypeReference<>() {}); // GH-90000
    }

    private Map<String, Object> getDeepHealth() throws Exception { // GH-90000
        HttpRequest request = mock(HttpRequest.class); // GH-90000
        when(request.getMethod()).thenReturn(HttpMethod.GET); // GH-90000
        Promise<HttpResponse> promise = controller.handle(request, "health/deep"); // GH-90000
        HttpResponse response = promise.getResult(); // GH-90000
        String body = response.getBody().getString(java.nio.charset.StandardCharsets.UTF_8); // GH-90000
        return MAPPER.readValue(body, new com.fasterxml.jackson.core.type.TypeReference<>() {}); // GH-90000
    }

    @Nested
    @DisplayName("handleHealth with no checks")
    class NoChecks {

        @Test
        @DisplayName("returns 'healthy' with version when no checks registered")
        void returnsHealthyWithVersion() throws Exception { // GH-90000
            Map<String, Object> body = getHealth(); // GH-90000
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
        void reportsHealthyWhenAllOk() throws Exception { // GH-90000
            controller.addDependencyCheck("data-cloud", () -> "ok"); // GH-90000
            controller.addDependencyCheck("review-queue", () -> "ok"); // GH-90000

            Map<String, Object> body = getHealth(); // GH-90000
            assertThat(body.get("status")).isEqualTo("healthy");
            @SuppressWarnings("unchecked")
            Map<String, Object> components = (Map<String, Object>) body.get("components");
            assertThat(components).containsEntry("data-cloud", "ok"); // GH-90000
            assertThat(components).containsEntry("review-queue", "ok"); // GH-90000
        }

        @Test
        @DisplayName("reports 'degraded' when one check returns non-ok status")
        void reportsDegradedWhenOneNotOk() throws Exception { // GH-90000
            controller.addDependencyCheck("data-cloud", () -> "disabled"); // GH-90000
            controller.addDependencyCheck("review-queue", () -> "ok"); // GH-90000

            Map<String, Object> body = getHealth(); // GH-90000
            assertThat(body.get("status")).isEqualTo("degraded");
            @SuppressWarnings("unchecked")
            Map<String, Object> components = (Map<String, Object>) body.get("components");
            assertThat(components).containsEntry("data-cloud", "disabled"); // GH-90000
        }

        @Test
        @DisplayName("reports 'degraded' and captures error message when check throws")
        void capturesErrorFromThrowingCheck() throws Exception { // GH-90000
            controller.addDependencyCheck("bad-dep", () -> { // GH-90000
                throw new RuntimeException("connection refused");
            });

            Map<String, Object> body = getHealth(); // GH-90000
            assertThat(body.get("status")).isEqualTo("degraded");
            @SuppressWarnings("unchecked")
            Map<String, Object> components = (Map<String, Object>) body.get("components");
            assertThat(components.get("bad-dep").toString()).contains("connection refused");
        }

        @Test
        @DisplayName("multiple checks all ok → healthy")
        void multipleChecksAllOkIsHealthy() throws Exception { // GH-90000
            controller.addDependencyCheck("dep-a", () -> "ok"); // GH-90000
            controller.addDependencyCheck("dep-b", () -> "ok"); // GH-90000
            controller.addDependencyCheck("dep-c", () -> "ok"); // GH-90000

            assertThat(getHealth().get("status")).isEqualTo("healthy");
        }

        @Test
        @DisplayName("deep probe includes deep-only checks and probe marker")
        void deepProbeIncludesDeepChecks() throws Exception { // GH-90000
            controller.addDependencyCheck("dep-a", () -> "ok"); // GH-90000
            controller.addDeepDependencyCheck("dep-deep", () -> "misconfigured"); // GH-90000

            Map<String, Object> parsed = getDeepHealth(); // GH-90000

            assertThat(parsed.get("status")).isEqualTo("degraded");
            assertThat(parsed.get("probe")).isEqualTo("deep");
            @SuppressWarnings("unchecked")
            Map<String, Object> components = (Map<String, Object>) parsed.get("components");
            assertThat(components).containsEntry("dep-a", "ok"); // GH-90000
            assertThat(components).containsEntry("dep-deep", "misconfigured"); // GH-90000
        }

        @Test
        @DisplayName("deep probe includes async dependency checks")
        void deepProbeIncludesAsyncChecks() throws Exception { // GH-90000
            controller.addDependencyCheck("dep-a", () -> "ok"); // GH-90000
            controller.addAsyncDeepDependencyCheck("dep-connectivity", () -> Promise.of("ok"));

            Map<String, Object> parsed = getDeepHealth(); // GH-90000

            assertThat(parsed.get("status")).isEqualTo("healthy");
            assertThat(parsed.get("probe")).isEqualTo("deep");
            @SuppressWarnings("unchecked")
            Map<String, Object> components = (Map<String, Object>) parsed.get("components");
            assertThat(components).containsEntry("dep-a", "ok"); // GH-90000
            assertThat(components).containsEntry("dep-connectivity", "ok"); // GH-90000
        }

        @Test
        @DisplayName("deep probe includes structured runtime metadata when supplied")
        void deepProbeIncludesStructuredRuntimeMetadata() throws Exception { // GH-90000
            controller.addDependencyCheck("dep-a", () -> "ok"); // GH-90000
            controller.setDeepResponseMetadataSupplier(() -> Map.of( // GH-90000
                "durability",
                Map.of( // GH-90000
                    "mode", "ephemeral",
                    "title", "Ephemeral runtime state"
                )
            ));

            Map<String, Object> parsed = getDeepHealth(); // GH-90000

            @SuppressWarnings("unchecked")
            Map<String, Object> durability = (Map<String, Object>) parsed.get("durability");
            assertThat(durability).containsEntry("mode", "ephemeral"); // GH-90000
            assertThat(durability).containsEntry("title", "Ephemeral runtime state"); // GH-90000
        }
    }

    @Nested
    @DisplayName("markReady / markNotReady")
    class Readiness {

        @Test
        @DisplayName("handleReady returns not-ready before markReady")
        void returnsNotReadyInitially() throws Exception { // GH-90000
            HttpRequest request = mock(HttpRequest.class); // GH-90000
            when(request.getMethod()).thenReturn(HttpMethod.GET); // GH-90000
            Promise<HttpResponse> promise = controller.handle(request, "ready"); // GH-90000
            HttpResponse response = promise.getResult(); // GH-90000
            String body = response.getBody().getString(java.nio.charset.StandardCharsets.UTF_8); // GH-90000
            Map<String, Object> parsed = MAPPER.readValue(body, // GH-90000
                new com.fasterxml.jackson.core.type.TypeReference<>() {}); // GH-90000
            assertThat(parsed.get("ready")).isEqualTo(false);
        }

        @Test
        @DisplayName("handleReady returns ready after markReady")
        void returnsReadyAfterMark() throws Exception { // GH-90000
            controller.markReady(); // GH-90000
            HttpRequest request = mock(HttpRequest.class); // GH-90000
            when(request.getMethod()).thenReturn(HttpMethod.GET); // GH-90000
            Promise<HttpResponse> promise = controller.handle(request, "ready"); // GH-90000
            HttpResponse response = promise.getResult(); // GH-90000
            String body = response.getBody().getString(java.nio.charset.StandardCharsets.UTF_8); // GH-90000
            Map<String, Object> parsed = MAPPER.readValue(body, // GH-90000
                new com.fasterxml.jackson.core.type.TypeReference<>() {}); // GH-90000
            assertThat(parsed.get("ready")).isEqualTo(true);
        }
    }
}
