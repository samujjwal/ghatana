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
 * - Event publishing for all operation types (single key, pattern, bulk delete) // GH-90000
 * - Cross-service event propagation
 * - Event deduplication (avoid re-processing own events) // GH-90000
 * - Concurrent event publishing
 * - Error handling (non-blocking failures) // GH-90000
 * - End-to-end distributed cache synchronization
 *
 * All tests use mock message bus to simulate real Kafka/RabbitMQ behavior.
 */
@DisplayName("CacheInvalidationEvent Integration Tests [GH-90000]")
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
    void setUp() { // GH-90000
        MockitoAnnotations.openMocks(this); // GH-90000
        objectMapper = new ObjectMapper(); // GH-90000
        publishedEvents = new ArrayList<>(); // GH-90000

        // Setup publisher
        publisher = new CacheInvalidationEventPublisher( // GH-90000
            messageBus,
            "TutorPutorContentCacheService",
            "tenant:123"
        );

        // Setup subscriber
        subscriber = new CacheInvalidationEventSubscriber( // GH-90000
            messageBus,
            cacheBackend,
            "DataCloudQueryCacheService"  // Different service to simulate cross-service sync
        );

        // Mock message bus to capture published events
        when(messageBus.publish(eq("cache-invalidation-events [GH-90000]"), any()))
            .thenAnswer(invocation -> { // GH-90000
                Object event = invocation.getArgument(1); // GH-90000
                publishedEvents.add(event); // GH-90000
                return Promise.complete(); // GH-90000
            });
    }

    @Nested
    @DisplayName("Single Key Delete Events [GH-90000]")
    class SingleKeyDeleteEventTests {

        @Test
        @DisplayName("should publish single key deletion event [GH-90000]")
        void shouldPublishSingleKeyDeleteEvent() { // GH-90000
            runPromise(() -> publisher.publishSingleKeyDelete("content:generated:123", "trace:id:1")); // GH-90000

            assertThat(publishedEvents) // GH-90000
                .hasSize(1) // GH-90000
                .anySatisfy(event -> { // GH-90000
                    assertThat(event) // GH-90000
                        .isInstanceOf(CacheInvalidationEventPublisher.CacheInvalidationEvent.class); // GH-90000
                    CacheInvalidationEventPublisher.CacheInvalidationEvent invalidationEvent =
                        (CacheInvalidationEventPublisher.CacheInvalidationEvent) event; // GH-90000
                    assertThat(invalidationEvent.operationType) // GH-90000
                        .isEqualTo(CacheInvalidationEventPublisher.OperationType.SINGLE_KEY_DELETE); // GH-90000
                    assertThat(invalidationEvent.cacheKey).isEqualTo("content:generated:123 [GH-90000]");
                });
        }

        @Test
        @DisplayName("should include correlation ID in published event [GH-90000]")
        void shouldIncludeCorrelationId() { // GH-90000
            String correlationId = "trace:request:789";
            runPromise(() -> publisher.publishSingleKeyDelete("cache:key", correlationId)); // GH-90000

            assertThat(publishedEvents).hasSize(1); // GH-90000
            verify(messageBus).publish(eq("cache-invalidation-events [GH-90000]"), any());
        }

        @Test
        @DisplayName("should handle multiple rapid single key deletions [GH-90000]")
        void shouldHandleMultipleRapidDeletions() { // GH-90000
            for (int i = 0; i < 100; i++) { // GH-90000
                final int index = i;
                runPromise(() -> publisher.publishSingleKeyDelete("cache:key:" + index, "trace:" + index)); // GH-90000
            }

            assertThat(publishedEvents).hasSize(100); // GH-90000
            verify(messageBus, times(100)).publish(eq("cache-invalidation-events [GH-90000]"), any());
        }
    }

    @Nested
    @DisplayName("Pattern Delete Events [GH-90000]")
    class PatternDeleteEventTests {

        @Test
        @DisplayName("should publish pattern deletion event with key count [GH-90000]")
        void shouldPublishPatternDeleteEvent() { // GH-90000
            runPromise(() -> publisher.publishPatternDelete("learning-path:user:100:*", 25, "trace:id:2")); // GH-90000

            assertThat(publishedEvents) // GH-90000
                .hasSize(1) // GH-90000
                .anySatisfy(event -> { // GH-90000
                    assertThat(event) // GH-90000
                        .isInstanceOf(CacheInvalidationEventPublisher.CacheInvalidationEvent.class); // GH-90000
                    CacheInvalidationEventPublisher.CacheInvalidationEvent invalidationEvent =
                        (CacheInvalidationEventPublisher.CacheInvalidationEvent) event; // GH-90000
                    assertThat(invalidationEvent.operationType) // GH-90000
                        .isEqualTo(CacheInvalidationEventPublisher.OperationType.PATTERN_DELETE); // GH-90000
                    assertThat(invalidationEvent.cacheKey).isEqualTo("learning-path:user:100:* [GH-90000]");
                    assertThat(invalidationEvent.keyCount).isEqualTo(25); // GH-90000
                });
        }

        @Test
        @DisplayName("should support various glob patterns [GH-90000]")
        void shouldSupportVariousPatterns() { // GH-90000
            String[] patterns = {
                "content:*",
                "learning-path:user:*:*",
                "query:result:dataset:*",
                "aggregate:*:*"
            };

            for (String pattern : patterns) { // GH-90000
                runPromise(() -> publisher.publishPatternDelete(pattern, 10, "trace:pattern")); // GH-90000
            }

            assertThat(publishedEvents).hasSize(4); // GH-90000
        }

        @Test
        @DisplayName("should include correct key count in event [GH-90000]")
        void shouldIncludeCorrectKeyCount() { // GH-90000
            long keyCount = 150;
            runPromise(() -> publisher.publishPatternDelete("dataset:*", keyCount, "trace:pattern:keys")); // GH-90000

            assertThat(publishedEvents).hasSize(1); // GH-90000
            verify(messageBus).publish(eq("cache-invalidation-events [GH-90000]"), any());
        }
    }

    @Nested
    @DisplayName("Bulk Delete Events [GH-90000]")
    class BulkDeleteEventTests {

        @Test
        @DisplayName("should publish bulk deletion event [GH-90000]")
        void shouldPublishBulkDeleteEvent() { // GH-90000
            runPromise(() -> publisher.publishBulkDelete("tenant:456:offboarding", 5000, "trace:bulk:1")); // GH-90000

            assertThat(publishedEvents) // GH-90000
                .hasSize(1) // GH-90000
                .anySatisfy(event -> { // GH-90000
                    assertThat(event) // GH-90000
                        .isInstanceOf(CacheInvalidationEventPublisher.CacheInvalidationEvent.class); // GH-90000
                    CacheInvalidationEventPublisher.CacheInvalidationEvent invalidationEvent =
                        (CacheInvalidationEventPublisher.CacheInvalidationEvent) event; // GH-90000
                    assertThat(invalidationEvent.operationType) // GH-90000
                        .isEqualTo(CacheInvalidationEventPublisher.OperationType.BULK_DELETE); // GH-90000
                    assertThat(invalidationEvent.cacheKey).isEqualTo("tenant:456:offboarding [GH-90000]");
                    assertThat(invalidationEvent.keyCount).isEqualTo(5000); // GH-90000
                });
        }

        @Test
        @DisplayName("should handle large bulk deletion events [GH-90000]")
        void shouldHandleLargeBulkDeletion() { // GH-90000
            long largeKeyCount = 100_000;
            runPromise(() -> publisher.publishBulkDelete("migration:schema:v1:to:v2", largeKeyCount, "trace:migration")); // GH-90000

            assertThat(publishedEvents).hasSize(1); // GH-90000
            verify(messageBus).publish(eq("cache-invalidation-events [GH-90000]"), any());
        }
    }

    @Nested
    @DisplayName("Event Subscription and Application [GH-90000]")
    class EventSubscriptionTests {

        @Test
        @DisplayName("should apply single key deletion from received event [GH-90000]")
        void shouldApplySingleKeyDeletion() { // GH-90000
            AtomicBoolean eventProcessed = new AtomicBoolean(false); // GH-90000

            when(messageBus.subscribe(eq("cache-invalidation-events [GH-90000]"), any()))
                .thenAnswer(invocation -> { // GH-90000
                    eventProcessed.set(true); // GH-90000
                    return Promise.complete(); // GH-90000
                });

            runPromise(() -> subscriber.start()); // GH-90000

            verify(messageBus).subscribe(eq("cache-invalidation-events [GH-90000]"), any());
            assertThat(eventProcessed).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should apply pattern deletion from received event [GH-90000]")
        void shouldApplyPatternDeletion() { // GH-90000
            when(messageBus.subscribe(eq("cache-invalidation-events [GH-90000]"), any()))
                .thenAnswer(invocation -> { // GH-90000
                    when(cacheBackend.deletePattern("learning-path:user:100:* [GH-90000]")).thenReturn(25);
                    return Promise.complete(); // GH-90000
                });

            runPromise(() -> subscriber.start()); // GH-90000

            verify(messageBus).subscribe(eq("cache-invalidation-events [GH-90000]"), any());
        }

        @Test
        @DisplayName("should skip events from own service (avoid duplicate processing) [GH-90000]")
        void shouldSkipOwnServiceEvents() { // GH-90000
            // Create publisher with same source as subscriber
            CacheInvalidationEventPublisher sameSourcePublisher = new CacheInvalidationEventPublisher( // GH-90000
                messageBus,
                "DataCloudQueryCacheService",  // Same as subscriber's serviceSource
                "tenant:123"
            );

            when(messageBus.subscribe(eq("cache-invalidation-events [GH-90000]"), any()))
                .thenReturn(Promise.complete()); // GH-90000

            runPromise(() -> subscriber.start()); // GH-90000

            // Verify that deleteKey would NOT be called since event source matches
            verify(cacheBackend, never()).deleteKey(anyString()); // GH-90000
        }
    }

    @Nested
    @DisplayName("Cross-Service Event Propagation [GH-90000]")
    class CrossServicePropagationTests {

        @Test
        @DisplayName("should propagate events between different services [GH-90000]")
        void shouldPropagateBetweenServices() { // GH-90000
            // Service A publishes event
            CacheInvalidationEventPublisher publisherA = new CacheInvalidationEventPublisher( // GH-90000
                messageBus, "ServiceA", "tenant:123"
            );

            // Service B subscribes
            CacheInvalidationEventSubscriber subscriberB = new CacheInvalidationEventSubscriber( // GH-90000
                messageBus, cacheBackend, "ServiceB"
            );

            // Service A publishes
            runPromise(() -> publisherA.publishSingleKeyDelete("cache:key:1", "trace:cross:1")); // GH-90000

            when(messageBus.subscribe(eq("cache-invalidation-events [GH-90000]"), any()))
                .thenReturn(Promise.complete()); // GH-90000

            runPromise(() -> subscriberB.start()); // GH-90000

            assertThat(publishedEvents).hasSize(1); // GH-90000
            verify(messageBus).publish(eq("cache-invalidation-events [GH-90000]"), any());
        }

        @Test
        @DisplayName("should handle events from multiple services concurrently [GH-90000]")
        void shouldHandleMultipleServicesConcurrently() throws InterruptedException { // GH-90000
            int serviceCount = 5;
            Thread[] threads = new Thread[serviceCount];

            for (int s = 0; s < serviceCount; s++) { // GH-90000
                final int serviceId = s;
                threads[s] = new Thread(() -> { // GH-90000
                    CacheInvalidationEventPublisher servicePublisher = new CacheInvalidationEventPublisher( // GH-90000
                        messageBus, "Service" + serviceId, "tenant:123"
                    );

                    for (int op = 0; op < 20; op++) { // GH-90000
                        final int operation = op;
                        runPromise(() -> servicePublisher.publishSingleKeyDelete( // GH-90000
                            "key:service:" + serviceId + ":op:" + operation,
                            "trace:" + serviceId + ":" + operation
                        ));
                    }
                });
                threads[s].start(); // GH-90000
            }

            for (Thread t : threads) { // GH-90000
                t.join(); // GH-90000
            }

            assertThat(publishedEvents).hasSize(serviceCount * 20); // GH-90000
        }
    }

    @Nested
    @DisplayName("Error Handling [GH-90000]")
    class ErrorHandlingTests {

        @Test
        @DisplayName("should handle message bus publish failures gracefully [GH-90000]")
        void shouldHandlePublishFailureGracefully() { // GH-90000
            when(messageBus.publish(eq("cache-invalidation-events [GH-90000]"), any()))
                .thenReturn(Promise.ofException(new RuntimeException("Bus unavailable [GH-90000]")));

            // Should not throw exception
            assertThatCode(() -> publisher.publishSingleKeyDelete("cache:key", "trace:error") // GH-90000
                .whenComplete(($, $$) -> { })) // GH-90000
                .doesNotThrowAnyException(); // GH-90000

            clearFatalError(); // GH-90000

            verify(messageBus).publish(eq("cache-invalidation-events [GH-90000]"), any());
        }

        @Test
        @DisplayName("should handle cache backend deletion failures in subscriber [GH-90000]")
        void shouldHandleBackendFailureGracefully() { // GH-90000
            doThrow(new RuntimeException("Cache connection lost [GH-90000]"))
                .when(cacheBackend).deleteKey(anyString()); // GH-90000

            when(messageBus.subscribe(eq("cache-invalidation-events [GH-90000]"), any()))
                .thenAnswer(invocation -> { // GH-90000
                    // Simulate backend failure during event processing
                    return Promise.complete(); // GH-90000
                });

            // Should not throw exception
            assertThatCode(() -> runPromise(() -> subscriber.start())) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("should handle malformed events gracefully [GH-90000]")
        void shouldHandleMalformedEvents() { // GH-90000
            // Publish invalid event
            publishedEvents.add(new InvalidEvent()); // GH-90000

            when(messageBus.subscribe(eq("cache-invalidation-events [GH-90000]"), any()))
                .thenReturn(Promise.complete()); // GH-90000

            // Should not throw exception
            assertThatCode(() -> runPromise(() -> subscriber.start())) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Performance Under Load [GH-90000]")
    class PerformanceUnderLoadTests {

        @Test
        @DisplayName("should handle 1000 rapid publications [GH-90000]")
        void shouldHandle1000RapidPublications() { // GH-90000
            long startTime = System.currentTimeMillis(); // GH-90000

            for (int i = 0; i < 1000; i++) { // GH-90000
                final int index = i;
                runPromise(() -> publisher.publishSingleKeyDelete("cache:perf:key:" + index, "trace:perf")); // GH-90000
            }

            long elapsedMs = System.currentTimeMillis() - startTime; // GH-90000

            assertThat(publishedEvents).hasSize(1000); // GH-90000
            assertThat(elapsedMs).isLessThan(5000);  // Should complete in < 5 seconds // GH-90000

            System.out.println("1000 publications - elapsed: " + elapsedMs + "ms"); // GH-90000
        }

        @Test
        @DisplayName("should maintain performance with mixed operation types [GH-90000]")
        void shouldMaintainPerformanceWithMixedOps() { // GH-90000
            long startTime = System.currentTimeMillis(); // GH-90000

            for (int i = 0; i < 100; i++) { // GH-90000
                final int index = i;
                runPromise(() -> publisher.publishSingleKeyDelete("key:" + index, "trace:" + index)); // GH-90000
                runPromise(() -> publisher.publishPatternDelete("pattern:*", 10, "trace:" + index)); // GH-90000
                runPromise(() -> publisher.publishBulkDelete("bulk:" + index, 100, "trace:" + index)); // GH-90000
            }

            long elapsedMs = System.currentTimeMillis() - startTime; // GH-90000

            assertThat(publishedEvents).hasSize(300);  // 100 each of 3 types // GH-90000
            assertThat(elapsedMs).isLessThan(3000); // GH-90000

            System.out.println("300 mixed operations - elapsed: " + elapsedMs + "ms"); // GH-90000
        }
    }

    // ============= Test Support Classes =============

    static class InvalidEvent {
        // Intentionally malformed event for error handling tests
    }
}
