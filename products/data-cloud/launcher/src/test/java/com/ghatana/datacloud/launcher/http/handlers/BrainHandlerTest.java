package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.brain.DataCloudBrain;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Regression tests for BrainHandler tenant enforcement
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("BrainHandler")
@ExtendWith(MockitoExtension.class) // GH-90000
class BrainHandlerTest extends EventloopTestBase {

    @Mock
    private DataCloudBrain brain;

    @Mock
    private HttpHandlerSupport http;

    @Mock
    private HttpRequest request;

    @Mock
    private HttpResponse errorResponse;

    private BrainHandler handler;

    @BeforeEach
    void setUp() { // GH-90000
        handler = new BrainHandler(brain, http); // GH-90000
        when(http.errorResponse(400, "X-Tenant-Id header is required")).thenReturn(errorResponse); // GH-90000
    }

    @Test
    @DisplayName("brain stats rejects missing tenant before stats lookup")
    void brainStatsRejectsMissingTenant() { // GH-90000
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleBrainStats(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(brain, never()).getStats(any()); // GH-90000
    }

    @Test
    @DisplayName("attention elevate rejects missing tenant before manager or body access")
    void attentionElevateRejectsMissingTenant() { // GH-90000
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleBrainAttentionElevate(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(brain, never()).getAttentionManager(); // GH-90000
        verify(request, never()).loadBody(); // GH-90000
    }

    @Test
    @DisplayName("patterns rejects missing tenant before pattern lookup")
    void patternsRejectMissingTenant() { // GH-90000
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleBrainPatterns(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(brain, never()).listPatterns(anyInt(), any()); // GH-90000
    }

    @Test
    @DisplayName("pattern match rejects missing tenant before body access")
    void patternMatchRejectsMissingTenant() { // GH-90000
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleBrainPatternsMatch(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(request, never()).loadBody(); // GH-90000
    }
}