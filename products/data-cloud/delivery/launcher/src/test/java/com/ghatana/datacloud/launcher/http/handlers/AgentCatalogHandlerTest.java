package com.ghatana.datacloud.launcher.http.handlers;
import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport;
import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport.TenantResolutionResult;

import com.ghatana.platform.observability.MetricsCollector;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * @doc.type class
 * @doc.purpose Regression tests for AgentCatalogHandler tenant enforcement
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AgentCatalogHandler")
@ExtendWith(MockitoExtension.class) 
class AgentCatalogHandlerTest extends EventloopTestBase {

    @Mock
    private HttpHandlerSupport http;

    @Mock
    private MetricsCollector metrics;

    @Mock
    private HttpRequest request;

    @Mock
    private HttpResponse errorResponse;

    private AgentCatalogHandler handler;

    @BeforeEach
    void setUp() { 
        handler = new AgentCatalogHandler(http, metrics); 
        when(http.errorResponse(anyInt(), anyString())).thenReturn(errorResponse); 
    }

    @Test
    @DisplayName("list rejects missing tenant before metrics or catalog work")
    void listRejectsMissingTenant() { 
        when(http.requireTenantIdWithError(request)).thenReturn(TenantResolutionResult.error(401, "Unauthorized")); 

        HttpResponse response = runPromise(() -> handler.handleListCatalog(request)); 

        assertThat(response).isSameAs(errorResponse); 
        verify(metrics, never()).incrementCounter("agent.catalog.list", "tenant", "default"); 
    }

    @Test
    @DisplayName("get rejects missing tenant before metrics or catalog work")
    void getRejectsMissingTenant() { 
        when(request.getPathParameter("id")).thenReturn("agent-1");
        when(http.requireTenantIdWithError(request)).thenReturn(TenantResolutionResult.error(401, "Unauthorized")); 

        HttpResponse response = runPromise(() -> handler.handleGetAgent(request)); 

        assertThat(response).isSameAs(errorResponse); 
        verify(metrics, never()).incrementCounter("agent.catalog.get", "tenant", "default", "agentId", "agent-1"); 
    }
}