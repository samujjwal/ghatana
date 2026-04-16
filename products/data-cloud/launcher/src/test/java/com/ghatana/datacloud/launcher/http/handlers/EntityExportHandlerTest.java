package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.analytics.export.EntityExportService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
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
        when(http.errorResponse(400, "X-Tenant-Id header is required")).thenReturn(errorResponse);
    }

    @Test
    @DisplayName("export rejects missing tenant before export service access")
    void exportRejectsMissingTenant() {
        when(http.requireTenantIdOrFail(request)).thenReturn(null);

        HttpResponse response = runPromise(() -> handler.handleExportEntities(request));

        assertThat(response).isSameAs(errorResponse);
        verify(exportService, never()).exportCsv("default", "default", java.util.Map.of(), 0);
        verify(exportService, never()).exportNdjson("default", "default", java.util.Map.of(), 0);
    }
}