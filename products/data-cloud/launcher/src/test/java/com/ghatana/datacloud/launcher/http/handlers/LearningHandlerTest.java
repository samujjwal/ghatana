package com.ghatana.datacloud.launcher.http.handlers;

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
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Regression tests for LearningHandler tenant enforcement
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("LearningHandler")
@ExtendWith(MockitoExtension.class) // GH-90000
class LearningHandlerTest extends EventloopTestBase {

    @Mock
    private DataCloudLearningBridge learningBridge;

    @Mock
    private HttpHandlerSupport http;

    @Mock
    private HttpRequest request;

    @Mock
    private HttpResponse errorResponse;

    private LearningHandler handler;

    @BeforeEach
    void setUp() { // GH-90000
        handler = new LearningHandler(learningBridge, http); // GH-90000
        when(http.errorResponse(400, "X-Tenant-Id header is required")).thenReturn(errorResponse); // GH-90000
    }

    @Test
    @DisplayName("learning trigger rejects missing tenant before bridge execution")
    void learningTriggerRejectsMissingTenant() { // GH-90000
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleLearningTrigger(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(learningBridge, never()).runLearning("default", true); // GH-90000
    }
}