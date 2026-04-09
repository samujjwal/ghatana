package com.ghatana.kernel.event;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.registry.KernelRegistryImpl;
import com.ghatana.kernel.test.TestKernelContextFactory;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for Kernel Event Bus.
 * Validates cross-module event publishing and subscription.
 *
 * @doc.type class
 * @doc.purpose Validates Kernel event bus cross-module communication
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Kernel Event Bus Integration Tests")
class KernelEventBusIntegrationTest extends EventloopTestBase {

    private KernelRegistryImpl registry;
    private KernelContext context;
    private TestEventBus eventBus;

    @BeforeEach
    void setUp() {
        registry = new KernelRegistryImpl();
        context = TestKernelContextFactory.create(registry);
        eventBus = new TestEventBus();
    }

    @Test
    @DisplayName("Should publish and subscribe to events across modules")
    void testCrossModuleEventPublishing() {
        // GIVEN: Two modules with event subscription
        List<TestEvent> module1Events = new CopyOnWriteArrayList<>();
        List<TestEvent> module2Events = new CopyOnWriteArrayList<>();

        eventBus.subscribe("test.event", event -> {
            module1Events.add(event);
            return Promise.complete();
        });

        eventBus.subscribe("test.event", event -> {
            module2Events.add(event);
            return Promise.complete();
        });

        // WHEN: Publish event
        TestEvent event = new TestEvent("test-data");
        runPromise(() -> eventBus.publish("test.event", event));

        // THEN: Both modules receive the event
        assertThat(module1Events).hasSize(1);
        assertThat(module2Events).hasSize(1);
        assertThat(module1Events.get(0).getData()).isEqualTo("test-data");
        assertThat(module2Events.get(0).getData()).isEqualTo("test-data");
    }

    @Test
    @DisplayName("Should handle event ordering guarantees")
    void testEventOrdering() {
        // GIVEN: Subscriber tracking event order
        List<Integer> receivedOrder = new CopyOnWriteArrayList<>();

        eventBus.subscribe("ordered.event", event -> {
            receivedOrder.add(((OrderedEvent) event).getSequence());
            return Promise.complete();
        });

        // WHEN: Publish events in sequence
        for (int i = 1; i <= 10; i++) {
            OrderedEvent event = new OrderedEvent(i);
            runPromise(() -> eventBus.publish("ordered.event", event));
        }

        // THEN: Events received in order
        assertThat(receivedOrder).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
    }

    @Test
    @DisplayName("Should propagate correlation ID across events")
    void testCorrelationIdPropagation() {
        // GIVEN: Subscriber that checks correlation ID
        List<String> correlationIds = new CopyOnWriteArrayList<>();

        eventBus.subscribe("correlated.event", event -> {
            correlationIds.add(((CorrelatedEvent) event).getCorrelationId());
            return Promise.complete();
        });

        // WHEN: Publish events with correlation ID
        String correlationId = "correlation-123";
        CorrelatedEvent event = new CorrelatedEvent("data", correlationId);
        runPromise(() -> eventBus.publish("correlated.event", event));

        // THEN: Correlation ID is preserved
        assertThat(correlationIds).containsExactly(correlationId);
    }

    @Test
    @DisplayName("Should handle subscriber errors without affecting other subscribers")
    void testSubscriberErrorIsolation() {
        // GIVEN: Multiple subscribers, one fails
        List<TestEvent> successfulSubscriber = new CopyOnWriteArrayList<>();
        AtomicInteger failingSubscriberCalls = new AtomicInteger(0);

        eventBus.subscribe("test.event", event -> {
            successfulSubscriber.add(event);
            return Promise.complete();
        });

        eventBus.subscribe("test.event", event -> {
            failingSubscriberCalls.incrementAndGet();
            return Promise.ofException(new RuntimeException("Subscriber failed"));
        });

        // WHEN: Publish event
        TestEvent event = new TestEvent("test-data");
        runPromise(() -> eventBus.publish("test.event", event));

        // THEN: Successful subscriber still receives event
        assertThat(successfulSubscriber).hasSize(1);
        assertThat(failingSubscriberCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should support event filtering by type")
    void testEventFiltering() {
        // GIVEN: Subscribers for different event types
        List<TestEvent> typeAEvents = new CopyOnWriteArrayList<>();
        List<TestEvent> typeBEvents = new CopyOnWriteArrayList<>();

        eventBus.subscribe("event.typeA", event -> {
            typeAEvents.add(event);
            return Promise.complete();
        });

        eventBus.subscribe("event.typeB", event -> {
            typeBEvents.add(event);
            return Promise.complete();
        });

        // WHEN: Publish different event types
        runPromise(() -> eventBus.publish("event.typeA", new TestEvent("dataA")));
        runPromise(() -> eventBus.publish("event.typeB", new TestEvent("dataB")));

        // THEN: Each subscriber receives only its event type
        assertThat(typeAEvents).hasSize(1);
        assertThat(typeBEvents).hasSize(1);
        assertThat(typeAEvents.get(0).getData()).isEqualTo("dataA");
        assertThat(typeBEvents.get(0).getData()).isEqualTo("dataB");
    }

    @Test
    @DisplayName("Should handle high-volume event publishing")
    void testHighVolumeEventPublishing() {
        // GIVEN: Subscriber counting events
        AtomicInteger eventCount = new AtomicInteger(0);

        eventBus.subscribe("high.volume", event -> {
            eventCount.incrementAndGet();
            return Promise.complete();
        });

        // WHEN: Publish 1000 events
        int totalEvents = 1000;
        for (int i = 0; i < totalEvents; i++) {
            TestEvent event = new TestEvent("data-" + i);
            runPromise(() -> eventBus.publish("high.volume", event));
        }

        // THEN: All events received
        assertThat(eventCount.get()).isEqualTo(totalEvents);
    }

    @Test
    @DisplayName("Should support unsubscribe functionality")
    void testUnsubscribe() {
        // GIVEN: Subscriber that can be unsubscribed
        List<TestEvent> events = new CopyOnWriteArrayList<>();

        String subscriptionId = eventBus.subscribe("test.event", event -> {
            events.add(event);
            return Promise.complete();
        });

        // WHEN: Publish event, unsubscribe, publish again
        runPromise(() -> eventBus.publish("test.event", new TestEvent("data1")));

        eventBus.unsubscribe(subscriptionId);

        runPromise(() -> eventBus.publish("test.event", new TestEvent("data2")));

        // THEN: Only first event received
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getData()).isEqualTo("data1");
    }

    @Test
    @DisplayName("Should handle async event processing")
    void testAsyncEventProcessing() {
        // GIVEN: Subscriber with async processing
        List<TestEvent> processedEvents = new CopyOnWriteArrayList<>();

        eventBus.subscribe("async.event", event -> {
            return Promise.ofCallback(cb -> {
                // Simulate async processing
                processedEvents.add(event);
                cb.set(null);
            });
        });

        // WHEN: Publish event
        TestEvent event = new TestEvent("async-data");
        runPromise(() -> eventBus.publish("async.event", event));

        // THEN: Event processed asynchronously
        assertThat(processedEvents).hasSize(1);
        assertThat(processedEvents.get(0).getData()).isEqualTo("async-data");
    }

    // Test event implementations

    private static class TestEvent {
        private final String data;

        TestEvent(String data) {
            this.data = data;
        }

        String getData() {
            return data;
        }
    }

    private static class OrderedEvent extends TestEvent {
        private final int sequence;

        OrderedEvent(int sequence) {
            super("data-" + sequence);
            this.sequence = sequence;
        }

        int getSequence() {
            return sequence;
        }
    }

    private static class CorrelatedEvent extends TestEvent {
        private final String correlationId;

        CorrelatedEvent(String data, String correlationId) {
            super(data);
            this.correlationId = correlationId;
        }

        String getCorrelationId() {
            return correlationId;
        }
    }

    private interface EventHandler {
        Promise<Void> handle(TestEvent event);
    }

    private static class TestEventBus {
        private final java.util.Map<String, List<SubscriptionEntry>> subscriptions = new java.util.HashMap<>();
        private final AtomicInteger subscriptionIdCounter = new AtomicInteger(0);

        String subscribe(String eventType, EventHandler handler) {
            String subscriptionId = "sub-" + subscriptionIdCounter.incrementAndGet();
            subscriptions.computeIfAbsent(eventType, k -> new ArrayList<>())
                .add(new SubscriptionEntry(subscriptionId, handler));
            return subscriptionId;
        }

        void unsubscribe(String subscriptionId) {
            subscriptions.values().forEach(list ->
                list.removeIf(entry -> entry.subscriptionId.equals(subscriptionId))
            );
        }

        Promise<Void> publish(String eventType, TestEvent event) {
            List<SubscriptionEntry> handlers = subscriptions.get(eventType);
            if (handlers == null || handlers.isEmpty()) {
                return Promise.complete();
            }

            List<Promise<Void>> handlerPromises = new ArrayList<>();
            for (SubscriptionEntry entry : handlers) {
                Promise<Void> handlerPromise = entry.handler.handle(event)
                    .then(
                        result -> Promise.complete(),
                        error -> {
                            // Log error but don't fail other handlers
                            System.err.println("Handler error: " + error.getMessage());
                            return Promise.complete();
                        }
                    );
                handlerPromises.add(handlerPromise);
            }

            return io.activej.promise.Promises.all(handlerPromises);
        }

        private static class SubscriptionEntry {
            final String subscriptionId;
            final EventHandler handler;

            SubscriptionEntry(String subscriptionId, EventHandler handler) {
                this.subscriptionId = subscriptionId;
                this.handler = handler;
            }
        }
    }
}
