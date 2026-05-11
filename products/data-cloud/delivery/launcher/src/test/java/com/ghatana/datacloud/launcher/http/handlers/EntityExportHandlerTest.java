package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.analytics.export.EntityExportService;
import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport;
import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport.TenantResolutionResult;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.bytebuf.ByteBuf;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Regression tests for EntityExportHandler tenant enforcement
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("EntityExportHandler")
@ExtendWith(MockitoExtension.class) 
class EntityExportHandlerTest extends EventloopTestBase {

    @Mock
    private EntityExportService exportService;

    @Mock
    private HttpHandlerSupport http;

    @Mock
    private HttpRequest request;

    @Mock
    private HttpResponse errorResponse;

    private EntityExportHandler handler;

    @BeforeEach
    void setUp() { 
        handler = new EntityExportHandler(exportService, http); 
        lenient().when(http.errorResponse(anyInt(), anyString())).thenReturn(errorResponse); 
    }

    @Test
    @DisplayName("export rejects missing tenant before export service access")
    void exportRejectsMissingTenant() { 
        when(http.requireTenantIdWithError(request)).thenReturn(TenantResolutionResult.error(401, "Unauthorized")); 

        HttpResponse response = runPromise(() -> handler.handleExportEntities(request)); 

        assertThat(response).isSameAs(errorResponse); 
        verify(exportService, never()).exportCsv("default", "default", java.util.Map.of(), 0); 
        verify(exportService, never()).exportNdjson("default", "default", java.util.Map.of(), 0); 
    }

        @Test
        @DisplayName("approved export route rejects missing confirmation token")
        void approvedExportRejectsMissingToken() {
        HttpResponse badRequest = org.mockito.Mockito.mock(HttpResponse.class);
        when(http.requireTenantIdWithError(request)).thenReturn(TenantResolutionResult.success("tenant-a", null));
        when(request.getPathParameter("collection")).thenReturn("users");
        when(request.loadBody()).thenReturn(Promise.of(ByteBuf.wrapForReading(
            "{\"dryRun\":false,\"format\":\"csv\"}".getBytes(StandardCharsets.UTF_8))));
        when(http.objectMapper()).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper());
        when(http.errorResponse(400,
            "confirmationToken is required to authorise PII export. Perform a dry-run first to obtain a valid token."))
            .thenReturn(badRequest);

        HttpResponse response = runPromise(() -> handler.handleExportEntitiesWithApproval(request));

        assertThat(response).isSameAs(badRequest);
        verify(exportService, never()).exportCsvGoverned(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt());
        verify(exportService, never()).exportNdjsonGoverned(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt());
        }

        @Test
        @DisplayName("approved export route rejects invalid confirmation token")
        void approvedExportRejectsInvalidToken() {
        HttpResponse forbidden = org.mockito.Mockito.mock(HttpResponse.class);
        when(http.requireTenantIdWithError(request)).thenReturn(TenantResolutionResult.success("tenant-a", null));
        when(request.getPathParameter("collection")).thenReturn("users");
        when(request.loadBody()).thenReturn(Promise.of(ByteBuf.wrapForReading(
            "{\"dryRun\":false,\"format\":\"csv\",\"confirmationToken\":\"bad-token\"}"
                .getBytes(StandardCharsets.UTF_8))));
        when(http.objectMapper()).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper());
        when(http.errorResponse(org.mockito.ArgumentMatchers.eq(403), org.mockito.ArgumentMatchers.contains("invalid or expired")))
            .thenReturn(forbidden);

        HttpResponse response = runPromise(() -> handler.handleExportEntitiesWithApproval(request));

        assertThat(response).isSameAs(forbidden);
        verify(exportService, never()).exportCsvGoverned(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt());
        verify(exportService, never()).exportNdjsonGoverned(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt());
        }

        @Test
        @DisplayName("approved export route uses governed CSV export after valid token")
        void approvedExportUsesGovernedCsvMethod() {
        when(http.requireTenantIdWithError(request)).thenReturn(TenantResolutionResult.success("tenant-a", null));
        when(request.getPathParameter("collection")).thenReturn("users");
        when(http.corsAllowOrigin()).thenReturn("*");
        when(http.objectMapper()).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper());

        String token = DestructiveActionToken.buildToken(
            "export-pii",
            "tenant-a",
            "users",
            Instant.now().toEpochMilli());

        when(request.loadBody()).thenReturn(Promise.of(ByteBuf.wrapForReading(
            ("{\"dryRun\":false,\"format\":\"csv\",\"confirmationToken\":\"" + token + "\"}")
                .getBytes(StandardCharsets.UTF_8))));
        when(exportService.exportCsvGoverned("tenant-a", "users", Map.of(), 10_000)).thenReturn(Promise.of("id,name\n1,alice"));

        HttpResponse response = runPromise(() -> handler.handleExportEntitiesWithApproval(request));

        Assertions.assertEquals(200, response.getCode());
        verify(exportService).exportCsvGoverned("tenant-a", "users", Map.of(), 10_000);
        verify(exportService, never()).exportCsv("tenant-a", "users", Map.of(), 10_000);
        }
}