package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.plugins.vector.VectorMemoryPlugin;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Regression tests for SemanticSearchHandler tenant enforcement
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("SemanticSearchHandler")
@ExtendWith(MockitoExtension.class)
class SemanticSearchHandlerTest extends EventloopTestBase {

    @Mock
    private VectorMemoryPlugin vectorPlugin;

    @Mock
    private DataCloudClient client;

    @Mock
    private HttpHandlerSupport http;

    @Mock
    private HttpRequest request;

    @Mock
    private HttpResponse errorResponse;

    private SemanticSearchHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SemanticSearchHandler(vectorPlugin, client, http, new ObjectMapper());
        when(http.errorResponse(400, "X-Tenant-Id header is required")).thenReturn(errorResponse);
    }

    @Test
    @DisplayName("similar-entities rejects missing tenant before vector lookup")
    void similarEntitiesRejectsMissingTenant() {
        when(http.requireTenantIdOrFail(request)).thenReturn(null);

        HttpResponse response = runPromise(() -> handler.handleSimilarEntities(request));

        assertThat(response).isSameAs(errorResponse);
        verify(vectorPlugin, never()).findSimilar(any(), any(Integer.class), any(Boolean.class), any());
    }

    @Test
    @DisplayName("collection rag rejects missing tenant before reading body")
    void collectionRagRejectsMissingTenant() {
        when(http.requireTenantIdOrFail(request)).thenReturn(null);

        HttpResponse response = runPromise(() -> handler.handleCollectionRag(request));

        assertThat(response).isSameAs(errorResponse);
        verify(request, never()).loadBody();
        verify(vectorPlugin, never()).search(any());
    }
}