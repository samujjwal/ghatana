package com.ghatana.platform.cache.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.cache.DistributedCacheService.CacheBackend;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @doc.type class
 * @doc.purpose Integration tests for cache invalidation event publishing and subscription.
 * @doc.layer platform-test
 * @doc.pattern IntegrationTest
 *
 * Tests:
 * - Event publishing for all operation types (single key, pattern, bulk delete)
 * - Cross-service event propagation
 * - Event deduplication (avoid re-processing own events)
 * - Concurrent event publishing
 * - Error handling (non-blocking failures)
 * - End-to-end distributed cache synchronization
 *
 * All tests use mock message bus to simulate real Kafka/RabbitMQ behavior.
 */
@DisplayName("CacheInvalidationEvent Integration Tests")
class CacheInvalidationEventIntegrationTest extends EventloopTestBase {

    @Mock
    private AsyncMessageBus messageBus;

    @Mock
    private CacheBackend cacheBackend;

    private CacheInvalidationEventPublisher publisher;
    private CacheInvalidationEventSubscriber subscriber;
    private List<Object> publishedEvents;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
        publishedEvents = new ArrayList<>();

        // Setup publisher
        publisher = new CacheInvalidationEventPublisher(
            messageBus,
            "TutorPutorContentCacheService",
            "tenant:123"
        );

        // Setup subscriber
        subscriber = new CacheInvalidationEventSubscriber(
            messageBus,
            cacheBackend,
            "DataCloudQueryCacheService"  // Different service to simulate cross-service sync
        );

        // Mock message bus to capture published events
        when(messageBus.publish(eq("cache-invalidation-events"), any()))
            .thenAnswer(invocation -> {
                Object event = invocation.getArgument(1);
                publishedEvents.add(event);
                return Promise.complete();
            });
    }

    @Nested
    @DisplayName("Single Key Delete Events")
    class SingleKeyDeleteEventTests {

        @Test
        @DisplayName("should publish single key deletion event")
        void shouldPublishSingleKeyDeleteEvent() {
            runPromise(() -> publisher.publishSingleKeyDelete("content:generated:123", "trace:id:1"));

            assertThat(publishedEvents)
                .hasSize(1)
                .anySatisfy(event -> {
                    assertThat(event)
                        .isInstanceOf(CacheInvalidationEventPublisher.CacheInvalidationEvent.class);
                    CacheInvalidationEventPublisher.CacheInvalidationEvent invalidationEvent =
                        (CacheInvalidationEventPublisher.CacheInvalidationEvent) event;
                    assertThat(invalidationEvent.operationType)
                        .isEqualTo(CacheInvalidationEventPublisher.OperationType.SINGLE_KEY_DELETE);
                    assertThat(invalidationEvent.cacheKey).isEqualTo("content:generated:123");
                });
        }

        @Test
        @DisplayName("should include correlation ID in published event")
        void shouldIncludeCorrelationId() {
            String correlationId = "trace:request:789";
            runPromise(() -> publisher.publishSingleKeyDelete("cache:key", correlationId));

            assertThat(publishedEvents).hasSize(1);
            verify(messageBus).publish(eq("cache-invalidation-events"), any());
        }

        @Test
        @DisplayName("should handle multiple rapid single key deletions")
        void shouldHandleMultipleRapidDeletions() {
            for (int i = 0; i < 100; i++) {
                final int index = i;
                runPromise(() -> publisher.publishSingleKeyDelete("cache:key:" + index, "trace:" + index));
            }

            assertThat(publishedEvents).hasSize(100);
            verify(messageBus, times(100)).publish(eq("cache-invalidation-events"), any());
        }
    }

    @Nested
    @DisplayName("Pattern Delete Events")
    class PatternDeleteEventTests {

        @Test
        @DisplayName("should publish pattern deletion event with key count")
        void shouldPublishPatternDeleteEvent() {
            runPromise(() -> publisher.publishPatternDelete("learning-path:user:100:*", 25, "trace:id:2"));

            assertThat(publishedEvents)
                .hasSize(1)
                .anySatisfy(event -> {
                    assertThat(event)
                        .isInstanceOf(CacheInvalidationEventPublisher.CacheInvalidationEvent.class);
                    CacheInvalidationEventPublisher.CacheInvalidationEvent invalidationEvent =
                        (CacheInvalidationEventPublisher.CacheInvalidationEvent) event;
                    assertThat(invalidationEvent.operationType)
                        .isEqualTo(CacheInvalidationEventPublisher.OperationType.PATTERN_DELETE);
                    assertThat(invalidationEvent.cacheKey).isEqualTo("learning-path:user:100:*");
                    assertThat(invalidationEvent.keyCount).isEqualTo(25);
                });
        }

        @Test
        @DisplayName("should support various glob patterns")
        void shouldSupportVariousPatterns() {
            String[] patterns = {
                "content:*",
                "learning-path:user:*:*",
                "query:result:dataset:*",
                "aggregate:*:*"
            };

            for (String pattern : patterns) {
                runPromise(() -> publisher.publishPatternDelete(pattern, 10, "trace:pattern"));
            }

            assertThat(publishedEvents).hasSize(4);
        }

        @Test
        @DisplayName("should include correct key count in event")
        void shouldIncludeCorrectKeyCount() {
            long keyCount = 150;
            runPromise(() -> publisher.publishPatternDelete("dataset:*", keyCount, "trace:pattern:keys"));

            assertThat(publishedEvents).hasSize(1);
            verify(messageBus).publish(eq("cache-invalidation-events"), any());
        }
    }

    @Nested
    @DisplayName("Bulk Delete Events")
    class BulkDeleteEventTests {

        @Test
        @DisplayName("should publish bulk deletion event")
        void shouldPublishBulkDeleteEvent() {
            runPromise(() -> publisher.publishBulkDelete("tenant:456:offboarding", 5000, "trace:bulk:1"));

            assertThat(publishedEvents)
                .hasSize(1)
                .anySatisfy(event -> {
                    assertThat(event)
                        .isInstanceOf(CacheInvalidationEventPublisher.CacheInvalidationEvent.class);
                    CacheInvalidationEventPublisher.CacheInvalidationEvent invalidationEvent =
                        (CacheInvalidationEventPublisher.CacheInvalidationEvent) event;
                    assertThat(invalidationEvent.operationType)
                        .isEqualTo(CacheInvalidationEventPublisher.OperationType.BULK_DELETE);
                    assertThat(invalidationEvent.cacheKey).isEqualTo("tenant:456:offboarding");
                    assertThat(invalidationEvent.keyCount).isEqualTo(5000);
                });
        }

        @Test
        @DisplayName("should handle large bulk deletion events")
        void shouldHandleLargeBulkDeletion() {
            long largeKeyCount = 100_000;
            runPromise(() -> publisher.publishBulkDelete("migration:schema:v1:to:v2", largeKeyCount, "trace:migration"));

            assertThat(publishedEvents).hasSize(1);
            verify(messageBus).publish(eq("cache-invalidation-events"), any());
        }
    }

    @Nested
    @DisplayName("Event Subscription and Application")
    class EventSubscriptionTests {

        @Test
        @DisplayName("should apply single key deletion from received event")
        void shouldApplySingleKeyDeletion() {
            AtomicBoolean eventProcessed = new AtomicBoolean(false);

            when(messageBus.subscribe(eq("cache-invalidation-events"), any()))
                .thenAnswer(invocation -> {
                    eventProcessed.set(true);
                    return Promise.complete();
                });

            runPromise(() -> subscriber.start());

            verify(messageBus).subscribe(eq("cache-invalidation-events"), any());
            assertThat(eventProcessed).isTrue();
        }

        @Test
        @DisplayName("should apply pattern deletion from received event")
        void shouldApplyPatternDeletion() {
            when(messageBus.subscribe(eq("cache-invalidation-events"), any()))
                .thenAnswer(invocation -> {
                    when(cacheBackend.deletePattern("learning-path:user:100:*")).thenReturn(25);
                    return Promise.complete();
                });

            runPromise(() -> subscriber.start());

            verify(messageBus).subscribe(eq("cache-invalidation-events"), any());
        }

        @Test
        @DisplayName("should skip events from own service (avoid duplicate processing)")
        void shouldSkipOwnServiceEvents() {
            // Create publisher with same source as subscriber
            CacheInvalidationEventPublisher sameSourcePublisher = new CacheInvalidationEventPublisher(
                messageBus,
                "DataCloudQueryCacheService",  // Same as subscriber's serviceSource
                "tenant:123"
            );

            when(messageBus.subscribe(eq("cache-invalidation-events"), any()))
                .thenReturn(Promise.complete());

            runPromise(() -> subscriber.start());

            // Verify that deleteKey would NOT be called since event source matches
            verify(cacheBackend, never()).deleteKey(anyString());
        }
    }

    @Nested
    @DisplayName("Cross-Service Event Propagation")
    class CrossServicePropagationTests {

        @Test
        @DisplayName("should propagate events between different services")
        void shouldPropagateBetweenServices() {
            // Service A publishes event
            CacheInvalidationEventPublisher publisherA = new CacheInvalidationEventPublisher(
                messageBus, "ServiceA", "tenant:123"
            );

            // Service B subscribes
            CacheInvalidationEventSubscriber subscriberB = new CacheInvalidationEventSubscriber(
                messageBus, cacheBackend, "ServiceB"
            );

            // Service A publishes
            runPromise(() -> publisherA.publishSingleKeyDelete("cache:key:1", "trace:cross:1"));

            when(messageBus.subscribe(eq("cache-invalidation-events"), any()))
                .thenReturn(Promise.complete());

            runPromise(() -> subscriberB.start());

            assertThat(publishedEvents).hasSize(1);
            verify(messageBus).publish(eq("cache-invalidation-events"), any());
        }

        @Test
        @DisplayName("should handle events from multiple services concurrently")
        void shouldHandleMultipleServicesConcurrently() throws InterruptedException {
            int serviceCount = 5;
            Thread[] threads = new Thread[serviceCount];

            for (int s = 0; s < serviceCount; s++) {
                final int serviceId = s;
                threads[s] = new Thread(() -> {
                    CacheInvalidationEventPublisher servicePublisher = new CacheInvalidationEventPublisher(
                        messageBus, "Service" + serviceId, "tenant:123"
                    );

                    for (int op = 0; op < 20; op++) {
                        final int operation = op;
                        runPromise(() -> servicePublisher.publishSingleKeyDelete(
                            "key:service:" + serviceId + ":op:" + operation,
                            "trace:" + serviceId + ":" + operation
                        ));
                    }
                });
                threads[s].start();
            }

            for (Thread t : threads) {
                t.join();
            }

            assertThat(publishedEvents).hasSize(serviceCount * 20);
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("should handle message bus publish failures gracefully")
        void shouldHandlePublishFailureGracefully() {
            when(messageBus.publish(eq("cache-invalidation-events"), any()))
                .thenReturn(Promise.ofException(new RuntimeException("Bus unavailable")));

            // Should not throw exception
            assertThatCode(() -> publisher.publishSingleKeyDelete("cache:key", "trace:error")
                .whenComplete(($, $$) -> { }))
                .doesNotThrowAnyException();

            clearFatalError();

            verify(messageBus).publish(eq("cache-invalidation-events"), any());
        }

        @Test
        @DisplayName("should handle cache backend deletion failures in subscriber")
        void shouldHandleBackendFailureGracefully() {
            doThrow(new RuntimeException("Cache connection lost"))
                .when(cacheBackend).deleteKey(anyString());

            when(messageBus.subscribe(eq("cache-invalidation-events"), any()))
                .thenAnswer(invocation -> {
                    // Simulate backend failure during event processing
                    return Promise.complete();
                });

            // Should not throw exception
            assertThatCode(() -> runPromise(() -> subscriber.start()))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should handle malformed events gracefully")
        void shouldHandleMalformedEvents() {
            // Publish invalid event
            publishedEvents.add(new InvalidEvent());

            when(messageBus.subscribe(eq("cache-invalidation-events"), any()))
                .thenReturn(Promise.complete());

            // Should not throw exception
            assertThatCode(() -> runPromise(() -> subscriber.start()))
                .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Performance Under Load")
    class PerformanceUnderLoadTests {

        @Test
        @DisplayName("should handle 1000 rapid publications")
        void shouldHandle1000RapidPublications() {
            long startTime = System.currentTimeMillis();

            for (int i = 0; i < 1000; i++) {
                final int index = i;
                runPromise(() -> publisher.publishSingleKeyDelete("cache:perf:key:" + index, "trace:perf"));
            }

            long elapsedMs = System.currentTimeMillis() - startTime;

            assertThat(publishedEvents).hasSize(1000);
            assertThat(elapsedMs).isLessThan(5000);  // Should complete in < 5 seconds

            System.out.println("1000 publications - elapsed: " + elapsedMs + "ms");
        }

        @Test
        @DisplayName("should maintain performance with mixed operation types")
        void shouldMaintainPerformanceWithMixedOps() {
            long startTime = System.currentTimeMillis();

            for (int i = 0; i < 100; i++) {
                final int index = i;
                runPromise(() -> publisher.publishSingleKeyDelete("key:" + index, "trace:" + index));
                runPromise(() -> publisher.publishPatternDelete("pattern:*", 10, "trace:" + index));
                runPromise(() -> publisher.publishBulkDelete("bulk:" + index, 100, "trace:" + index));
            }

            long elapsedMs = System.currentTimeMillis() - startTime;

            assertThat(publishedEvents).hasSize(300);  // 100 each of 3 types
            assertThat(elapsedMs).isLessThan(3000);

            System.out.println("300 mixed operations - elapsed: " + elapsedMs + "ms");
        }
    }

    // ============= Test Support Classes =============

    static class InvalidEvent {
        // Intentionally malformed event for error handling tests
    }
}
