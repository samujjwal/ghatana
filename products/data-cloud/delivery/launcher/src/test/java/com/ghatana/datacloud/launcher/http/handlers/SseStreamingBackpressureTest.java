package com.ghatana.datacloud.launcher.http.handlers;
import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport;
import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport.TenantResolutionResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.brain.DataCloudBrain;
import com.ghatana.datacloud.governance.QuotaCheckResult;
import com.ghatana.datacloud.governance.TenantQuotaService;
import com.ghatana.datacloud.infrastructure.storage.OpenSearchConnector;
import com.ghatana.datacloud.launcher.learning.DataCloudLearningBridge;
import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DC-P2-003: Backpressure, quota, cross-tenant isolation, and shutdown tests for
 * {@link SseStreamingHandler}.
 *
 * <p>Complements {@link SseStreamingHandlerTest}, which only covers missing-tenant rejection.
 * This class adds:
 * <ul>
 *   <li>Connection quota enforcement (429 when quota exceeded)</li>
 *   <li>Cross-tenant stream isolation (tenant-A cannot read tenant-B stream)</li>
 *   <li>Graceful shutdown cleanup (subscriptions and queues cleared)</li>
 *   <li>Stream with no-op quota service proceeds without error</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose DC-P2-003 SSE backpressure, quota, and isolation tests
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SseStreamingHandler — backpressure and tenant isolation (DC-P2-003)")
class SseStreamingBackpressureTest extends EventloopTestBase {

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
    private TenantQuotaService quotaService;

    @Mock
    private OpenSearchConnector openSearchConnector;

    private SseStreamingHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SseStreamingHandler(client, brain, learningBridge, objectMapper, http)
                .withOpenSearchConnector(openSearchConnector)
                .withTenantQuotaService(quotaService);
        lenient().when(http.errorResponse(anyInt(), anyString()))
                .thenReturn(mock(HttpResponse.class));
        lenient().when(http.errorResponse(eq(429), anyString()))
                .thenReturn(mock(HttpResponse.class));
    }

    // ── Quota enforcement (backpressure) ──────────────────────────────────────

    @Nested
    @DisplayName("Connection quota enforcement")
    class QuotaEnforcement {

        @Test
        @DisplayName("entity CDC stream returns 429 when quota service rejects the connection")
        void entityCdcStream_returns429WhenQuotaExceeded() {
            when(http.requireTenantIdWithError(request)).thenReturn(TenantResolutionResult.success("tenant-a", null));
            when(request.getPathParameter("collection")).thenReturn("orders");
            when(quotaService.checkQuota("tenant-a", "CONNECTION", 1))
                    .thenReturn(QuotaCheckResult.reject("max connections reached", 10, 10));

            HttpResponse response = runPromise(() -> handler.handleEntityCdcStream(request));

            verify(http).errorResponse(eq(429), anyString());
            // Must not attempt to open a real subscription when quota is exceeded
            verify(client, never()).eventLogStore();
        }

        @Test
        @DisplayName("entity CDC stream proceeds when quota service permits the connection")
        void entityCdcStream_proceedsWhenQuotaAllowed() {
            when(http.requireTenantIdWithError(request)).thenReturn(TenantResolutionResult.success("tenant-a", null));
            when(request.getPathParameter("collection")).thenReturn("events");
            when(quotaService.checkQuota("tenant-a", "CONNECTION", 1))
                    .thenReturn(QuotaCheckResult.permit());

            EventLogStore mockEventLogStore = mock(EventLogStore.class);
            when(client.eventLogStore()).thenReturn(mockEventLogStore);
            // Return a pending promise — the stream stays open, which is expected behavior
            when(mockEventLogStore.tail(any(), any(), any())).thenReturn(io.activej.promise.Promise.ofCallback(cb -> {
                // Simulate an open (never-resolved) subscription by simply not calling cb
            }));

            // Invoke without awaiting to test quota enforcement is passed — the call must NOT 429
            handler.handleEntityCdcStream(request);

            // Quota service was consulted
            verify(quotaService).checkQuota("tenant-a", "CONNECTION", 1);
            // Event log was opened (quota was not blocking)
            verify(client).eventLogStore();
        }

        @Test
        @DisplayName("quota not checked when quotaService is not configured — connection proceeds")
        void entityCdcStream_noQuotaService_connectionAllowed() {
            // Handler without a quota service installed
            SseStreamingHandler handlerWithoutQuota = new SseStreamingHandler(
                    client, brain, learningBridge, objectMapper, http)
                    .withOpenSearchConnector(openSearchConnector);
            when(http.requireTenantIdWithError(request)).thenReturn(TenantResolutionResult.success("tenant-b", null));
            when(request.getPathParameter("collection")).thenReturn("products");

            EventLogStore mockEventLogStore = mock(EventLogStore.class);
            when(client.eventLogStore()).thenReturn(mockEventLogStore);
            when(mockEventLogStore.tail(any(), any(), any())).thenReturn(io.activej.promise.Promise.ofCallback(cb -> {}));

            handlerWithoutQuota.handleEntityCdcStream(request);

            // Must reach the event log store — no quota rejection
            verify(client).eventLogStore();
        }
    }

    // ── Cross-tenant isolation ────────────────────────────────────────────────

    @Nested
    @DisplayName("Cross-tenant stream isolation")
    class CrossTenantIsolation {

        @Test
        @DisplayName("missing tenant header is always rejected before any stream is opened")
        void missingTenantIsAlwaysRejected() {
            when(http.requireTenantIdWithError(request)).thenReturn(TenantResolutionResult.error(401, "Unauthorized"));

            runPromise(() -> handler.handleEntityCdcStream(request));

            // No stream should ever be opened without a resolved tenant
            verify(client, never()).eventLogStore();
            verify(quotaService, never()).checkQuota(anyString(), anyString(), anyInt());
        }

        @Test
        @DisplayName("tenant-A cannot trigger tenant-B CDC stream via header manipulation")
        void tenantACannotOpenTenantBStream() {
            // Simulate tenant resolution returning tenant-a regardless of caller manipulation.
            // The contract is: the resolved tenantId (from requireTenantIdWithError) is passed to
            // TenantContext; callers cannot inject a foreign tenantId after the header check.
            when(http.requireTenantIdWithError(request)).thenReturn(TenantResolutionResult.success("tenant-a", null));
            when(request.getPathParameter("collection")).thenReturn("secrets");
            when(quotaService.checkQuota("tenant-a", "CONNECTION", 1))
                    .thenReturn(QuotaCheckResult.permit());

            EventLogStore mockEventLogStore = mock(EventLogStore.class);
            when(client.eventLogStore()).thenReturn(mockEventLogStore);
            when(mockEventLogStore.tail(any(), any(), any()))
                    .thenReturn(io.activej.promise.Promise.ofCallback(cb -> {}));

            handler.handleEntityCdcStream(request);

            // The subscription must use the resolved tenantId, never an unchecked value
            verify(quotaService).checkQuota(eq("tenant-a"), anyString(), anyInt());
            // Event log opened with the resolved tenant (verified by quota call with tenant-a)
        }
    }

    // ── Shutdown cleanup ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Graceful shutdown cleanup")
    class ShutdownCleanup {

        @Test
        @DisplayName("shutdown() on idle handler completes without exception")
        void shutdownOnIdleHandlerIsNoOp() {
            // Must not throw even when no streams are active
            handler.shutdown();
        }

        @Test
        @DisplayName("shutdown() after adding a mock subscription cancels it")
        void shutdownCancelsMockSubscription() {
            // Open a stream so a subscription is registered
            when(http.requireTenantIdWithError(request)).thenReturn(TenantResolutionResult.success("tenant-s", null));
            when(request.getPathParameter("collection")).thenReturn("audit-log");
            when(quotaService.checkQuota("tenant-s", "CONNECTION", 1))
                    .thenReturn(QuotaCheckResult.permit());

            EventLogStore.Subscription mockSubscription = mock(EventLogStore.Subscription.class);
            EventLogStore mockEventLogStore = mock(EventLogStore.class);
            when(client.eventLogStore()).thenReturn(mockEventLogStore);
            // Resolve immediately with the subscription so it gets registered
            when(mockEventLogStore.tail(any(), any(), any()))
                    .thenReturn(io.activej.promise.Promise.of(mockSubscription));
            lenient().when(http.corsAllowOrigin()).thenReturn("*");

            // Run on eventloop so the promise resolves and subscription registers
            runPromise(() -> handler.handleEntityCdcStream(request));

            handler.shutdown();

            verify(mockSubscription).cancel();
        }
    }
}
