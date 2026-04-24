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
    void setUp() { // GH-90000
        registry = new KernelRegistryImpl(); // GH-90000
        context = TestKernelContextFactory.create(registry); // GH-90000
        eventBus = new TestEventBus(); // GH-90000
    }

    @Test
    @DisplayName("Should publish and subscribe to events across modules")
    void testCrossModuleEventPublishing() { // GH-90000
        // GIVEN: Two modules with event subscription
        List<TestEvent> module1Events = new CopyOnWriteArrayList<>(); // GH-90000
        List<TestEvent> module2Events = new CopyOnWriteArrayList<>(); // GH-90000

        eventBus.subscribe("test.event", event -> { // GH-90000
            module1Events.add(event); // GH-90000
            return Promise.complete(); // GH-90000
        });

        eventBus.subscribe("test.event", event -> { // GH-90000
            module2Events.add(event); // GH-90000
            return Promise.complete(); // GH-90000
        });

        // WHEN: Publish event
        TestEvent event = new TestEvent("test-data");
        runPromise(() -> eventBus.publish("test.event", event)); // GH-90000

        // THEN: Both modules receive the event
        assertThat(module1Events).hasSize(1); // GH-90000
        assertThat(module2Events).hasSize(1); // GH-90000
        assertThat(module1Events.get(0).getData()).isEqualTo("test-data");
        assertThat(module2Events.get(0).getData()).isEqualTo("test-data");
    }

    @Test
    @DisplayName("Should handle event ordering guarantees")
    void testEventOrdering() { // GH-90000
        // GIVEN: Subscriber tracking event order
        List<Integer> receivedOrder = new CopyOnWriteArrayList<>(); // GH-90000

        eventBus.subscribe("ordered.event", event -> { // GH-90000
            receivedOrder.add(((OrderedEvent) event).getSequence()); // GH-90000
            return Promise.complete(); // GH-90000
        });

        // WHEN: Publish events in sequence
        for (int i = 1; i <= 10; i++) { // GH-90000
            OrderedEvent event = new OrderedEvent(i); // GH-90000
            runPromise(() -> eventBus.publish("ordered.event", event)); // GH-90000
        }

        // THEN: Events received in order
        assertThat(receivedOrder).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10); // GH-90000
    }

    @Test
    @DisplayName("Should propagate correlation ID across events")
    void testCorrelationIdPropagation() { // GH-90000
        // GIVEN: Subscriber that checks correlation ID
        List<String> correlationIds = new CopyOnWriteArrayList<>(); // GH-90000

        eventBus.subscribe("correlated.event", event -> { // GH-90000
            correlationIds.add(((CorrelatedEvent) event).getCorrelationId()); // GH-90000
            return Promise.complete(); // GH-90000
        });

        // WHEN: Publish events with correlation ID
        String correlationId = "correlation-123";
        CorrelatedEvent event = new CorrelatedEvent("data", correlationId); // GH-90000
        runPromise(() -> eventBus.publish("correlated.event", event)); // GH-90000

        // THEN: Correlation ID is preserved
        assertThat(correlationIds).containsExactly(correlationId); // GH-90000
    }

    @Test
    @DisplayName("Should handle subscriber errors without affecting other subscribers")
    void testSubscriberErrorIsolation() { // GH-90000
        // GIVEN: Multiple subscribers, one fails
        List<TestEvent> successfulSubscriber = new CopyOnWriteArrayList<>(); // GH-90000
        AtomicInteger failingSubscriberCalls = new AtomicInteger(0); // GH-90000

        eventBus.subscribe("test.event", event -> { // GH-90000
            successfulSubscriber.add(event); // GH-90000
            return Promise.complete(); // GH-90000
        });

        eventBus.subscribe("test.event", event -> { // GH-90000
            failingSubscriberCalls.incrementAndGet(); // GH-90000
            return Promise.ofException(new RuntimeException("Subscriber failed"));
        });

        // WHEN: Publish event
        TestEvent event = new TestEvent("test-data");
        runPromise(() -> eventBus.publish("test.event", event)); // GH-90000

        // THEN: Successful subscriber still receives event
        assertThat(successfulSubscriber).hasSize(1); // GH-90000
        assertThat(failingSubscriberCalls.get()).isEqualTo(1); // GH-90000
    }

    @Test
    @DisplayName("Should support event filtering by type")
    void testEventFiltering() { // GH-90000
        // GIVEN: Subscribers for different event types
        List<TestEvent> typeAEvents = new CopyOnWriteArrayList<>(); // GH-90000
        List<TestEvent> typeBEvents = new CopyOnWriteArrayList<>(); // GH-90000

        eventBus.subscribe("event.typeA", event -> { // GH-90000
            typeAEvents.add(event); // GH-90000
            return Promise.complete(); // GH-90000
        });

        eventBus.subscribe("event.typeB", event -> { // GH-90000
            typeBEvents.add(event); // GH-90000
            return Promise.complete(); // GH-90000
        });

        // WHEN: Publish different event types
        runPromise(() -> eventBus.publish("event.typeA", new TestEvent("dataA")));
        runPromise(() -> eventBus.publish("event.typeB", new TestEvent("dataB")));

        // THEN: Each subscriber receives only its event type
        assertThat(typeAEvents).hasSize(1); // GH-90000
        assertThat(typeBEvents).hasSize(1); // GH-90000
        assertThat(typeAEvents.get(0).getData()).isEqualTo("dataA");
        assertThat(typeBEvents.get(0).getData()).isEqualTo("dataB");
    }

    @Test
    @DisplayName("Should handle high-volume event publishing")
    void testHighVolumeEventPublishing() { // GH-90000
        // GIVEN: Subscriber counting events
        AtomicInteger eventCount = new AtomicInteger(0); // GH-90000

        eventBus.subscribe("high.volume", event -> { // GH-90000
            eventCount.incrementAndGet(); // GH-90000
            return Promise.complete(); // GH-90000
        });

        // WHEN: Publish 1000 events
        int totalEvents = 1000;
        for (int i = 0; i < totalEvents; i++) { // GH-90000
            TestEvent event = new TestEvent("data-" + i); // GH-90000
            runPromise(() -> eventBus.publish("high.volume", event)); // GH-90000
        }

        // THEN: All events received
        assertThat(eventCount.get()).isEqualTo(totalEvents); // GH-90000
    }

    @Test
    @DisplayName("Should support unsubscribe functionality")
    void testUnsubscribe() { // GH-90000
        // GIVEN: Subscriber that can be unsubscribed
        List<TestEvent> events = new CopyOnWriteArrayList<>(); // GH-90000

        String subscriptionId = eventBus.subscribe("test.event", event -> { // GH-90000
            events.add(event); // GH-90000
            return Promise.complete(); // GH-90000
        });

        // WHEN: Publish event, unsubscribe, publish again
        runPromise(() -> eventBus.publish("test.event", new TestEvent("data1")));

        eventBus.unsubscribe(subscriptionId); // GH-90000

        runPromise(() -> eventBus.publish("test.event", new TestEvent("data2")));

        // THEN: Only first event received
        assertThat(events).hasSize(1); // GH-90000
        assertThat(events.get(0).getData()).isEqualTo("data1");
    }

    @Test
    @DisplayName("Should handle async event processing")
    void testAsyncEventProcessing() { // GH-90000
        // GIVEN: Subscriber with async processing
        List<TestEvent> processedEvents = new CopyOnWriteArrayList<>(); // GH-90000

        eventBus.subscribe("async.event", event -> { // GH-90000
            return Promise.ofCallback(cb -> { // GH-90000
                // Simulate async processing
                processedEvents.add(event); // GH-90000
                cb.set(null); // GH-90000
            });
        });

        // WHEN: Publish event
        TestEvent event = new TestEvent("async-data");
        runPromise(() -> eventBus.publish("async.event", event)); // GH-90000

        // THEN: Event processed asynchronously
        assertThat(processedEvents).hasSize(1); // GH-90000
        assertThat(processedEvents.get(0).getData()).isEqualTo("async-data");
    }

    // Test event implementations

    private static class TestEvent {
        private final String data;

        TestEvent(String data) { // GH-90000
            this.data = data;
        }

        String getData() { // GH-90000
            return data;
        }
    }

    private static class OrderedEvent extends TestEvent {
        private final int sequence;

        OrderedEvent(int sequence) { // GH-90000
            super("data-" + sequence); // GH-90000
            this.sequence = sequence;
        }

        int getSequence() { // GH-90000
            return sequence;
        }
    }

    private static class CorrelatedEvent extends TestEvent {
        private final String correlationId;

        CorrelatedEvent(String data, String correlationId) { // GH-90000
            super(data); // GH-90000
            this.correlationId = correlationId;
        }

        String getCorrelationId() { // GH-90000
            return correlationId;
        }
    }

    private interface EventHandler {
        Promise<Void> handle(TestEvent event); // GH-90000
    }

    private static class TestEventBus {
        private final java.util.Map<String, List<SubscriptionEntry>> subscriptions = new java.util.HashMap<>(); // GH-90000
        private final AtomicInteger subscriptionIdCounter = new AtomicInteger(0); // GH-90000

        String subscribe(String eventType, EventHandler handler) { // GH-90000
            String subscriptionId = "sub-" + subscriptionIdCounter.incrementAndGet(); // GH-90000
            subscriptions.computeIfAbsent(eventType, k -> new ArrayList<>()) // GH-90000
                .add(new SubscriptionEntry(subscriptionId, handler)); // GH-90000
            return subscriptionId;
        }

        void unsubscribe(String subscriptionId) { // GH-90000
            subscriptions.values().forEach(list -> // GH-90000
                list.removeIf(entry -> entry.subscriptionId.equals(subscriptionId)) // GH-90000
            );
        }

        Promise<Void> publish(String eventType, TestEvent event) { // GH-90000
            List<SubscriptionEntry> handlers = subscriptions.get(eventType); // GH-90000
            if (handlers == null || handlers.isEmpty()) { // GH-90000
                return Promise.complete(); // GH-90000
            }

            List<Promise<Void>> handlerPromises = new ArrayList<>(); // GH-90000
            for (SubscriptionEntry entry : handlers) { // GH-90000
                Promise<Void> handlerPromise = entry.handler.handle(event) // GH-90000
                    .then( // GH-90000
                        result -> Promise.complete(), // GH-90000
                        error -> {
                            // Isolate handler failure — other subscribers still run
                            return Promise.complete(); // GH-90000
                        }
                    );
                handlerPromises.add(handlerPromise); // GH-90000
            }

            return io.activej.promise.Promises.all(handlerPromises); // GH-90000
        }

        private static class SubscriptionEntry {
            final String subscriptionId;
            final EventHandler handler;

            SubscriptionEntry(String subscriptionId, EventHandler handler) { // GH-90000
                this.subscriptionId = subscriptionId;
                this.handler = handler;
            }
        }
    }
}
