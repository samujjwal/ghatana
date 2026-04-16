package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.launcher.http.ApiResponse;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Regression tests for CapabilityRegistryHandler tenant enforcement
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("CapabilityRegistryHandler")
@ExtendWith(MockitoExtension.class)
class CapabilityRegistryHandlerTest extends EventloopTestBase {

    @Mock
    private HttpHandlerSupport httpSupport;

    @Mock
    private HttpRequest request;

    @Mock
    private HttpResponse errorResponse;

    @Mock
    private HttpResponse successResponse;

    private CapabilityRegistryHandler handler;

    @BeforeEach
    void setUp() {
        Supplier<Map<String, Object>> snapshotSupplier = () -> Map.of("voiceGateway", Map.of("status", "available"));
        handler = new CapabilityRegistryHandler(httpSupport, new ObjectMapper(), snapshotSupplier);
    }

    @Test
    @DisplayName("returns capability envelope when tenant header is present")
    void returnsCapabilityEnvelopeWhenTenantPresent() {
        when(httpSupport.requireTenantIdOrFail(request)).thenReturn("tenant-capabilities");
        when(httpSupport.resolveCorrelationId(request)).thenReturn("req-1");
        when(httpSupport.envelopeResponse(any(ApiResponse.class), any(ObjectMapper.class))).thenReturn(successResponse);

        HttpResponse response = runPromise(() -> handler.handleCapabilities(request));

        assertThat(response).isSameAs(successResponse);
        verify(httpSupport).envelopeResponse(any(ApiResponse.class), any(ObjectMapper.class));
    }

    @Test
    @DisplayName("returns 400 when tenant header is missing")
    void returns400WhenTenantHeaderMissing() {
        when(httpSupport.requireTenantIdOrFail(request)).thenReturn(null);
        when(httpSupport.errorResponse(400, "X-Tenant-Id header is required")).thenReturn(errorResponse);

        HttpResponse response = runPromise(() -> handler.handleCapabilities(request));

        assertThat(response).isSameAs(errorResponse);
        verify(httpSupport).errorResponse(400, "X-Tenant-Id header is required");
    }
}