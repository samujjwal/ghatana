package com.ghatana.datacloud.launcher.http.handlers;
import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport;
import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport.TenantResolutionResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.brain.DataCloudBrain;
import com.ghatana.datacloud.infrastructure.storage.OpenSearchConnector;
import com.ghatana.datacloud.launcher.learning.DataCloudLearningBridge;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * @doc.type class
 * @doc.purpose Regression tests for SseStreamingHandler tenant enforcement
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("SseStreamingHandler")
@ExtendWith(MockitoExtension.class) 
class SseStreamingHandlerTest extends EventloopTestBase {

    @Mock
    private DataCloudClient client;

    @Mock
    private DataCloudBrain brain;

    @Mock
    private DataCloudLearningBridge learningBridge;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private HttpHandlerSupport http;

    @Mock
    private HttpRequest request;

    @Mock
    private HttpResponse errorResponse;

    @Mock
    private OpenSearchConnector openSearchConnector;

    private SseStreamingHandler handler;

    @BeforeEach
    void setUp() { 
        handler = new SseStreamingHandler(client, brain, learningBridge, objectMapper, http) 
            .withOpenSearchConnector(openSearchConnector); 
        when(http.errorResponse(anyInt(), anyString())).thenReturn(errorResponse); 
    }

    @Test
    @DisplayName("entity CDC rejects missing tenant before event log access")
    void entityCdcRejectsMissingTenant() { 
        when(http.requireTenantIdWithError(request)).thenReturn(TenantResolutionResult.error(401, "Unauthorized")); 

        HttpResponse response = runPromise(() -> handler.handleEntityCdcStream(request)); 

        assertThat(response).isSameAs(errorResponse); 
        verify(client, never()).eventLogStore(); 
    }

    @Test
    @DisplayName("general SSE rejects missing tenant before event log access")
    void generalSseRejectsMissingTenant() { 
        when(http.requireTenantIdWithError(request)).thenReturn(TenantResolutionResult.error(401, "Unauthorized")); 

        HttpResponse response = runPromise(() -> handler.handleSseStream(request)); 

        assertThat(response).isSameAs(errorResponse); 
        verify(client, never()).eventLogStore(); 
    }

    @Test
    @DisplayName("brain workspace SSE rejects missing tenant before workspace access")
    void brainWorkspaceRejectsMissingTenant() { 
        when(http.requireTenantIdWithError(request)).thenReturn(TenantResolutionResult.error(401, "Unauthorized")); 

        HttpResponse response = runPromise(() -> handler.handleBrainWorkspaceStream(request)); 

        assertThat(response).isSameAs(errorResponse); 
        verify(brain, never()).getWorkspace(); 
    }

    @Test
    @DisplayName("learning SSE rejects missing tenant before stream setup")
    void learningStreamRejectsMissingTenant() { 
        when(http.requireTenantIdWithError(request)).thenReturn(TenantResolutionResult.error(401, "Unauthorized")); 

        HttpResponse response = runPromise(() -> handler.handleLearningStream(request)); 

        assertThat(response).isSameAs(errorResponse); 
        verify(learningBridge, never()).getStatus(); 
    }

    @Test
    @DisplayName("streaming query SSE rejects missing tenant before query execution")
    void streamingQueryRejectsMissingTenant() { 
        when(request.getPathParameter("collection")).thenReturn("orders");
        when(request.getQueryParameter("q")).thenReturn("status:open");
        when(http.requireTenantIdWithError(request)).thenReturn(TenantResolutionResult.error(401, "Unauthorized")); 

        HttpResponse response = runPromise(() -> handler.handleStreamingQuerySse(request)); 

        assertThat(response).isSameAs(errorResponse); 
        verifyNoInteractions(openSearchConnector); 
        verify(client, never()).eventLogStore(); 
    }
}