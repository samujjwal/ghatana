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
@ExtendWith(MockitoExtension.class) // GH-90000
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
    void setUp() { // GH-90000
        handler = new MemoryPlaneHandler(client, http); // GH-90000
        when(http.errorResponse(400, "X-Tenant-Id header is required")).thenReturn(errorResponse); // GH-90000
    }

    @Test
    @DisplayName("store memory rejects missing tenant before loading body")
    void storeMemoryRejectsMissingTenant() { // GH-90000
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleStoreMemory(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(request, never()).loadBody(); // GH-90000
    }

    @Test
    @DisplayName("list memory rejects missing tenant before query access")
    void listMemoryRejectsMissingTenant() { // GH-90000
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleListMemory(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(client, never()).query("default", "dc_memory", DataCloudClient.Query.limit(1)); // GH-90000
    }

    @Test
    @DisplayName("get agent memory rejects missing tenant before query access")
    void getAgentMemoryRejectsMissingTenant() { // GH-90000
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleGetAgentMemory(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(client, never()).query("default", "dc_memory", DataCloudClient.Query.limit(1)); // GH-90000
    }

    @Test
    @DisplayName("memory by tier rejects missing tenant before query access")
    void getMemoryByTierRejectsMissingTenant() { // GH-90000
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleGetAgentMemoryByTier(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(client, never()).query("default", "dc_memory", DataCloudClient.Query.limit(1)); // GH-90000
    }

    @Test
    @DisplayName("search memory rejects missing tenant before loading body")
    void searchMemoryRejectsMissingTenant() { // GH-90000
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleSearchAgentMemory(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(request, never()).loadBody(); // GH-90000
    }

    @Test
    @DisplayName("delete memory rejects missing tenant before delete access")
    void deleteMemoryRejectsMissingTenant() { // GH-90000
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleDeleteMemory(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(client, never()).delete("default", "dc_memory", "default"); // GH-90000
    }

    @Test
    @DisplayName("retain memory rejects missing tenant before loading body")
    void retainMemoryRejectsMissingTenant() { // GH-90000
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleRetainMemory(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(request, never()).loadBody(); // GH-90000
    }
}