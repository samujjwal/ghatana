package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.aiplatform.featurestore.FeatureStoreService;
import com.ghatana.datacloud.ai.AIModelManager;
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
 * @doc.purpose Regression tests for AiModelHandler tenant enforcement
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AiModelHandler")
@ExtendWith(MockitoExtension.class)
class AiModelHandlerTest extends EventloopTestBase {

    @Mock
    private AIModelManager aiModelManager;

    @Mock
    private FeatureStoreService featureStoreService;

    @Mock
    private HttpHandlerSupport http;

    @Mock
    private HttpRequest request;

    @Mock
    private HttpResponse errorResponse;

    @Mock
    private ObjectMapper objectMapper;

    private AiModelHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AiModelHandler(aiModelManager, featureStoreService, http);
        when(http.errorResponse(400, "X-Tenant-Id header is required")).thenReturn(errorResponse);
        when(http.objectMapper()).thenReturn(objectMapper);
    }

    @Test
    @DisplayName("list models rejects missing tenant before manager access")
    void listModelsRejectsMissingTenant() {
        when(http.requireTenantIdOrFail(request)).thenReturn(null);

        HttpResponse response = runPromise(() -> handler.handleListAiModels(request));

        assertThat(response).isSameAs(errorResponse);
        verify(aiModelManager, never()).getAllModels("default");
    }

    @Test
    @DisplayName("register model rejects missing tenant before loading body")
    void registerModelRejectsMissingTenant() {
        when(http.requireTenantIdOrFail(request)).thenReturn(null);

        HttpResponse response = runPromise(() -> handler.handleRegisterAiModel(request));

        assertThat(response).isSameAs(errorResponse);
        verify(request, never()).loadBody();
    }

    @Test
    @DisplayName("promote model rejects missing tenant before loading body")
    void promoteModelRejectsMissingTenant() {
        when(http.requireTenantIdOrFail(request)).thenReturn(null);

        HttpResponse response = runPromise(() -> handler.handlePromoteAiModel(request));

        assertThat(response).isSameAs(errorResponse);
        verify(request, never()).loadBody();
    }

    @Test
    @DisplayName("ingest feature rejects missing tenant before loading body")
    void ingestFeatureRejectsMissingTenant() {
        when(http.requireTenantIdOrFail(request)).thenReturn(null);

        HttpResponse response = runPromise(() -> handler.handleIngestFeature(request));

        assertThat(response).isSameAs(errorResponse);
        verify(request, never()).loadBody();
    }

    @Test
    @DisplayName("get features rejects missing tenant before feature lookup")
    void getFeaturesRejectsMissingTenant() {
        when(http.requireTenantIdOrFail(request)).thenReturn(null);

        HttpResponse response = runPromise(() -> handler.handleGetFeatures(request));

        assertThat(response).isSameAs(errorResponse);
        verify(featureStoreService, never()).getFeatures("default", "default", java.util.List.of());
    }
}