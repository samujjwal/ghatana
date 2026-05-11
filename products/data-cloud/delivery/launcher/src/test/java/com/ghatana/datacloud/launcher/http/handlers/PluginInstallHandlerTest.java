package com.ghatana.datacloud.launcher.http.handlers;
import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport;
import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport.TenantResolutionResult;

import com.ghatana.datacloud.launcher.http.plugins.DataCloudRuntimePluginManager;
import com.ghatana.datacloud.spi.StoragePluginRegistry;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Regression tests for PluginInstallHandler tenant enforcement
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PluginInstallHandler")
@ExtendWith(MockitoExtension.class) 
class PluginInstallHandlerTest extends EventloopTestBase {

    @Mock
    private HttpHandlerSupport http;

    @Mock
    private StoragePluginRegistry pluginRegistry;

    @Mock
    private DataCloudRuntimePluginManager runtimePluginManager;

    @Mock
    private MetricsCollector metrics;

    @Mock
    private HttpRequest request;

    @Mock
    private HttpResponse errorResponse;

    private PluginInstallHandler handler;

    @BeforeEach
    void setUp() { 
        handler = new PluginInstallHandler(http, pluginRegistry, runtimePluginManager, metrics); 
        when(http.errorResponse(anyInt(), anyString())).thenReturn(errorResponse); 
        lenient().when(http.errorResponse(anyInt(), anyString())) 
            .thenReturn(errorResponse); 
    }

    @Test
    @DisplayName("list rejects missing tenant before plugin registry access")
    void listRejectsMissingTenant() { 
        when(http.requireTenantIdWithError(request)).thenReturn(TenantResolutionResult.error(401, "Unauthorized")); 

        HttpResponse response = runPromise(() -> handler.handleListPlugins(request)); 

        assertThat(response).isSameAs(errorResponse); 
        verify(pluginRegistry, never()).getAllPlugins(); 
        verify(runtimePluginManager, never()).getAllPlugins(); 
    }

    @Test
    @DisplayName("get rejects missing tenant before plugin lookup")
    void getRejectsMissingTenant() { 
        when(request.getPathParameter("id")).thenReturn("plugin-1");
        when(http.requireTenantIdWithError(request)).thenReturn(TenantResolutionResult.error(401, "Unauthorized")); 

        HttpResponse response = runPromise(() -> handler.handleGetPlugin(request)); 

        assertThat(response).isSameAs(errorResponse); 
        verify(pluginRegistry, never()).getPlugin("plugin-1");
        verify(runtimePluginManager, never()).getPlugin("plugin-1");
    }

    @Test
    @DisplayName("upgrade rejects missing tenant before reading body")
    void upgradeRejectsMissingTenant() { 
        // Enable upgrade path so tenant validation is exercised before body loading.
        handler.withPluginUpgradeEnabled(true); 
        when(request.getPathParameter("id")).thenReturn("plugin-1");
        when(http.requireTenantIdWithError(request)).thenReturn(TenantResolutionResult.error(401, "Unauthorized")); 

        HttpResponse response = runPromise(() -> handler.handleUpgradePlugin(request)); 

        assertThat(response).isSameAs(errorResponse); 
        verify(request, never()).loadBody(); 
    }
}