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
 * @doc.purpose Regression tests for AlertingHandler tenant enforcement
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AlertingHandler [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class AlertingHandlerTest extends EventloopTestBase {

    @Mock
    private DataCloudClient client;

    @Mock
    private HttpHandlerSupport http;

    @Mock
    private HttpRequest request;

    @Mock
    private HttpResponse errorResponse;

    private AlertingHandler handler;

    @BeforeEach
    void setUp() { // GH-90000
        handler = new AlertingHandler(client, http); // GH-90000
        when(http.errorResponse(400, "X-Tenant-Id header is required")).thenReturn(errorResponse); // GH-90000
    }

    @Test
    @DisplayName("list alerts rejects missing tenant before query access [GH-90000]")
    void listAlertsRejectsMissingTenant() { // GH-90000
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleListAlerts(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(client, never()).query("default", AlertingHandler.ALERTS_COLLECTION, DataCloudClient.Query.limit(1)); // GH-90000
    }

    @Test
    @DisplayName("create alert rule rejects missing tenant before loading body [GH-90000]")
    void createAlertRuleRejectsMissingTenant() { // GH-90000
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleCreateAlertRule(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(request, never()).loadBody(); // GH-90000
    }
}