package com.ghatana.yappc.services.lifecycle;

import com.ghatana.aep.event.EventCloud;
import com.ghatana.orchestrator.subsys.TriggerListener;
import com.ghatana.yappc.services.lifecycle.dlq.DlqPublisher;
import com.ghatana.yappc.services.lifecycle.operators.PhaseTransitionValidatorOperator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.promise.Promise;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

@DisplayName("YAPPC AEP Pipeline E2E Tests (Item 7.2)")
class YappcAepPipelineE2eTest {

    private static final String PIPELINE_ID = "lifecycle-management-v1";
    private static final String PHASE_TRANSITION_REQUESTED = "phase.transition.requested";

    @Mock
    private TriggerListener mockTriggerListener;

    @Mock
    private EventCloud mockEventCloud;

    @Mock
    private YappcAepPipelineBootstrapper mockPipelineBootstrapper;

    @Mock
    private DlqPublisher mockDlqPublisher;

    @Mock
    private PhaseTransitionValidatorOperator mockValidator;

    private ObjectMapper objectMapper;
    private TriggerListenerBootstrap triggerListenerBootstrap;

    private EventCloud.Subscription mockSubscription;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();

        // Setup mock subscription
        mockSubscription = mock(EventCloud.Subscription.class);
        when(mockSubscription.isCancelled()).thenReturn(false);

        // Setup EventCloud to store handler and allow manual triggering
        AtomicReference<EventCloud.EventHandler> handlerRef = new AtomicReference<>();
        when(mockEventCloud.subscribe(
                nullable(String.class),
                eq(PHASE_TRANSITION_REQUESTED),
                any(EventCloud.EventHandler.class)))
            .thenAnswer(inv -> {
                EventCloud.EventHandler handler = inv.getArgument(2);
                handlerRef.set(handler);
                return mockSubscription;
            });

        // Setup TriggerListener to succeed
        when(mockTriggerListener.handlePatternMatch(any(), any(), any(), any()))
            .thenReturn(Promise.complete());

        // Create bootstrap with test tenant
        triggerListenerBootstrap = new TriggerListenerBootstrap(
            mockTriggerListener,
            mockEventCloud,
            mockPipelineBootstrapper,
            mockDlqPublisher,
            objectMapper,
            List.of("test-tenant")
        );
    }

    @Nested
    @DisplayName("Bootstrap Lifecycle")
    class BootstrapLifecycleTests {

        @Test
        @DisplayName("Should start successfully and subscribe to events")
        void shouldStartSuccessfully() {
            Promise<Void> result = triggerListenerBootstrap.start();

            assertThat(result.isComplete()).isTrue();
            assertThat(triggerListenerBootstrap.isRunning()).isTrue();
            assertThat(triggerListenerBootstrap.getSubscriptionCount()).isEqualTo(1);

            verify(mockEventCloud, times(1)).subscribe(
                nullable(String.class),
                eq(PHASE_TRANSITION_REQUESTED),
                any(EventCloud.EventHandler.class)
            );
        }

        @Test
        @DisplayName("Should be idempotent on multiple start() calls")
        void shouldBeIdempotentOnMultipleStarts() {
            triggerListenerBootstrap.start();
            triggerListenerBootstrap.start();
            triggerListenerBootstrap.start();

            assertThat(triggerListenerBootstrap.isRunning()).isTrue();
            assertThat(triggerListenerBootstrap.getSubscriptionCount()).isEqualTo(1);

            verify(mockEventCloud, times(1)).subscribe(
                nullable(String.class),
                any(String.class),
                any(EventCloud.EventHandler.class)
            );
        }

        @Test
        @DisplayName("Should stop successfully and cancel subscriptions")
        void shouldStopSuccessfully() {
            triggerListenerBootstrap.start();
            assertThat(triggerListenerBootstrap.isRunning()).isTrue();

            Promise<Void> result = triggerListenerBootstrap.stop();

            assertThat(result.isComplete()).isTrue();
            assertThat(triggerListenerBootstrap.isRunning()).isFalse();
            assertThat(triggerListenerBootstrap.getSubscriptionCount()).isEqualTo(0);

            verify(mockSubscription, times(1)).cancel();
        }

        @Test
        @DisplayName("Should be idempotent on multiple stop() calls")
        void shouldBeIdempotentOnMultipleStops() {
            triggerListenerBootstrap.start();
            triggerListenerBootstrap.stop();
            triggerListenerBootstrap.stop();
            triggerListenerBootstrap.stop();

            assertThat(triggerListenerBootstrap.isRunning()).isFalse();

            verify(mockSubscription, times(1)).cancel();
        }
    }

    @Nested
    @DisplayName("Event Processing")
    class EventProcessingTests {

        @Test
        @DisplayName("Should process valid phase transition event")
        void shouldProcessValidEvent() throws Exception {
            triggerListenerBootstrap.start();

            // Simulate receiving event from event cloud
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("tenantId", "tenant-123");
            eventData.put("projectId", "proj-456");
            eventData.put("currentPhase", "DESIGN");
            eventData.put("targetPhase", "PLANNING");
            eventData.put("correlationId", "corr-789");

            byte[] payload = objectMapper.writeValueAsBytes(eventData);

            // Manually trigger the handler (simulating EventCloud subscription)
            String eventId = "evt-001";
            EventCloud.EventHandler handler = captureEventHandler();
            handler.handle(eventId, PHASE_TRANSITION_REQUESTED, payload);

            // Verify TriggerListener was called
            verify(mockTriggerListener, times(1)).handlePatternMatch(
                eq("tenant-123"),
                eq(PIPELINE_ID),
                eq(eventId),
                argThat(data -> data instanceof Map && ((Map<?, ?>) data).containsKey("projectId"))
            );
        }

        @Test
        @DisplayName("Should handle null payload and publish to DLQ")
        void shouldHandleNullPayload() {
            triggerListenerBootstrap.start();

            EventCloud.EventHandler handler = captureEventHandler();
            String eventId = "evt-null";

            handler.handle(eventId, PHASE_TRANSITION_REQUESTED, null);

            verify(mockDlqPublisher, times(1)).publishErrorEvent(
                eq(eventId),
                eq(PHASE_TRANSITION_REQUESTED),
                eq("EMPTY_PAYLOAD"),
                any(String.class)
            );
        }

        @Test
        @DisplayName("Should handle empty payload and publish to DLQ")
        void shouldHandleEmptyPayload() {
            triggerListenerBootstrap.start();

            EventCloud.EventHandler handler = captureEventHandler();
            String eventId = "evt-empty";

            handler.handle(eventId, PHASE_TRANSITION_REQUESTED, new byte[0]);

            verify(mockDlqPublisher, times(1)).publishErrorEvent(
                eq(eventId),
                eq(PHASE_TRANSITION_REQUESTED),
                eq("EMPTY_PAYLOAD"),
                any(String.class)
            );
        }

        @Test
        @DisplayName("Should handle malformed JSON payload and publish to DLQ")
        void shouldHandleMalformedJson() {
            triggerListenerBootstrap.start();

            EventCloud.EventHandler handler = captureEventHandler();
            String eventId = "evt-malformed";
            byte[] malformedPayload = "{invalid json".getBytes();

            handler.handle(eventId, PHASE_TRANSITION_REQUESTED, malformedPayload);

            verify(mockDlqPublisher, times(1)).publishErrorEvent(
                eq(eventId),
                eq(PHASE_TRANSITION_REQUESTED),
                eq("PROCESSING_ERROR"),
                any(String.class)
            );
        }

        @Test
        @DisplayName("Should handle pipeline processing errors and publish to DLQ")
        void shouldHandlePipelineErrors() throws Exception {
            // Setup TriggerListener to fail
            when(mockTriggerListener.handlePatternMatch(any(), any(), any(), any()))
                .thenReturn(Promise.ofException(new RuntimeException("Pipeline error")));

            triggerListenerBootstrap.start();

            Map<String, Object> eventData = new HashMap<>();
            eventData.put("tenantId", "tenant-error");
            eventData.put("projectId", "proj-error");
            byte[] payload = objectMapper.writeValueAsBytes(eventData);

            EventCloud.EventHandler handler = captureEventHandler();
            String eventId = "evt-error";
            handler.handle(eventId, PHASE_TRANSITION_REQUESTED, payload);

            // Small delay to allow async completion to be processed
            Thread.sleep(100);

            verify(mockDlqPublisher, times(1)).publishErrorEvent(
                eq(eventId),
                eq(PHASE_TRANSITION_REQUESTED),
                eq("PIPELINE_ERROR"),
                any(String.class)
            );
        }
    }

    @Nested
    @DisplayName("Multi-Event Processing")
    class MultiEventProcessingTests {

        @Test
        @DisplayName("Should handle multiple concurrent events")
        void shouldHandleMultipleConcurrentEvents() throws Exception {
            triggerListenerBootstrap.start();

            AtomicInteger successCount = new AtomicInteger(0);
            when(mockTriggerListener.handlePatternMatch(any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    successCount.incrementAndGet();
                    return Promise.complete();
                });

            EventCloud.EventHandler handler = captureEventHandler();

            // Send 10 concurrent events
            ExecutorService executor = Executors.newFixedThreadPool(5);
            List<Future<?>> futures = new ArrayList<>();

            for (int i = 0; i < 10; i++) {
                final int index = i;
                futures.add(executor.submit(() -> {
                    try {
                        Map<String, Object> eventData = new HashMap<>();
                        eventData.put("tenantId", "tenant-" + index);
                        eventData.put("projectId", "proj-" + index);
                        byte[] payload = objectMapper.writeValueAsBytes(eventData);

                        handler.handle("evt-" + index, PHASE_TRANSITION_REQUESTED, payload);
                    } catch (Exception e) {
                        fail("Event processing failed", e);
                    }
                }));
            }

            // Wait for all events to complete
            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }

            executor.shutdown();

            // Verify all events were processed
            assertThat(successCount.get()).isEqualTo(10);
            verify(mockTriggerListener, times(10)).handlePatternMatch(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should maintain subscription after processing multiple events")
        void shouldMaintainSubscriptionAfterMultipleEvents() throws Exception {
            triggerListenerBootstrap.start();
            EventCloud.EventHandler handler = captureEventHandler();

            Map<String, Object> eventData = new HashMap<>();
            eventData.put("tenantId", "tenant-multi");
            byte[] payload = objectMapper.writeValueAsBytes(eventData);

            // Send multiple events
            for (int i = 0; i < 5; i++) {
                handler.handle("evt-multi-" + i, PHASE_TRANSITION_REQUESTED, payload);
            }

            // Verify subscription is still active
            assertThat(triggerListenerBootstrap.isRunning()).isTrue();
            assertThat(triggerListenerBootstrap.getSubscriptionCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Should execute full flow: start → subscribe → process → stop")
        void shouldExecuteFullFlow() throws Exception {
            // GIVEN: Bootstrap not started
            assertThat(triggerListenerBootstrap.isRunning()).isFalse();

            // WHEN: Start bootstrap
            triggerListenerBootstrap.start();

            // THEN: Should be subscribed
            assertThat(triggerListenerBootstrap.isRunning()).isTrue();

            // GIVEN: Prepare phase transition event
            Map<String, Object> event = new HashMap<>();
            event.put("tenantId", "tenant-full-flow");
            event.put("currentPhase", "DESIGN");
            event.put("targetPhase", "PLANNING");
            byte[] payload = objectMapper.writeValueAsBytes(event);

            // WHEN: Process event
            EventCloud.EventHandler handler = captureEventHandler();
            handler.handle("evt-flow", PHASE_TRANSITION_REQUESTED, payload);

            // THEN: Pipeline should be triggered
            verify(mockTriggerListener, times(1)).handlePatternMatch(any(), any(), any(), any());

            // WHEN: Stop bootstrap
            triggerListenerBootstrap.stop();

            // THEN: Should be stopped
            assertThat(triggerListenerBootstrap.isRunning()).isFalse();
        }

        @Test
        @DisplayName("Should gracefully handle stop without start")
        void shouldGracefullyHandleStopWithoutStart() {
            Promise<Void> result = triggerListenerBootstrap.stop();

            assertThat(result.isComplete()).isTrue();
            assertThat(triggerListenerBootstrap.isRunning()).isFalse();
        }
    }

    // ========== Helper Methods ==========

    /**
     * Capture the EventHandler passed to EventCloud.subscribe().
     */
    private EventCloud.EventHandler captureEventHandler() {
        ArgumentCaptor<EventCloud.EventHandler> captor = ArgumentCaptor.forClass(EventCloud.EventHandler.class);
        verify(mockEventCloud).subscribe(any(), any(), captor.capture());
        return captor.getValue();
    }
}
