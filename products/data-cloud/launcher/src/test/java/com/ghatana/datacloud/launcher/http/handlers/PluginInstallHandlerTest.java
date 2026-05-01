package com.ghatana.datacloud.launcher.http.handlers;

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
@ExtendWith(MockitoExtension.class) // GH-90000
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
    void setUp() { // GH-90000
        handler = new PluginInstallHandler(http, pluginRegistry, runtimePluginManager, metrics); // GH-90000
        when(http.errorResponse(400, "X-Tenant-Id header is required")).thenReturn(errorResponse); // GH-90000
        lenient().when(http.errorResponse(501, "Plugin hot-swap upgrade is not enabled on this instance")) // GH-90000
            .thenReturn(errorResponse); // GH-90000
    }

    @Test
    @DisplayName("list rejects missing tenant before plugin registry access")
    void listRejectsMissingTenant() { // GH-90000
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleListPlugins(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(pluginRegistry, never()).getAllPlugins(); // GH-90000
        verify(runtimePluginManager, never()).getAllPlugins(); // GH-90000
    }

    @Test
    @DisplayName("get rejects missing tenant before plugin lookup")
    void getRejectsMissingTenant() { // GH-90000
        when(request.getPathParameter("id")).thenReturn("plugin-1");
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleGetPlugin(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(pluginRegistry, never()).getPlugin("plugin-1");
        verify(runtimePluginManager, never()).getPlugin("plugin-1");
    }

    @Test
    @DisplayName("upgrade rejects missing tenant before reading body")
    void upgradeRejectsMissingTenant() { // GH-90000
        // Enable upgrade path so tenant validation is exercised before body loading.
        handler.withPluginUpgradeEnabled(true); // GH-90000
        when(request.getPathParameter("id")).thenReturn("plugin-1");
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleUpgradePlugin(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(request, never()).loadBody(); // GH-90000
    }
}