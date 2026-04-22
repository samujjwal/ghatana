package com.ghatana.datacloud.launcher.http.handlers;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Regression tests for SseStreamingHandler tenant enforcement
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("SseStreamingHandler [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
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
    void setUp() { // GH-90000
        handler = new SseStreamingHandler(client, brain, learningBridge, objectMapper, http) // GH-90000
            .withOpenSearchConnector(openSearchConnector); // GH-90000
        when(http.errorResponse(400, "X-Tenant-Id header is required")).thenReturn(errorResponse); // GH-90000
    }

    @Test
    @DisplayName("entity CDC rejects missing tenant before event log access [GH-90000]")
    void entityCdcRejectsMissingTenant() { // GH-90000
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleEntityCdcStream(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(client, never()).eventLogStore(); // GH-90000
    }

    @Test
    @DisplayName("general SSE rejects missing tenant before event log access [GH-90000]")
    void generalSseRejectsMissingTenant() { // GH-90000
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleSseStream(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(client, never()).eventLogStore(); // GH-90000
    }

    @Test
    @DisplayName("brain workspace SSE rejects missing tenant before workspace access [GH-90000]")
    void brainWorkspaceRejectsMissingTenant() { // GH-90000
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleBrainWorkspaceStream(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(brain, never()).getWorkspace(); // GH-90000
    }

    @Test
    @DisplayName("learning SSE rejects missing tenant before stream setup [GH-90000]")
    void learningStreamRejectsMissingTenant() { // GH-90000
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleLearningStream(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(learningBridge, never()).getStatus(); // GH-90000
    }

    @Test
    @DisplayName("streaming query SSE rejects missing tenant before query execution [GH-90000]")
    void streamingQueryRejectsMissingTenant() { // GH-90000
        when(request.getPathParameter("collection [GH-90000]")).thenReturn("orders [GH-90000]");
        when(request.getQueryParameter("q [GH-90000]")).thenReturn("status:open [GH-90000]");
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleStreamingQuerySse(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verifyNoInteractions(openSearchConnector); // GH-90000
        verify(client, never()).eventLogStore(); // GH-90000
    }
}