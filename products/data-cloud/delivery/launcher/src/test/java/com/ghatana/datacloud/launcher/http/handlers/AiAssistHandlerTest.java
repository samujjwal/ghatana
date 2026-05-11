package com.ghatana.datacloud.launcher.http.handlers;
import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport;
import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport.TenantResolutionResult;

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
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Regression tests for AiAssistHandler tenant enforcement
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AiAssistHandler")
@ExtendWith(MockitoExtension.class) 
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
    void setUp() { 
        handler = new AiAssistHandler(completionService, objectMapper, http, blockingExecutor, AiRecommendationMetrics.NOOP); 
        when(http.errorResponse(anyInt(), anyString())).thenReturn(errorResponse);
    }

    @Test
    @DisplayName("entity suggest rejects missing tenant before loading body")
    void entitySuggestRejectsMissingTenant() { 
        when(http.requireTenantIdWithError(request)).thenReturn(TenantResolutionResult.error(401, "Unauthorized")); 

        HttpResponse response = runPromise(() -> handler.handleEntitySuggest(request)); 

        assertThat(response).isSameAs(errorResponse); 
        verify(request, never()).loadBody(4096); 
    }

    @Test
    @DisplayName("analytics suggest rejects missing tenant before loading body")
    void analyticsSuggestRejectsMissingTenant() { 
        when(http.requireTenantIdWithError(request)).thenReturn(TenantResolutionResult.error(401, "Unauthorized")); 

        HttpResponse response = runPromise(() -> handler.handleAnalyticsSuggest(request)); 

        assertThat(response).isSameAs(errorResponse); 
        verify(request, never()).loadBody(4096); 
    }

    @Test
    @DisplayName("pipeline hint rejects missing tenant before loading body")
    void pipelineHintRejectsMissingTenant() { 
        when(http.requireTenantIdWithError(request)).thenReturn(TenantResolutionResult.error(401, "Unauthorized")); 

        HttpResponse response = runPromise(() -> handler.handlePipelineOptimiseHint(request)); 

        assertThat(response).isSameAs(errorResponse); 
        verify(request, never()).loadBody(4096); 
    }

    @Test
    @DisplayName("pipeline draft rejects missing tenant before loading body")
    void pipelineDraftRejectsMissingTenant() { 
        when(http.requireTenantIdWithError(request)).thenReturn(TenantResolutionResult.error(401, "Unauthorized")); 

        HttpResponse response = runPromise(() -> handler.handlePipelineDraft(request)); 

        assertThat(response).isSameAs(errorResponse); 
        verify(request, never()).loadBody(4096); 
    }

    @Test
    @DisplayName("brain explain rejects missing tenant before loading body")
    void brainExplainRejectsMissingTenant() { 
        when(http.requireTenantIdWithError(request)).thenReturn(TenantResolutionResult.error(401, "Unauthorized")); 

        HttpResponse response = runPromise(() -> handler.handleBrainExplain(request)); 

        assertThat(response).isSameAs(errorResponse); 
        verify(request, never()).loadBody(4096); 
    }
}