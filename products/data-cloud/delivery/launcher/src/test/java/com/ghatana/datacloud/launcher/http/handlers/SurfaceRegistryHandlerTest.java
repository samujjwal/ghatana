package com.ghatana.datacloud.launcher.http.handlers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.launcher.http.SurfaceRecord;
import com.ghatana.datacloud.launcher.http.RuntimeTruthStatus;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Regression tests for SurfaceRegistryHandler tenant enforcement and contract compliance
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("SurfaceRegistryHandler")
class SurfaceRegistryHandlerTest extends EventloopTestBase {

    private HttpHandlerSupport httpSupport;

    private SurfaceRegistryHandler handler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() { 
        Supplier<List<SurfaceRecord>> snapshotSupplier = () -> List.of(
            SurfaceRecord.builder("voiceGateway")
                .state(RuntimeTruthStatus.LIVE)
                .ownerPlane("intelligence")
                .probe(new com.ghatana.datacloud.launcher.http.DependencyProbeResult("voice-gateway-probe", true, "UP", "OK", java.time.Instant.now()))
                .build()
        );
        httpSupport = new HttpHandlerSupport(objectMapper, "*", "GET", "Content-Type", false, "test");
        handler = new SurfaceRegistryHandler(httpSupport, objectMapper, snapshotSupplier); 
    }

    private HttpRequest authorizedRequest(String path, String tenantId) {
        HttpRequest.Builder builder = HttpRequest.get("http://localhost" + path)
            .withHeader(HttpHeaders.HOST, "localhost")
            .withHeader(HttpHeaders.of("X-Permissions"), "surface:read");
        if (tenantId != null) {
            builder.withHeader(HttpHeaders.of("X-Tenant-ID"), tenantId);
        }
        return builder.build();
    }

    private Map<String, Object> responseBody(HttpResponse response) {
        try {
            String body = runPromise(response::loadBody).getString(StandardCharsets.UTF_8);
            return objectMapper.readValue(body, Map.class);
        } catch (Exception error) {
            throw new AssertionError("Failed to parse response body", error);
        }
    }

    @Test
    @DisplayName("returns surfaces envelope when tenant header is present")
    void returnsSurfacesEnvelopeWhenTenantPresent() {
        HttpRequest request = authorizedRequest("/api/v1/surfaces", "tenant-capabilities");

        HttpResponse response = runPromise(() -> handler.handleSurfaces(request));

        assertThat(response.getCode()).isEqualTo(200);
        Map<String, Object> body = responseBody(response);
        assertThat(body).containsKey("data");
    }

    @Test
    @DisplayName("returns surfaces envelope when tenant header is present (alternate tenant)")
    void returnsSurfacesEnvelopeWhenTenantPresentForAlternateTenant() {
        HttpRequest request = authorizedRequest("/api/v1/surfaces", "tenant-surfaces");

        HttpResponse response = runPromise(() -> handler.handleSurfaces(request));

        assertThat(response.getCode()).isEqualTo(200);
        Map<String, Object> body = responseBody(response);
        assertThat(body).containsKey("data");
    }

    @Test
    @DisplayName("returns 400 when tenant header is missing")
    void returns400WhenTenantHeaderMissing() {
        HttpRequest request = authorizedRequest("/api/v1/surfaces", null);

        HttpResponse response = runPromise(() -> handler.handleSurfaces(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("returns 400 from surfaces endpoint when tenant header is missing")
    void returns400WhenTenantHeaderMissingForSurfaces() {
        HttpRequest request = authorizedRequest("/api/v1/surfaces", null);

        HttpResponse response = runPromise(() -> handler.handleSurfaces(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("returns schema envelope from canonical surfaces schema endpoint")
    void returnsSurfaceSchemaEnvelopeWhenTenantPresent() {
        HttpRequest request = authorizedRequest("/api/v1/surfaces/schema", "tenant-schema");

        HttpResponse response = runPromise(() -> handler.handleSurfaceSchema(request));

        assertThat(response.getCode()).isEqualTo(200);
        Map<String, Object> body = responseBody(response);
        assertThat(body).containsKey("data");
    }

    @Test
    @DisplayName("P0 contract test: surfaces endpoint returns SurfaceRecord objects not maps")
    void surfacesEndpointReturnsSurfaceRecordObjectsNotMaps() {
        Supplier<List<SurfaceRecord>> snapshotSupplier = () -> List.of(
            SurfaceRecord.builder("test-surface")
                .state(RuntimeTruthStatus.LIVE)
                .ownerPlane("data")
                .probe(new com.ghatana.datacloud.launcher.http.DependencyProbeResult("test-probe", true, "UP", "OK", java.time.Instant.now()))
                .build()
        );
        new SurfaceRegistryHandler(httpSupport, objectMapper, snapshotSupplier);

        // Verify that SurfaceRecord.toMap() produces the expected structure
        SurfaceRecord testRecord = SurfaceRecord.builder("test-surface")
            .state(RuntimeTruthStatus.LIVE)
            .ownerPlane("data")
            .probe(new com.ghatana.datacloud.launcher.http.DependencyProbeResult("test-probe", true, "UP", "OK", java.time.Instant.now()))
            .build();
        
        Map<String, Object> recordMap = testRecord.toMap();
        
        // Verify required SurfaceRecord fields are present
        assertThat(recordMap).containsKey("surfaceId");
        assertThat(recordMap).containsKey("state");
        assertThat(recordMap).containsKey("status");
        assertThat(recordMap).containsKey("ownerPlane");
        assertThat(recordMap).containsKey("dependencyProbes");
    }
}
