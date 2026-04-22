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
@DisplayName("HealthController [GH-90000]")
class HealthControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper(); // GH-90000

    private HealthController controller;

    @BeforeEach
    void setUp() { // GH-90000
        controller = new HealthController("1.0.0-test [GH-90000]");
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
    @DisplayName("handleHealth with no checks [GH-90000]")
    class NoChecks {

        @Test
        @DisplayName("returns 'healthy' with version when no checks registered [GH-90000]")
        void returnsHealthyWithVersion() throws Exception { // GH-90000
            Map<String, Object> body = getHealth(); // GH-90000
            assertThat(body.get("status [GH-90000]")).isEqualTo("healthy [GH-90000]");
            assertThat(body.get("version [GH-90000]")).isEqualTo("1.0.0-test [GH-90000]");
            assertThat(body).containsKey("timestamp [GH-90000]");
            assertThat(body).doesNotContainKey("components [GH-90000]");
        }
    }

    @Nested
    @DisplayName("handleHealth with dependency checks [GH-90000]")
    class WithChecks {

        @Test
        @DisplayName("reports 'healthy' when all checks return 'ok' [GH-90000]")
        void reportsHealthyWhenAllOk() throws Exception { // GH-90000
            controller.addDependencyCheck("data-cloud", () -> "ok"); // GH-90000
            controller.addDependencyCheck("review-queue", () -> "ok"); // GH-90000

            Map<String, Object> body = getHealth(); // GH-90000
            assertThat(body.get("status [GH-90000]")).isEqualTo("healthy [GH-90000]");
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> components = (Map<String, Object>) body.get("components [GH-90000]");
            assertThat(components).containsEntry("data-cloud", "ok"); // GH-90000
            assertThat(components).containsEntry("review-queue", "ok"); // GH-90000
        }

        @Test
        @DisplayName("reports 'degraded' when one check returns non-ok status [GH-90000]")
        void reportsDegradedWhenOneNotOk() throws Exception { // GH-90000
            controller.addDependencyCheck("data-cloud", () -> "disabled"); // GH-90000
            controller.addDependencyCheck("review-queue", () -> "ok"); // GH-90000

            Map<String, Object> body = getHealth(); // GH-90000
            assertThat(body.get("status [GH-90000]")).isEqualTo("degraded [GH-90000]");
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> components = (Map<String, Object>) body.get("components [GH-90000]");
            assertThat(components).containsEntry("data-cloud", "disabled"); // GH-90000
        }

        @Test
        @DisplayName("reports 'degraded' and captures error message when check throws [GH-90000]")
        void capturesErrorFromThrowingCheck() throws Exception { // GH-90000
            controller.addDependencyCheck("bad-dep", () -> { // GH-90000
                throw new RuntimeException("connection refused [GH-90000]");
            });

            Map<String, Object> body = getHealth(); // GH-90000
            assertThat(body.get("status [GH-90000]")).isEqualTo("degraded [GH-90000]");
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> components = (Map<String, Object>) body.get("components [GH-90000]");
            assertThat(components.get("bad-dep [GH-90000]").toString()).contains("connection refused [GH-90000]");
        }

        @Test
        @DisplayName("multiple checks all ok → healthy [GH-90000]")
        void multipleChecksAllOkIsHealthy() throws Exception { // GH-90000
            controller.addDependencyCheck("dep-a", () -> "ok"); // GH-90000
            controller.addDependencyCheck("dep-b", () -> "ok"); // GH-90000
            controller.addDependencyCheck("dep-c", () -> "ok"); // GH-90000

            assertThat(getHealth().get("status [GH-90000]")).isEqualTo("healthy [GH-90000]");
        }

        @Test
        @DisplayName("deep probe includes deep-only checks and probe marker [GH-90000]")
        void deepProbeIncludesDeepChecks() throws Exception { // GH-90000
            controller.addDependencyCheck("dep-a", () -> "ok"); // GH-90000
            controller.addDeepDependencyCheck("dep-deep", () -> "misconfigured"); // GH-90000

            Map<String, Object> parsed = getDeepHealth(); // GH-90000

            assertThat(parsed.get("status [GH-90000]")).isEqualTo("degraded [GH-90000]");
            assertThat(parsed.get("probe [GH-90000]")).isEqualTo("deep [GH-90000]");
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> components = (Map<String, Object>) parsed.get("components [GH-90000]");
            assertThat(components).containsEntry("dep-a", "ok"); // GH-90000
            assertThat(components).containsEntry("dep-deep", "misconfigured"); // GH-90000
        }

        @Test
        @DisplayName("deep probe includes async dependency checks [GH-90000]")
        void deepProbeIncludesAsyncChecks() throws Exception { // GH-90000
            controller.addDependencyCheck("dep-a", () -> "ok"); // GH-90000
            controller.addAsyncDeepDependencyCheck("dep-connectivity", () -> Promise.of("ok [GH-90000]"));

            Map<String, Object> parsed = getDeepHealth(); // GH-90000

            assertThat(parsed.get("status [GH-90000]")).isEqualTo("healthy [GH-90000]");
            assertThat(parsed.get("probe [GH-90000]")).isEqualTo("deep [GH-90000]");
            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> components = (Map<String, Object>) parsed.get("components [GH-90000]");
            assertThat(components).containsEntry("dep-a", "ok"); // GH-90000
            assertThat(components).containsEntry("dep-connectivity", "ok"); // GH-90000
        }

        @Test
        @DisplayName("deep probe includes structured runtime metadata when supplied [GH-90000]")
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

            @SuppressWarnings("unchecked [GH-90000]")
            Map<String, Object> durability = (Map<String, Object>) parsed.get("durability [GH-90000]");
            assertThat(durability).containsEntry("mode", "ephemeral"); // GH-90000
            assertThat(durability).containsEntry("title", "Ephemeral runtime state"); // GH-90000
        }
    }

    @Nested
    @DisplayName("markReady / markNotReady [GH-90000]")
    class Readiness {

        @Test
        @DisplayName("handleReady returns not-ready before markReady [GH-90000]")
        void returnsNotReadyInitially() throws Exception { // GH-90000
            HttpRequest request = mock(HttpRequest.class); // GH-90000
            when(request.getMethod()).thenReturn(HttpMethod.GET); // GH-90000
            Promise<HttpResponse> promise = controller.handle(request, "ready"); // GH-90000
            HttpResponse response = promise.getResult(); // GH-90000
            String body = response.getBody().getString(java.nio.charset.StandardCharsets.UTF_8); // GH-90000
            Map<String, Object> parsed = MAPPER.readValue(body, // GH-90000
                new com.fasterxml.jackson.core.type.TypeReference<>() {}); // GH-90000
            assertThat(parsed.get("ready [GH-90000]")).isEqualTo(false);
        }

        @Test
        @DisplayName("handleReady returns ready after markReady [GH-90000]")
        void returnsReadyAfterMark() throws Exception { // GH-90000
            controller.markReady(); // GH-90000
            HttpRequest request = mock(HttpRequest.class); // GH-90000
            when(request.getMethod()).thenReturn(HttpMethod.GET); // GH-90000
            Promise<HttpResponse> promise = controller.handle(request, "ready"); // GH-90000
            HttpResponse response = promise.getResult(); // GH-90000
            String body = response.getBody().getString(java.nio.charset.StandardCharsets.UTF_8); // GH-90000
            Map<String, Object> parsed = MAPPER.readValue(body, // GH-90000
                new com.fasterxml.jackson.core.type.TypeReference<>() {}); // GH-90000
            assertThat(parsed.get("ready [GH-90000]")).isEqualTo(true);
        }
    }
}
