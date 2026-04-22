package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.datacloud.launcher.ai.AiRecommendationMetrics;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import java.util.concurrent.Executor;
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
 * @doc.purpose Regression tests for AiAssistHandler tenant enforcement
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AiAssistHandler [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class AiAssistHandlerTest extends EventloopTestBase {

    @Mock
    private CompletionService completionService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private HttpHandlerSupport http;

    @Mock
    private Executor blockingExecutor;

    @Mock
    private HttpRequest request;

    @Mock
    private HttpResponse errorResponse;

    private AiAssistHandler handler;

    @BeforeEach
    void setUp() { // GH-90000
        handler = new AiAssistHandler(completionService, objectMapper, http, blockingExecutor, AiRecommendationMetrics.NOOP); // GH-90000
        when(http.errorResponse(400, "X-Tenant-Id header is required")).thenReturn(errorResponse); // GH-90000
    }

    @Test
    @DisplayName("entity suggest rejects missing tenant before loading body [GH-90000]")
    void entitySuggestRejectsMissingTenant() { // GH-90000
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleEntitySuggest(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(request, never()).loadBody(4096); // GH-90000
    }

    @Test
    @DisplayName("analytics suggest rejects missing tenant before loading body [GH-90000]")
    void analyticsSuggestRejectsMissingTenant() { // GH-90000
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleAnalyticsSuggest(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(request, never()).loadBody(4096); // GH-90000
    }

    @Test
    @DisplayName("pipeline hint rejects missing tenant before loading body [GH-90000]")
    void pipelineHintRejectsMissingTenant() { // GH-90000
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handlePipelineOptimiseHint(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(request, never()).loadBody(4096); // GH-90000
    }

    @Test
    @DisplayName("pipeline draft rejects missing tenant before loading body [GH-90000]")
    void pipelineDraftRejectsMissingTenant() { // GH-90000
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handlePipelineDraft(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(request, never()).loadBody(4096); // GH-90000
    }

    @Test
    @DisplayName("brain explain rejects missing tenant before loading body [GH-90000]")
    void brainExplainRejectsMissingTenant() { // GH-90000
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleBrainExplain(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(request, never()).loadBody(4096); // GH-90000
    }
}