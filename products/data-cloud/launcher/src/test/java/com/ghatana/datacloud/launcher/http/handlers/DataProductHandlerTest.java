package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.plugins.lineage.LineagePlugin;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Regression tests for DataProductHandler tenant enforcement
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataProductHandler [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class DataProductHandlerTest extends EventloopTestBase {

    @Mock
    private DataCloudClient client;

    @Mock
    private HttpHandlerSupport http;

    @Mock
    private LineagePlugin lineagePlugin;

    @Mock
    private HttpRequest request;

    @Mock
    private HttpResponse errorResponse;

    private DataProductHandler handler;

    @BeforeEach
    void setUp() { // GH-90000
        handler = new DataProductHandler(client, http, new ObjectMapper(), lineagePlugin); // GH-90000
        when(http.errorResponse(400, "X-Tenant-Id header is required")).thenReturn(errorResponse); // GH-90000
    }

    @Test
    @DisplayName("publish rejects missing tenant before reading body [GH-90000]")
    void publishRejectsMissingTenant() { // GH-90000
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handlePublishDataProduct(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(request, never()).loadBody(); // GH-90000
        verify(client, never()).query(any(), any(), any()); // GH-90000
    }

    @Test
    @DisplayName("list rejects missing tenant before querying products [GH-90000]")
    void listRejectsMissingTenant() { // GH-90000
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleListDataProducts(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(client, never()).query(any(), any(), any()); // GH-90000
    }

    @Test
    @DisplayName("subscribe rejects missing tenant before reading body [GH-90000]")
    void subscribeRejectsMissingTenant() { // GH-90000
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleSubscribe(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(request, never()).loadBody(); // GH-90000
        verify(client, never()).findById(any(), any(), any()); // GH-90000
    }
}