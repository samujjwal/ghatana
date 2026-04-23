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
import static org.mockito.Mockito.lenient;
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
@ExtendWith(MockitoExtension.class) // GH-90000
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
    void setUp() { // GH-90000
        handler = new AiModelHandler(aiModelManager, featureStoreService, http); // GH-90000
        lenient().when(http.errorResponse(400, "X-Tenant-Id header is required")).thenReturn(errorResponse); // GH-90000
        lenient().when(http.objectMapper()).thenReturn(objectMapper); // GH-90000
    }

    @Test
    @DisplayName("list models rejects missing tenant before manager access")
    void listModelsRejectsMissingTenant() { // GH-90000
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleListAiModels(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(aiModelManager, never()).getAllModels("default");
    }

    @Test
    @DisplayName("register model rejects missing tenant before loading body")
    void registerModelRejectsMissingTenant() { // GH-90000
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleRegisterAiModel(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(request, never()).loadBody(); // GH-90000
    }

    @Test
    @DisplayName("promote model rejects missing tenant before loading body")
    void promoteModelRejectsMissingTenant() { // GH-90000
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handlePromoteAiModel(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(request, never()).loadBody(); // GH-90000
    }

    @Test
    @DisplayName("ingest feature rejects missing tenant before loading body")
    void ingestFeatureRejectsMissingTenant() { // GH-90000
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleIngestFeature(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(request, never()).loadBody(); // GH-90000
    }

    @Test
    @DisplayName("get features rejects missing tenant before feature lookup")
    void getFeaturesRejectsMissingTenant() { // GH-90000
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleGetFeatures(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(featureStoreService, never()).getFeatures("default", "default", java.util.List.of()); // GH-90000
    }
}