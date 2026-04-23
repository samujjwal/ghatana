package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.DataCloudClient;
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
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Regression tests for EventHandler tenant enforcement
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("EventHandler tenant enforcement")
@ExtendWith(MockitoExtension.class) // GH-90000
class EventHandlerTenantEnforcementTest extends EventloopTestBase {

    @Mock
    private DataCloudClient client;

    @Mock
    private HttpHandlerSupport http;

    @Mock
    private HttpRequest request;

    @Mock
    private HttpResponse errorResponse;

    private EventHandler handler;

    @BeforeEach
    void setUp() { // GH-90000
        handler = new EventHandler(client, http); // GH-90000
        when(http.errorResponse(400, "X-Tenant-Id header is required")).thenReturn(errorResponse); // GH-90000
    }

    @Test
    @DisplayName("append rejects missing tenant before reading body")
    void appendRejectsMissingTenantBeforeReadingBody() { // GH-90000
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleAppendEvent(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(request, never()).loadBody(); // GH-90000
        verify(client, never()).appendEvent(any(), any()); // GH-90000
    }

    @Test
    @DisplayName("query rejects missing tenant before querying events")
    void queryRejectsMissingTenantBeforeQueryingEvents() { // GH-90000
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleQueryEvents(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(client, never()).queryEvents(any(), any()); // GH-90000
    }

    @Test
    @DisplayName("get-by-offset rejects missing tenant before querying events")
    void getByOffsetRejectsMissingTenantBeforeQueryingEvents() { // GH-90000
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleGetEventByOffset(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(client, never()).queryEvents(any(), any()); // GH-90000
    }
}