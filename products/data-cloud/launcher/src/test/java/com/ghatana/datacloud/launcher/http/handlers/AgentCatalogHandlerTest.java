package com.ghatana.datacloud.launcher.http.handlers;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Regression tests for AgentCatalogHandler tenant enforcement
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AgentCatalogHandler [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
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
    void setUp() { // GH-90000
        handler = new AgentCatalogHandler(http, metrics); // GH-90000
        when(http.errorResponse(400, "X-Tenant-Id header is required")).thenReturn(errorResponse); // GH-90000
    }

    @Test
    @DisplayName("list rejects missing tenant before metrics or catalog work [GH-90000]")
    void listRejectsMissingTenant() { // GH-90000
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleListCatalog(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(metrics, never()).incrementCounter("agent.catalog.list", "tenant", "default"); // GH-90000
    }

    @Test
    @DisplayName("get rejects missing tenant before metrics or catalog work [GH-90000]")
    void getRejectsMissingTenant() { // GH-90000
        when(request.getPathParameter("id [GH-90000]")).thenReturn("agent-1 [GH-90000]");
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleGetAgent(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(metrics, never()).incrementCounter("agent.catalog.get", "tenant", "default", "agentId", "agent-1"); // GH-90000
    }
}