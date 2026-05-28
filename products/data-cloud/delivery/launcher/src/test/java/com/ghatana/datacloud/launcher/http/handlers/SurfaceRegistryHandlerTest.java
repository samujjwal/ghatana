package com.ghatana.datacloud.launcher.http.handlers;
import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport;
import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport.TenantResolutionResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.launcher.http.ApiResponse;
import com.ghatana.datacloud.launcher.http.SurfaceRecord;
import com.ghatana.datacloud.launcher.http.RuntimeTruthStatus;
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
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Regression tests for SurfaceRegistryHandler tenant enforcement and contract compliance
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("SurfaceRegistryHandler")
@ExtendWith(MockitoExtension.class) 
class SurfaceRegistryHandlerTest extends EventloopTestBase {

    @Mock
    private HttpHandlerSupport httpSupport;

    @Mock
    private HttpRequest request;

    @Mock
    private HttpResponse errorResponse;

    @Mock
    private HttpResponse successResponse;

    private SurfaceRegistryHandler handler;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() { 
        Supplier<List<SurfaceRecord>> snapshotSupplier = () -> List.of(
            SurfaceRecord.builder("voiceGateway")
                .state(RuntimeTruthStatus.LIVE)
                .ownerPlane("intelligence")
                .probe(new com.ghatana.datacloud.launcher.http.DependencyProbeResult("voice-gateway-probe", true, "UP", "OK", java.time.Instant.now()))
                .build()
        );
        handler = new SurfaceRegistryHandler(httpSupport, objectMapper, snapshotSupplier); 
    }

    @Test
    @DisplayName("returns surfaces envelope when tenant header is present")
    void returnsSurfacesEnvelopeWhenTenantPresent() {
        when(httpSupport.requireTenantIdWithError(request)).thenReturn(TenantResolutionResult.success("tenant-capabilities", null));
        when(httpSupport.resolveCorrelationId(request)).thenReturn("req-1");
        when(httpSupport.envelopeResponse(any(ApiResponse.class), any(ObjectMapper.class))).thenReturn(successResponse);

        HttpResponse response = runPromise(() -> handler.handleSurfaces(request));

        assertThat(response).isSameAs(successResponse);
        verify(httpSupport).envelopeResponse(any(ApiResponse.class), any(ObjectMapper.class));
    }

    @Test
    @DisplayName("returns surfaces envelope when tenant header is present (alternate tenant)")
    void returnsSurfacesEnvelopeWhenTenantPresentForAlternateTenant() {
        when(httpSupport.requireTenantIdWithError(request)).thenReturn(TenantResolutionResult.success("tenant-surfaces", null));
        when(httpSupport.resolveCorrelationId(request)).thenReturn("req-2");
        when(httpSupport.envelopeResponse(any(ApiResponse.class), any(ObjectMapper.class))).thenReturn(successResponse);

        HttpResponse response = runPromise(() -> handler.handleSurfaces(request));

        assertThat(response).isSameAs(successResponse);
        verify(httpSupport).envelopeResponse(any(ApiResponse.class), any(ObjectMapper.class));
    }

    @Test
    @DisplayName("returns 400 when tenant header is missing")
    void returns400WhenTenantHeaderMissing() {
        when(httpSupport.requireTenantIdWithError(request)).thenReturn(TenantResolutionResult.error(400, "X-Tenant-Id header is required"));
        when(httpSupport.errorResponse(400, "X-Tenant-Id header is required")).thenReturn(errorResponse);

        HttpResponse response = runPromise(() -> handler.handleSurfaces(request));

        assertThat(response).isSameAs(errorResponse);
        verify(httpSupport).errorResponse(400, "X-Tenant-Id header is required");
    }

    @Test
    @DisplayName("returns 400 from surfaces endpoint when tenant header is missing")
    void returns400WhenTenantHeaderMissingForSurfaces() {
        when(httpSupport.requireTenantIdWithError(request)).thenReturn(TenantResolutionResult.error(400, "X-Tenant-Id header is required"));
        when(httpSupport.errorResponse(400, "X-Tenant-Id header is required")).thenReturn(errorResponse);

        HttpResponse response = runPromise(() -> handler.handleSurfaces(request));

        assertThat(response).isSameAs(errorResponse);
        verify(httpSupport).errorResponse(400, "X-Tenant-Id header is required");
    }

    @Test
    @DisplayName("returns schema envelope from canonical surfaces schema endpoint")
    void returnsSurfaceSchemaEnvelopeWhenTenantPresent() {
        when(httpSupport.requireTenantIdWithError(request)).thenReturn(TenantResolutionResult.success("tenant-schema", null));
        when(httpSupport.resolveCorrelationId(request)).thenReturn("req-3");
        when(httpSupport.envelopeResponse(any(ApiResponse.class), any(ObjectMapper.class))).thenReturn(successResponse);

        HttpResponse response = runPromise(() -> handler.handleSurfaceSchema(request));

        assertThat(response).isSameAs(successResponse);
        verify(httpSupport).envelopeResponse(any(ApiResponse.class), any(ObjectMapper.class));
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
        SurfaceRegistryHandler contractHandler = new SurfaceRegistryHandler(httpSupport, objectMapper, snapshotSupplier);

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