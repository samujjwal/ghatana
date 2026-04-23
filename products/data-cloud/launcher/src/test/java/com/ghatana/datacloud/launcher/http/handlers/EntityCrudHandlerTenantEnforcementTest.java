package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.infrastructure.storage.OpenSearchConnector;
import com.ghatana.datacloud.launcher.http.TraceSpanSupport;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import java.util.Map;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Regression tests for EntityCrudHandler tenant enforcement
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("EntityCrudHandler tenant enforcement")
@ExtendWith(MockitoExtension.class) // GH-90000
class EntityCrudHandlerTenantEnforcementTest extends EventloopTestBase {

    @Mock
    private DataCloudClient client;

    @Mock
    private HttpHandlerSupport http;

    @Mock
    private HttpRequest request;

    @Mock
    private HttpResponse errorResponse;

    @Mock
    private OpenSearchConnector openSearchConnector;

    @Mock
    private BiConsumer<String, Map<String, Object>> wsBroadcaster;

    private EntityCrudHandler handler;

    @BeforeEach
    void setUp() { // GH-90000
        handler = new EntityCrudHandler(client, http, wsBroadcaster) // GH-90000
            .withTraceSupport(TraceSpanSupport.disabled()) // GH-90000
            .withOpenSearchConnector(openSearchConnector); // GH-90000
        when(http.errorResponse(400, "X-Tenant-Id header is required")).thenReturn(errorResponse); // GH-90000
    }

    @Test
    @DisplayName("save rejects missing tenant before loading body")
    void saveRejectsMissingTenant() { // GH-90000
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleSaveEntity(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(request, never()).loadBody(); // GH-90000
    }

    @Test
    @DisplayName("get rejects missing tenant before store access")
    void getRejectsMissingTenant() { // GH-90000
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleGetEntity(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(client, never()).findById("default", "default", "default"); // GH-90000
    }

    @Test
    @DisplayName("query rejects missing tenant before store access")
    void queryRejectsMissingTenant() { // GH-90000
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleQueryEntities(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(client, never()).query("default", "default", DataCloudClient.Query.limit(1)); // GH-90000
    }

    @Test
    @DisplayName("delete rejects missing tenant before store access")
    void deleteRejectsMissingTenant() { // GH-90000
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleDeleteEntity(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(client, never()).findById("default", "default", "default"); // GH-90000
    }

    @Test
    @DisplayName("batch save rejects missing tenant before loading body")
    void batchSaveRejectsMissingTenant() { // GH-90000
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleBatchSaveEntities(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(request, never()).loadBody(); // GH-90000
    }

    @Test
    @DisplayName("batch delete rejects missing tenant before loading body")
    void batchDeleteRejectsMissingTenant() { // GH-90000
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleBatchDeleteEntities(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(request, never()).loadBody(); // GH-90000
    }

    @Test
    @DisplayName("full text search rejects missing tenant before search execution")
    void fullTextSearchRejectsMissingTenant() { // GH-90000
        when(request.getPathParameter("collection")).thenReturn("orders");
        when(request.getQueryParameter("q")).thenReturn("status:open");
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleFullTextSearch(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verifyNoInteractions(openSearchConnector); // GH-90000
    }

    @Test
    @DisplayName("as-of query rejects missing tenant before store access")
    void asOfRejectsMissingTenant() { // GH-90000
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleGetEntityAsOf(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(client, never()).findById("default", "default", "default"); // GH-90000
    }
}