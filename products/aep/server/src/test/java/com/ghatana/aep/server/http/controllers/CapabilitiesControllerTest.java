/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.http.controllers;

import com.ghatana.pipeline.registry.service.CapabilitiesService;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CapabilitiesController}.
 *
 * <p>Validates that the controller correctly delegates to {@link CapabilitiesService},
 * merges a {@code timestamp} field into every response, and forwards the enriched map
 * to the {@code jsonResponse} function for HTTP serialisation.
 *
 * @doc.type class
 * @doc.purpose Unit tests for CapabilitiesController HTTP handler delegation and response enrichment
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("CapabilitiesController")
class CapabilitiesControllerTest {

    /** Captures the payload passed to {@code jsonResponse} so tests can assert on it. */
    private final AtomicReference<Map<String, Object>> lastPayload = new AtomicReference<>();

    /** Stub HttpResponse returned by the capturing {@code jsonResponse} function. */
    private final HttpResponse stubResponse = mock(HttpResponse.class);

    /** Capturing jsonResponse function that stores the payload for assertions. */
    private final Function<Map<String, Object>, HttpResponse> capturingJsonResponse = data -> {
        lastPayload.set(new HashMap<>(data));
        return stubResponse;
    };

    private CapabilitiesService service;
    private CapabilitiesController controller;

    /** A minimal GET request used as the argument to every handler under test. */
    private static HttpRequest getRequest() {
        HttpRequest request = mock(HttpRequest.class);
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        return request;
    }

    @BeforeEach
    void setUp() {
        // Use the real service — it is a pure value object with no external dependencies
        service = new CapabilitiesService();
        controller = new CapabilitiesController(service, capturingJsonResponse);
        lastPayload.set(null);
    }

    // =========================================================================
    // Schema Capabilities
    // =========================================================================

    @Nested
    @DisplayName("handleSchemaCapabilities")
    class SchemaCapabilities {

        @Test
        @DisplayName("returns a settled promise resolving to the captured response")
        void returnsSettledPromise() {
            Promise<HttpResponse> promise = controller.handleSchemaCapabilities(getRequest());

            assertThat(promise.getResult()).isSameAs(stubResponse);
        }

        @Test
        @DisplayName("payload contains schemaFormats key from the service")
        void payloadContainsSchemaFormats() {
            controller.handleSchemaCapabilities(getRequest());

            Map<String, Object> payload = lastPayload.get();
            assertThat(payload).containsKey("schemaFormats");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> formats = (List<Map<String, Object>>) payload.get("schemaFormats");
            assertThat(formats).isNotEmpty();
        }

        @Test
        @DisplayName("payload contains a non-blank ISO-8601 timestamp")
        void payloadContainsTimestamp() {
            Instant before = Instant.now().minusSeconds(1);

            controller.handleSchemaCapabilities(getRequest());

            Map<String, Object> payload = lastPayload.get();
            assertThat(payload).containsKey("timestamp");
            String ts = (String) payload.get("timestamp");
            Instant parsed = Instant.parse(ts);
            assertThat(parsed).isAfterOrEqualTo(before);
        }
    }

    // =========================================================================
    // Connector Capabilities
    // =========================================================================

    @Nested
    @DisplayName("handleConnectorCapabilities")
    class ConnectorCapabilities {

        @Test
        @DisplayName("returns a settled promise resolving to the captured response")
        void returnsSettledPromise() {
            Promise<HttpResponse> promise = controller.handleConnectorCapabilities(getRequest());

            assertThat(promise.getResult()).isSameAs(stubResponse);
        }

        @Test
        @DisplayName("payload contains connectors key from the service")
        void payloadContainsConnectors() {
            controller.handleConnectorCapabilities(getRequest());

            Map<String, Object> payload = lastPayload.get();
            assertThat(payload).containsKey("connectors");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> connectors = (List<Map<String, Object>>) payload.get("connectors");
            assertThat(connectors).isNotEmpty();
        }

        @Test
        @DisplayName("payload contains a non-blank ISO-8601 timestamp")
        void payloadContainsTimestamp() {
            Instant before = Instant.now().minusSeconds(1);

            controller.handleConnectorCapabilities(getRequest());

            String ts = (String) lastPayload.get().get("timestamp");
            assertThat(Instant.parse(ts)).isAfterOrEqualTo(before);
        }
    }

    // =========================================================================
    // Encoding Capabilities
    // =========================================================================

    @Nested
    @DisplayName("handleEncodingCapabilities")
    class EncodingCapabilities {

        @Test
        @DisplayName("returns a settled promise resolving to the captured response")
        void returnsSettledPromise() {
            Promise<HttpResponse> promise = controller.handleEncodingCapabilities(getRequest());

            assertThat(promise.getResult()).isSameAs(stubResponse);
        }

        @Test
        @DisplayName("payload contains encodings key from the service")
        void payloadContainsEncodings() {
            controller.handleEncodingCapabilities(getRequest());

            Map<String, Object> payload = lastPayload.get();
            assertThat(payload).containsKey("encodings");
            @SuppressWarnings("unchecked")
            List<String> encodings = (List<String>) payload.get("encodings");
            assertThat(encodings).isNotEmpty();
        }

        @Test
        @DisplayName("payload contains a non-blank ISO-8601 timestamp")
        void payloadContainsTimestamp() {
            Instant before = Instant.now().minusSeconds(1);

            controller.handleEncodingCapabilities(getRequest());

            String ts = (String) lastPayload.get().get("timestamp");
            assertThat(Instant.parse(ts)).isAfterOrEqualTo(before);
        }
    }

    // =========================================================================
    // Transform Capabilities
    // =========================================================================

    @Nested
    @DisplayName("handleTransformCapabilities")
    class TransformCapabilities {

        @Test
        @DisplayName("returns a settled promise resolving to the captured response")
        void returnsSettledPromise() {
            Promise<HttpResponse> promise = controller.handleTransformCapabilities(getRequest());

            assertThat(promise.getResult()).isSameAs(stubResponse);
        }

        @Test
        @DisplayName("payload contains transforms key from the service")
        void payloadContainsTransforms() {
            controller.handleTransformCapabilities(getRequest());

            Map<String, Object> payload = lastPayload.get();
            assertThat(payload).containsKey("transforms");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> transforms = (List<Map<String, Object>>) payload.get("transforms");
            assertThat(transforms).isNotEmpty();
        }

        @Test
        @DisplayName("payload contains a non-blank ISO-8601 timestamp")
        void payloadContainsTimestamp() {
            Instant before = Instant.now().minusSeconds(1);

            controller.handleTransformCapabilities(getRequest());

            String ts = (String) lastPayload.get().get("timestamp");
            assertThat(Instant.parse(ts)).isAfterOrEqualTo(before);
        }
    }

    // =========================================================================
    // Isolation: each handler produces an independent copy of the map
    // =========================================================================

    @Nested
    @DisplayName("Response isolation")
    class ResponseIsolation {

        @Test
        @DisplayName("schema and connector handlers produce independent payload copies")
        void handlersProduceIndependentPayloads() {
            controller.handleSchemaCapabilities(getRequest());
            Map<String, Object> schemaPayload = new HashMap<>(lastPayload.get());

            controller.handleConnectorCapabilities(getRequest());
            Map<String, Object> connectorPayload = new HashMap<>(lastPayload.get());

            // Keys should differ: schemaFormats vs. connectors
            assertThat(schemaPayload).containsKey("schemaFormats");
            assertThat(schemaPayload).doesNotContainKey("connectors");
            assertThat(connectorPayload).containsKey("connectors");
            assertThat(connectorPayload).doesNotContainKey("schemaFormats");
        }

        @Test
        @DisplayName("mutating the captured payload does not affect subsequent calls")
        void mutatingCapturedPayloadDoesNotAffectService() {
            controller.handleSchemaCapabilities(getRequest());
            Map<String, Object> first = lastPayload.get();
            first.put("injected", "pollution");

            controller.handleSchemaCapabilities(getRequest());
            Map<String, Object> second = lastPayload.get();

            assertThat(second).doesNotContainKey("injected");
        }
    }
}
