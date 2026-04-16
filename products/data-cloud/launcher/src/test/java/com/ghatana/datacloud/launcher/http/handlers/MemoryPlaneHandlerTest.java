package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.DataCloudClient;
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
 * @doc.purpose Regression tests for MemoryPlaneHandler tenant enforcement
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("MemoryPlaneHandler")
@ExtendWith(MockitoExtension.class)
class MemoryPlaneHandlerTest extends EventloopTestBase {

    @Mock
    private DataCloudClient client;

    @Mock
    private HttpHandlerSupport http;

    @Mock
    private HttpRequest request;

    @Mock
    private HttpResponse errorResponse;

    private MemoryPlaneHandler handler;

    @BeforeEach
    void setUp() {
        handler = new MemoryPlaneHandler(client, http);
        when(http.errorResponse(400, "X-Tenant-Id header is required")).thenReturn(errorResponse);
    }

    @Test
    @DisplayName("store memory rejects missing tenant before loading body")
    void storeMemoryRejectsMissingTenant() {
        when(http.requireTenantIdOrFail(request)).thenReturn(null);

        HttpResponse response = runPromise(() -> handler.handleStoreMemory(request));

        assertThat(response).isSameAs(errorResponse);
        verify(request, never()).loadBody();
    }

    @Test
    @DisplayName("list memory rejects missing tenant before query access")
    void listMemoryRejectsMissingTenant() {
        when(http.requireTenantIdOrFail(request)).thenReturn(null);

        HttpResponse response = runPromise(() -> handler.handleListMemory(request));

        assertThat(response).isSameAs(errorResponse);
        verify(client, never()).query("default", "dc_memory", DataCloudClient.Query.limit(1));
    }

    @Test
    @DisplayName("get agent memory rejects missing tenant before query access")
    void getAgentMemoryRejectsMissingTenant() {
        when(http.requireTenantIdOrFail(request)).thenReturn(null);

        HttpResponse response = runPromise(() -> handler.handleGetAgentMemory(request));

        assertThat(response).isSameAs(errorResponse);
        verify(client, never()).query("default", "dc_memory", DataCloudClient.Query.limit(1));
    }

    @Test
    @DisplayName("memory by tier rejects missing tenant before query access")
    void getMemoryByTierRejectsMissingTenant() {
        when(http.requireTenantIdOrFail(request)).thenReturn(null);

        HttpResponse response = runPromise(() -> handler.handleGetAgentMemoryByTier(request));

        assertThat(response).isSameAs(errorResponse);
        verify(client, never()).query("default", "dc_memory", DataCloudClient.Query.limit(1));
    }

    @Test
    @DisplayName("search memory rejects missing tenant before loading body")
    void searchMemoryRejectsMissingTenant() {
        when(http.requireTenantIdOrFail(request)).thenReturn(null);

        HttpResponse response = runPromise(() -> handler.handleSearchAgentMemory(request));

        assertThat(response).isSameAs(errorResponse);
        verify(request, never()).loadBody();
    }

    @Test
    @DisplayName("delete memory rejects missing tenant before delete access")
    void deleteMemoryRejectsMissingTenant() {
        when(http.requireTenantIdOrFail(request)).thenReturn(null);

        HttpResponse response = runPromise(() -> handler.handleDeleteMemory(request));

        assertThat(response).isSameAs(errorResponse);
        verify(client, never()).delete("default", "dc_memory", "default");
    }

    @Test
    @DisplayName("retain memory rejects missing tenant before loading body")
    void retainMemoryRejectsMissingTenant() {
        when(http.requireTenantIdOrFail(request)).thenReturn(null);

        HttpResponse response = runPromise(() -> handler.handleRetainMemory(request));

        assertThat(response).isSameAs(errorResponse);
        verify(request, never()).loadBody();
    }
}