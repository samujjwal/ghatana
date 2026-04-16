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
@DisplayName("DataProductHandler")
@ExtendWith(MockitoExtension.class)
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
    void setUp() {
        handler = new DataProductHandler(client, http, new ObjectMapper(), lineagePlugin);
        when(http.errorResponse(400, "X-Tenant-Id header is required")).thenReturn(errorResponse);
    }

    @Test
    @DisplayName("publish rejects missing tenant before reading body")
    void publishRejectsMissingTenant() {
        when(http.requireTenantIdOrFail(request)).thenReturn(null);

        HttpResponse response = runPromise(() -> handler.handlePublishDataProduct(request));

        assertThat(response).isSameAs(errorResponse);
        verify(request, never()).loadBody();
        verify(client, never()).query(any(), any(), any());
    }

    @Test
    @DisplayName("list rejects missing tenant before querying products")
    void listRejectsMissingTenant() {
        when(http.requireTenantIdOrFail(request)).thenReturn(null);

        HttpResponse response = runPromise(() -> handler.handleListDataProducts(request));

        assertThat(response).isSameAs(errorResponse);
        verify(client, never()).query(any(), any(), any());
    }

    @Test
    @DisplayName("subscribe rejects missing tenant before reading body")
    void subscribeRejectsMissingTenant() {
        when(http.requireTenantIdOrFail(request)).thenReturn(null);

        HttpResponse response = runPromise(() -> handler.handleSubscribe(request));

        assertThat(response).isSameAs(errorResponse);
        verify(request, never()).loadBody();
        verify(client, never()).findById(any(), any(), any());
    }
}