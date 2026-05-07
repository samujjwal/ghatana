package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.client.autonomy.AutonomyController;
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
 * @doc.purpose Regression tests for AutonomyHandler tenant enforcement
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AutonomyHandler")
@ExtendWith(MockitoExtension.class) 
class AutonomyHandlerTest extends EventloopTestBase {

    @Mock
    private AutonomyController autonomyController;

    @Mock
    private HttpHandlerSupport http;

    @Mock
    private HttpRequest request;

    @Mock
    private HttpResponse errorResponse;

    private AutonomyHandler handler;

    @BeforeEach
    void setUp() { 
        handler = new AutonomyHandler(autonomyController, http); 
        when(http.errorResponse(400, "X-Tenant-Id header is required")).thenReturn(errorResponse); 
    }

    @Test
    @DisplayName("set global level rejects missing tenant before loading body")
    void setGlobalLevelRejectsMissingTenant() { 
        when(http.requireTenantIdOrFail(request)).thenReturn(null); 

        HttpResponse response = runPromise(() -> handler.handleSetGlobalLevel(request)); 

        assertThat(response).isSameAs(errorResponse); 
        verify(request, never()).loadBody(); 
    }

    @Test
    @DisplayName("get global level rejects missing tenant before controller access")
    void getGlobalLevelRejectsMissingTenant() { 
        when(http.requireTenantIdOrFail(request)).thenReturn(null); 

        HttpResponse response = runPromise(() -> handler.handleGetGlobalLevel(request)); 

        assertThat(response).isSameAs(errorResponse); 
        verify(autonomyController, never()).listAllStates("default");
    }

    @Test
    @DisplayName("list domains rejects missing tenant before controller access")
    void listDomainsRejectsMissingTenant() { 
        when(http.requireTenantIdOrFail(request)).thenReturn(null); 

        HttpResponse response = runPromise(() -> handler.handleListDomains(request)); 

        assertThat(response).isSameAs(errorResponse); 
        verify(autonomyController, never()).listAllStates("default");
    }
}