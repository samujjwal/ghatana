package com.ghatana.datacloud.launcher.http.handlers;
import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport;
import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport.TenantResolutionResult;

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
 * @doc.purpose Regression tests for AlertingHandler tenant enforcement
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AlertingHandler")
@ExtendWith(MockitoExtension.class) 
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
    void setUp() { 
        handler = new AlertingHandler(client, http); 
        when(http.errorResponse(anyInt(), anyString())).thenReturn(errorResponse); 
    }

    @Test
    @DisplayName("list alerts rejects missing tenant before query access")
    void listAlertsRejectsMissingTenant() { 
        when(http.requireTenantIdWithError(request)).thenReturn(TenantResolutionResult.error(401, "Unauthorized")); 

        HttpResponse response = runPromise(() -> handler.handleListAlerts(request)); 

        assertThat(response).isSameAs(errorResponse); 
        verify(client, never()).query("default", AlertingHandler.ALERTS_COLLECTION, DataCloudClient.Query.limit(1)); 
    }

    @Test
    @DisplayName("create alert rule rejects missing tenant before loading body")
    void createAlertRuleRejectsMissingTenant() { 
        when(http.requireTenantIdWithError(request)).thenReturn(TenantResolutionResult.error(401, "Unauthorized")); 

        HttpResponse response = runPromise(() -> handler.handleCreateAlertRule(request)); 

        assertThat(response).isSameAs(errorResponse); 
        verify(request, never()).loadBody(); 
    }
}