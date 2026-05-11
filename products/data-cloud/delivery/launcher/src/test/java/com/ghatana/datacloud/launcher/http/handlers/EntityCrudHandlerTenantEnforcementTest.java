package com.ghatana.datacloud.launcher.http.handlers;
import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport;
import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport.TenantResolutionResult;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
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
@ExtendWith(MockitoExtension.class) 
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
    void setUp() { 
        handler = new EntityCrudHandler(client, http, wsBroadcaster) 
            .withTraceSupport(TraceSpanSupport.disabled()) 
            .withOpenSearchConnector(openSearchConnector); 
        lenient().when(http.errorResponse(anyInt(), anyString())).thenReturn(errorResponse); 
    }

    @Test
    @DisplayName("save rejects missing tenant before loading body")
    void saveRejectsMissingTenant() { 
        when(http.requireTenantIdWithError(request)).thenReturn(TenantResolutionResult.error(401, "Unauthorized")); 
        when(request.getPathParameter("collection")).thenReturn("test_collection"); 

        HttpResponse response = runPromise(() -> handler.handleSaveEntity(request)); 

        assertThat(response).isSameAs(errorResponse); 
        verify(request, never()).loadBody(); 
    }

    @Test
    @DisplayName("get rejects missing tenant before store access")
    void getRejectsMissingTenant() { 
        when(http.requireTenantIdWithError(request)).thenReturn(TenantResolutionResult.error(401, "Unauthorized")); 

        HttpResponse response = runPromise(() -> handler.handleGetEntity(request)); 

        assertThat(response).isSameAs(errorResponse); 
        verify(client, never()).findById("default", "default", "default"); 
    }

    @Test
    @DisplayName("query rejects missing tenant before store access")
    void queryRejectsMissingTenant() { 
        when(http.requireTenantIdWithError(request)).thenReturn(TenantResolutionResult.error(401, "Unauthorized")); 

        HttpResponse response = runPromise(() -> handler.handleQueryEntities(request)); 

        assertThat(response).isSameAs(errorResponse); 
        verify(client, never()).query("default", "default", DataCloudClient.Query.limit(1)); 
    }

    @Test
    @DisplayName("delete rejects missing tenant before store access")
    void deleteRejectsMissingTenant() { 
        when(http.requireTenantIdWithError(request)).thenReturn(TenantResolutionResult.error(401, "Unauthorized")); 

        HttpResponse response = runPromise(() -> handler.handleDeleteEntity(request)); 

        assertThat(response).isSameAs(errorResponse); 
        verify(client, never()).findById("default", "default", "default"); 
    }

    @Test
    @DisplayName("batch save rejects missing tenant before loading body")
    void batchSaveRejectsMissingTenant() { 
        when(http.requireTenantIdWithError(request)).thenReturn(TenantResolutionResult.error(401, "Unauthorized")); 

        HttpResponse response = runPromise(() -> handler.handleBatchSaveEntities(request)); 

        assertThat(response).isSameAs(errorResponse); 
        verify(request, never()).loadBody(); 
    }

    @Test
    @DisplayName("batch delete rejects missing tenant before loading body")
    void batchDeleteRejectsMissingTenant() { 
        when(http.requireTenantIdWithError(request)).thenReturn(TenantResolutionResult.error(401, "Unauthorized")); 

        HttpResponse response = runPromise(() -> handler.handleBatchDeleteEntities(request)); 

        assertThat(response).isSameAs(errorResponse); 
        verify(request, never()).loadBody(); 
    }

    @Test
    @DisplayName("full text search rejects missing tenant before search execution")
    void fullTextSearchRejectsMissingTenant() { 
        when(request.getPathParameter("collection")).thenReturn("orders");
        when(request.getQueryParameter("q")).thenReturn("status:open");
        when(http.requireTenantIdWithError(request)).thenReturn(TenantResolutionResult.error(401, "Unauthorized")); 

        HttpResponse response = runPromise(() -> handler.handleFullTextSearch(request)); 

        assertThat(response).isSameAs(errorResponse); 
        verifyNoInteractions(openSearchConnector); 
    }

    @Test
    @DisplayName("as-of query rejects missing tenant before store access")
    void asOfRejectsMissingTenant() { 
        when(http.requireTenantIdWithError(request)).thenReturn(TenantResolutionResult.error(401, "Unauthorized")); 

        HttpResponse response = runPromise(() -> handler.handleGetEntityAsOf(request)); 

        assertThat(response).isSameAs(errorResponse); 
        verify(client, never()).findById("default", "default", "default"); 
    }
}