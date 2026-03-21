package com.ghatana.pipeline.registry.web;

import com.ghatana.pipeline.registry.service.CapabilitiesService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CapabilitiesController}.
 *
 * @doc.type test
 * @doc.purpose Verify HTTP responses for all capabilities discovery endpoints
 * @doc.layer product
 * @doc.pattern ControllerTest
 */
@DisplayName("CapabilitiesController tests")
@ExtendWith(MockitoExtension.class)
class CapabilitiesControllerTest extends EventloopTestBase {

    @Mock
    private CapabilitiesService capabilitiesService;

    private CapabilitiesController controller;

    @BeforeEach
    void setUp() {
        controller = new CapabilitiesController(capabilitiesService);
    }

    @Test
    @DisplayName("GET /admin/capabilities/schemas returns 200 with schema formats")
    void getSchemaFormats_returns200() {
        when(capabilitiesService.getSchemaFormats()).thenReturn(
                Map.of("schemaFormats", List.of(
                        Map.of("id", "json-schema", "display", "JSON Schema", "enabled", true)
                )));

        HttpRequest request = HttpRequest.get("http://localhost/admin/capabilities/schemas").build();
        HttpResponse response = runPromise(() -> controller.getSchemaFormats(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("GET /admin/capabilities/encodings returns 200 with encodings")
    void getEncodings_returns200() {
        when(capabilitiesService.getEncodings()).thenReturn(
                Map.of("encodings", List.of("JSON", "AVRO_BINARY")));

        HttpRequest request = HttpRequest.get("http://localhost/admin/capabilities/encodings").build();
        HttpResponse response = runPromise(() -> controller.getEncodings(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("GET /admin/capabilities/connectors returns 200 with connectors")
    void getConnectors_returns200() {
        when(capabilitiesService.getConnectors()).thenReturn(
                Map.of("connectors", List.of(
                        Map.of("type", "KAFKA", "direction", "ingress", "enabled", true)
                )));

        HttpRequest request = HttpRequest.get("http://localhost/admin/capabilities/connectors").build();
        HttpResponse response = runPromise(() -> controller.getConnectors(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("GET /admin/capabilities/transforms returns 200 with transforms")
    void getTransforms_returns200() {
        when(capabilitiesService.getTransforms()).thenReturn(
                Map.of("transforms", List.of(
                        Map.of("id", "FILTER", "description", "Filter transform")
                )));

        HttpRequest request = HttpRequest.get("http://localhost/admin/capabilities/transforms").build();
        HttpResponse response = runPromise(() -> controller.getTransforms(request));

        assertThat(response.getCode()).isEqualTo(200);
    }
}
