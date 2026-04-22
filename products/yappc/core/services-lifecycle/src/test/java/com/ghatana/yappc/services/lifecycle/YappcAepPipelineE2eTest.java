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

@DisplayName("YAPPC AEP Pipeline E2E Tests (Item 7.2) [GH-90000]")
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
    void setUp() { // GH-90000
        MockitoAnnotations.openMocks(this); // GH-90000
        objectMapper = new ObjectMapper(); // GH-90000

        // Setup mock subscription
        mockSubscription = mock(EventCloud.Subscription.class); // GH-90000
        when(mockSubscription.isCancelled()).thenReturn(false); // GH-90000

        // Setup EventCloud to store handler and allow manual triggering
        AtomicReference<EventCloud.EventHandler> handlerRef = new AtomicReference<>(); // GH-90000
        when(mockEventCloud.subscribe( // GH-90000
                nullable(String.class), // GH-90000
                eq(PHASE_TRANSITION_REQUESTED), // GH-90000
                any(EventCloud.EventHandler.class))) // GH-90000
            .thenAnswer(inv -> { // GH-90000
                EventCloud.EventHandler handler = inv.getArgument(2); // GH-90000
                handlerRef.set(handler); // GH-90000
                return mockSubscription;
            });

        // Setup TriggerListener to succeed
        when(mockTriggerListener.handlePatternMatch(any(), any(), any(), any())) // GH-90000
            .thenReturn(Promise.complete()); // GH-90000

        // Create bootstrap with test tenant
        triggerListenerBootstrap = new TriggerListenerBootstrap( // GH-90000
            mockTriggerListener,
            mockEventCloud,
            mockPipelineBootstrapper,
            mockDlqPublisher,
            objectMapper,
            List.of("test-tenant [GH-90000]")
        );
    }

    @Nested
    @DisplayName("Bootstrap Lifecycle [GH-90000]")
    class BootstrapLifecycleTests {

        @Test
        @DisplayName("Should start successfully and subscribe to events [GH-90000]")
        void shouldStartSuccessfully() { // GH-90000
            Promise<Void> result = triggerListenerBootstrap.start(); // GH-90000

            assertThat(result.isComplete()).isTrue(); // GH-90000
            assertThat(triggerListenerBootstrap.isRunning()).isTrue(); // GH-90000
            assertThat(triggerListenerBootstrap.getSubscriptionCount()).isEqualTo(1); // GH-90000

            verify(mockEventCloud, times(1)).subscribe( // GH-90000
                nullable(String.class), // GH-90000
                eq(PHASE_TRANSITION_REQUESTED), // GH-90000
                any(EventCloud.EventHandler.class) // GH-90000
            );
        }

        @Test
        @DisplayName("Should be idempotent on multiple start() calls [GH-90000]")
        void shouldBeIdempotentOnMultipleStarts() { // GH-90000
            triggerListenerBootstrap.start(); // GH-90000
            triggerListenerBootstrap.start(); // GH-90000
            triggerListenerBootstrap.start(); // GH-90000

            assertThat(triggerListenerBootstrap.isRunning()).isTrue(); // GH-90000
            assertThat(triggerListenerBootstrap.getSubscriptionCount()).isEqualTo(1); // GH-90000

            verify(mockEventCloud, times(1)).subscribe( // GH-90000
                nullable(String.class), // GH-90000
                any(String.class), // GH-90000
                any(EventCloud.EventHandler.class) // GH-90000
            );
        }

        @Test
        @DisplayName("Should stop successfully and cancel subscriptions [GH-90000]")
        void shouldStopSuccessfully() { // GH-90000
            triggerListenerBootstrap.start(); // GH-90000
            assertThat(triggerListenerBootstrap.isRunning()).isTrue(); // GH-90000

            Promise<Void> result = triggerListenerBootstrap.stop(); // GH-90000

            assertThat(result.isComplete()).isTrue(); // GH-90000
            assertThat(triggerListenerBootstrap.isRunning()).isFalse(); // GH-90000
            assertThat(triggerListenerBootstrap.getSubscriptionCount()).isEqualTo(0); // GH-90000

            verify(mockSubscription, times(1)).cancel(); // GH-90000
        }

        @Test
        @DisplayName("Should be idempotent on multiple stop() calls [GH-90000]")
        void shouldBeIdempotentOnMultipleStops() { // GH-90000
            triggerListenerBootstrap.start(); // GH-90000
            triggerListenerBootstrap.stop(); // GH-90000
            triggerListenerBootstrap.stop(); // GH-90000
            triggerListenerBootstrap.stop(); // GH-90000

            assertThat(triggerListenerBootstrap.isRunning()).isFalse(); // GH-90000

            verify(mockSubscription, times(1)).cancel(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Event Processing [GH-90000]")
    class EventProcessingTests {

        @Test
        @DisplayName("Should process valid phase transition event [GH-90000]")
        void shouldProcessValidEvent() throws Exception { // GH-90000
            triggerListenerBootstrap.start(); // GH-90000

            // Simulate receiving event from event cloud
            Map<String, Object> eventData = new HashMap<>(); // GH-90000
            eventData.put("tenantId", "tenant-123"); // GH-90000
            eventData.put("projectId", "proj-456"); // GH-90000
            eventData.put("currentPhase", "DESIGN"); // GH-90000
            eventData.put("targetPhase", "PLANNING"); // GH-90000
            eventData.put("correlationId", "corr-789"); // GH-90000

            byte[] payload = objectMapper.writeValueAsBytes(eventData); // GH-90000

            // Manually trigger the handler (simulating EventCloud subscription) // GH-90000
            String eventId = "evt-001";
            EventCloud.EventHandler handler = captureEventHandler(); // GH-90000
            handler.handle(eventId, PHASE_TRANSITION_REQUESTED, payload); // GH-90000

            // Verify TriggerListener was called
            verify(mockTriggerListener, times(1)).handlePatternMatch( // GH-90000
                eq("tenant-123 [GH-90000]"),
                eq(PIPELINE_ID), // GH-90000
                eq(eventId), // GH-90000
                argThat(data -> data instanceof Map && ((Map<?, ?>) data).containsKey("projectId [GH-90000]"))
            );
        }

        @Test
        @DisplayName("Should handle null payload and publish to DLQ [GH-90000]")
        void shouldHandleNullPayload() { // GH-90000
            triggerListenerBootstrap.start(); // GH-90000

            EventCloud.EventHandler handler = captureEventHandler(); // GH-90000
            String eventId = "evt-null";

            handler.handle(eventId, PHASE_TRANSITION_REQUESTED, null); // GH-90000

            verify(mockDlqPublisher, times(1)).publishErrorEvent( // GH-90000
                eq(eventId), // GH-90000
                eq(PHASE_TRANSITION_REQUESTED), // GH-90000
                eq("EMPTY_PAYLOAD [GH-90000]"),
                any(String.class) // GH-90000
            );
        }

        @Test
        @DisplayName("Should handle empty payload and publish to DLQ [GH-90000]")
        void shouldHandleEmptyPayload() { // GH-90000
            triggerListenerBootstrap.start(); // GH-90000

            EventCloud.EventHandler handler = captureEventHandler(); // GH-90000
            String eventId = "evt-empty";

            handler.handle(eventId, PHASE_TRANSITION_REQUESTED, new byte[0]); // GH-90000

            verify(mockDlqPublisher, times(1)).publishErrorEvent( // GH-90000
                eq(eventId), // GH-90000
                eq(PHASE_TRANSITION_REQUESTED), // GH-90000
                eq("EMPTY_PAYLOAD [GH-90000]"),
                any(String.class) // GH-90000
            );
        }

        @Test
        @DisplayName("Should handle malformed JSON payload and publish to DLQ [GH-90000]")
        void shouldHandleMalformedJson() { // GH-90000
            triggerListenerBootstrap.start(); // GH-90000

            EventCloud.EventHandler handler = captureEventHandler(); // GH-90000
            String eventId = "evt-malformed";
            byte[] malformedPayload = "{invalid json".getBytes(); // GH-90000

            handler.handle(eventId, PHASE_TRANSITION_REQUESTED, malformedPayload); // GH-90000

            verify(mockDlqPublisher, times(1)).publishErrorEvent( // GH-90000
                eq(eventId), // GH-90000
                eq(PHASE_TRANSITION_REQUESTED), // GH-90000
                eq("PROCESSING_ERROR [GH-90000]"),
                any(String.class) // GH-90000
            );
        }

        @Test
        @DisplayName("Should handle pipeline processing errors and publish to DLQ [GH-90000]")
        void shouldHandlePipelineErrors() throws Exception { // GH-90000
            // Setup TriggerListener to fail
            when(mockTriggerListener.handlePatternMatch(any(), any(), any(), any())) // GH-90000
                .thenReturn(Promise.ofException(new RuntimeException("Pipeline error [GH-90000]")));

            triggerListenerBootstrap.start(); // GH-90000

            Map<String, Object> eventData = new HashMap<>(); // GH-90000
            eventData.put("tenantId", "tenant-error"); // GH-90000
            eventData.put("projectId", "proj-error"); // GH-90000
            byte[] payload = objectMapper.writeValueAsBytes(eventData); // GH-90000

            EventCloud.EventHandler handler = captureEventHandler(); // GH-90000
            String eventId = "evt-error";
            handler.handle(eventId, PHASE_TRANSITION_REQUESTED, payload); // GH-90000

            // Small delay to allow async completion to be processed
            Thread.sleep(100); // GH-90000

            verify(mockDlqPublisher, times(1)).publishErrorEvent( // GH-90000
                eq(eventId), // GH-90000
                eq(PHASE_TRANSITION_REQUESTED), // GH-90000
                eq("PIPELINE_ERROR [GH-90000]"),
                any(String.class) // GH-90000
            );
        }
    }

    @Nested
    @DisplayName("Multi-Event Processing [GH-90000]")
    class MultiEventProcessingTests {

        @Test
        @DisplayName("Should handle multiple concurrent events [GH-90000]")
        void shouldHandleMultipleConcurrentEvents() throws Exception { // GH-90000
            triggerListenerBootstrap.start(); // GH-90000

            AtomicInteger successCount = new AtomicInteger(0); // GH-90000
            when(mockTriggerListener.handlePatternMatch(any(), any(), any(), any())) // GH-90000
                .thenAnswer(inv -> { // GH-90000
                    successCount.incrementAndGet(); // GH-90000
                    return Promise.complete(); // GH-90000
                });

            EventCloud.EventHandler handler = captureEventHandler(); // GH-90000

            // Send 10 concurrent events
            ExecutorService executor = Executors.newFixedThreadPool(5); // GH-90000
            List<Future<?>> futures = new ArrayList<>(); // GH-90000

            for (int i = 0; i < 10; i++) { // GH-90000
                final int index = i;
                futures.add(executor.submit(() -> { // GH-90000
                    try {
                        Map<String, Object> eventData = new HashMap<>(); // GH-90000
                        eventData.put("tenantId", "tenant-" + index); // GH-90000
                        eventData.put("projectId", "proj-" + index); // GH-90000
                        byte[] payload = objectMapper.writeValueAsBytes(eventData); // GH-90000

                        handler.handle("evt-" + index, PHASE_TRANSITION_REQUESTED, payload); // GH-90000
                    } catch (Exception e) { // GH-90000
                        fail("Event processing failed", e); // GH-90000
                    }
                }));
            }

            // Wait for all events to complete
            for (Future<?> future : futures) { // GH-90000
                future.get(5, TimeUnit.SECONDS); // GH-90000
            }

            executor.shutdown(); // GH-90000

            // Verify all events were processed
            assertThat(successCount.get()).isEqualTo(10); // GH-90000
            verify(mockTriggerListener, times(10)).handlePatternMatch(any(), any(), any(), any()); // GH-90000
        }

        @Test
        @DisplayName("Should maintain subscription after processing multiple events [GH-90000]")
        void shouldMaintainSubscriptionAfterMultipleEvents() throws Exception { // GH-90000
            triggerListenerBootstrap.start(); // GH-90000
            EventCloud.EventHandler handler = captureEventHandler(); // GH-90000

            Map<String, Object> eventData = new HashMap<>(); // GH-90000
            eventData.put("tenantId", "tenant-multi"); // GH-90000
            byte[] payload = objectMapper.writeValueAsBytes(eventData); // GH-90000

            // Send multiple events
            for (int i = 0; i < 5; i++) { // GH-90000
                handler.handle("evt-multi-" + i, PHASE_TRANSITION_REQUESTED, payload); // GH-90000
            }

            // Verify subscription is still active
            assertThat(triggerListenerBootstrap.isRunning()).isTrue(); // GH-90000
            assertThat(triggerListenerBootstrap.getSubscriptionCount()).isEqualTo(1); // GH-90000
        }
    }

    @Nested
    @DisplayName("Integration Tests [GH-90000]")
    class IntegrationTests {

        @Test
        @DisplayName("Should execute full flow: start → subscribe → process → stop [GH-90000]")
        void shouldExecuteFullFlow() throws Exception { // GH-90000
            // GIVEN: Bootstrap not started
            assertThat(triggerListenerBootstrap.isRunning()).isFalse(); // GH-90000

            // WHEN: Start bootstrap
            triggerListenerBootstrap.start(); // GH-90000

            // THEN: Should be subscribed
            assertThat(triggerListenerBootstrap.isRunning()).isTrue(); // GH-90000

            // GIVEN: Prepare phase transition event
            Map<String, Object> event = new HashMap<>(); // GH-90000
            event.put("tenantId", "tenant-full-flow"); // GH-90000
            event.put("currentPhase", "DESIGN"); // GH-90000
            event.put("targetPhase", "PLANNING"); // GH-90000
            byte[] payload = objectMapper.writeValueAsBytes(event); // GH-90000

            // WHEN: Process event
            EventCloud.EventHandler handler = captureEventHandler(); // GH-90000
            handler.handle("evt-flow", PHASE_TRANSITION_REQUESTED, payload); // GH-90000

            // THEN: Pipeline should be triggered
            verify(mockTriggerListener, times(1)).handlePatternMatch(any(), any(), any(), any()); // GH-90000

            // WHEN: Stop bootstrap
            triggerListenerBootstrap.stop(); // GH-90000

            // THEN: Should be stopped
            assertThat(triggerListenerBootstrap.isRunning()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("Should gracefully handle stop without start [GH-90000]")
        void shouldGracefullyHandleStopWithoutStart() { // GH-90000
            Promise<Void> result = triggerListenerBootstrap.stop(); // GH-90000

            assertThat(result.isComplete()).isTrue(); // GH-90000
            assertThat(triggerListenerBootstrap.isRunning()).isFalse(); // GH-90000
        }
    }

    // ========== Helper Methods ==========

    /**
     * Capture the EventHandler passed to EventCloud.subscribe(). // GH-90000
     */
    private EventCloud.EventHandler captureEventHandler() { // GH-90000
        ArgumentCaptor<EventCloud.EventHandler> captor = ArgumentCaptor.forClass(EventCloud.EventHandler.class); // GH-90000
        verify(mockEventCloud).subscribe(any(), any(), captor.capture()); // GH-90000
        return captor.getValue(); // GH-90000
    }
}
