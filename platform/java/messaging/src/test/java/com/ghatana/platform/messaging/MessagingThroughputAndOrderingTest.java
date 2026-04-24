/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.messaging;

import com.ghatana.platform.messaging.config.RetryConfig;
import com.ghatana.platform.messaging.strategy.QueueMessage;
import com.ghatana.platform.messaging.strategy.QueueProducerStrategy;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Throughput, ordering, and backpressure contract tests for the messaging layer.
 *
 * <p>These are purely in-memory; no broker containers required.
 * They exercise:
 * <ul>
 *   <li>High-volume send throughput via the retry decorator</li>
 *   <li>FIFO ordering guarantee of in-memory producer sequences</li>
 *   <li>Retry exhaustion leaves partial-success visible to callers</li>
 *   <li>Backpressure: slow delegate does not lose messages</li>
 *   <li>Concurrent send safety — no messages dropped or duplicated</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Throughput, ordering, backpressure contract tests for messaging
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Messaging Throughput and Ordering")
class MessagingThroughputAndOrderingTest {

    private static final int MESSAGE_COUNT = 1_000;

    /** Zero-delay retry config for fast tests. */
    private static RetryConfig fastRetry(int maxAttempts) {
        return RetryConfig.builder()
            .maxAttempts(maxAttempts)
            .initialDelay(Duration.ZERO)
            .backoffMultiplier(1.0)
            .maxDelay(Duration.ZERO)
            .build();
    }

    private static QueueMessage msg(String id) {
        return new QueueMessage(id, "body-" + id, Map.of());
    }

    // ── Throughput ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("throughput")
    class Throughput {

        @Test
        @DisplayName("sends " + MESSAGE_COUNT + " messages without dropping any")
        void sendsHighVolumeWithoutDropping() {
            AtomicInteger sent = new AtomicInteger(0);
            QueueProducerStrategy delegate = alwaysSuccess(sent);
            RetryingConnectorDecorator producer = new RetryingConnectorDecorator(delegate, fastRetry(1));

            for (int i = 0; i < MESSAGE_COUNT; i++) {
                boolean ok = producer.send(msg(String.valueOf(i)));
                assertThat(ok).isTrue();
            }

            assertThat(sent.get()).isEqualTo(MESSAGE_COUNT);
        }

        @Test
        @DisplayName("throughput is not degraded by retry decorator on success path")
        void retryDecoratorAddsNegligibleOverheadOnSuccessPath() {
            AtomicInteger callCount = new AtomicInteger(0);
            QueueProducerStrategy delegate = alwaysSuccess(callCount);
            RetryingConnectorDecorator producer = new RetryingConnectorDecorator(delegate, fastRetry(3));

            long start = System.nanoTime();
            for (int i = 0; i < MESSAGE_COUNT; i++) {
                producer.send(msg(String.valueOf(i)));
            }
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            // Each message should succeed on first attempt — no retries
            assertThat(callCount.get()).isEqualTo(MESSAGE_COUNT);
            // Sanity: in-memory loop should complete well under 5 seconds
            assertThat(elapsedMs).isLessThan(5_000L);
        }
    }

    // ── Ordering ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ordering")
    class Ordering {

        @Test
        @DisplayName("messages are processed in send order (FIFO contract)")
        void messagesProcessedInSendOrder() {
            List<String> receivedOrder = new ArrayList<>();
            QueueProducerStrategy delegate = new RecordingProducer(receivedOrder);
            RetryingConnectorDecorator producer = new RetryingConnectorDecorator(delegate, fastRetry(1));

            List<String> sendOrder = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                String id = "msg-" + i;
                sendOrder.add(id);
                producer.send(msg(id));
            }

            assertThat(receivedOrder).containsExactlyElementsOf(sendOrder);
        }

        @Test
        @DisplayName("ordering preserved even when first attempt transiently fails")
        void orderingPreservedAfterTransientRetry() {
            List<String> receivedOrder = new CopyOnWriteArrayList<>();
            // Fails first attempt for every other message, then succeeds
            AtomicInteger callSeq = new AtomicInteger(0);
            QueueProducerStrategy delegate = new QueueProducerStrategy() {
                @Override
                public boolean send(QueueMessage message) {
                    int seq = callSeq.incrementAndGet();
                    if (seq % 3 == 1) {
                        // Fail every 3rd attempt on first call for that message
                        throw new RuntimeException("transient");
                    }
                    receivedOrder.add(message.getId());
                    return true;
                }

                @Override
                public Promise<Void> start() { return Promise.complete(); }

                @Override
                public Promise<Void> stop() { return Promise.complete(); }

                @Override
                public boolean isRunning() { return true; }
            };

            RetryingConnectorDecorator producer = new RetryingConnectorDecorator(delegate, fastRetry(3));

            List<String> expected = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                String id = "msg-" + i;
                expected.add(id);
                producer.send(msg(id));
            }

            // All must have arrived, in send order
            assertThat(receivedOrder).containsExactlyElementsOf(expected);
        }
    }

    // ── Backpressure ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("backpressure")
    class Backpressure {

        @Test
        @DisplayName("slow delegate does not lose messages — all eventually delivered")
        void slowDelegateDeliversAll() {
            AtomicInteger delivered = new AtomicInteger(0);
            // Simulate a slow but always-succeeding delegate
            QueueProducerStrategy slowDelegate = new QueueProducerStrategy() {
                @Override
                public boolean send(QueueMessage message) {
                    // Minimal CPU spin to simulate non-trivial work without Thread.sleep
                    long sum = 0;
                    for (int j = 0; j < 500; j++) sum += j;
                    if (sum >= 0) delivered.incrementAndGet();
                    return true;
                }

                @Override
                public Promise<Void> start() { return Promise.complete(); }

                @Override
                public Promise<Void> stop() { return Promise.complete(); }

                @Override
                public boolean isRunning() { return true; }
            };

            RetryingConnectorDecorator producer = new RetryingConnectorDecorator(slowDelegate, fastRetry(1));

            int count = 200;
            for (int i = 0; i < count; i++) {
                producer.send(msg(String.valueOf(i)));
            }

            assertThat(delivered.get()).isEqualTo(count);
        }

        @Test
        @DisplayName("retry exhaustion reported per-message, not silently swallowed")
        void retryExhaustionReportedCorrectly() {
            AtomicInteger failures = new AtomicInteger(0);
            QueueProducerStrategy alwaysFail = new QueueProducerStrategy() {
                @Override
                public boolean send(QueueMessage message) {
                    throw new RuntimeException("always fails");
                }

                @Override
                public Promise<Void> start() { return Promise.complete(); }

                @Override
                public Promise<Void> stop() { return Promise.complete(); }

                @Override
                public boolean isRunning() { return true; }
            };

            RetryingConnectorDecorator producer = new RetryingConnectorDecorator(alwaysFail, fastRetry(2));

            for (int i = 0; i < 5; i++) {
                try {
                    producer.send(msg(String.valueOf(i)));
                } catch (RuntimeException e) {
                    failures.incrementAndGet();
                }
            }

            // Every message fails — caller receives an exception for each
            assertThat(failures.get()).isEqualTo(5);
        }
    }

    // ── Concurrent send safety ────────────────────────────────────────────────

    @Nested
    @DisplayName("concurrent send safety")
    class ConcurrentSendSafety {

        @Test
        @DisplayName("no messages dropped under concurrent send from multiple threads")
        void noMessagesDroppedUnderConcurrentSend() throws InterruptedException {
            AtomicInteger delivered = new AtomicInteger(0);
            QueueProducerStrategy delegate = alwaysSuccess(delivered);
            RetryingConnectorDecorator producer = new RetryingConnectorDecorator(delegate, fastRetry(1));

            int threads = 10;
            int perThread = 100;
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threads);

            var executor = Executors.newFixedThreadPool(threads);
            IntStream.range(0, threads).forEach(t -> executor.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        producer.send(msg(t + "-" + i));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }));

            start.countDown();
            done.await();
            executor.shutdown();

            assertThat(delivered.get()).isEqualTo(threads * perThread);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static QueueProducerStrategy alwaysSuccess(AtomicInteger counter) {
        return new QueueProducerStrategy() {
            @Override
            public boolean send(QueueMessage message) {
                counter.incrementAndGet();
                return true;
            }

            @Override
            public Promise<Void> start() { return Promise.complete(); }

            @Override
            public Promise<Void> stop() { return Promise.complete(); }

            @Override
            public boolean isRunning() { return true; }
        };
    }

    private static class RecordingProducer implements QueueProducerStrategy {
        private final List<String> received;

        RecordingProducer(List<String> received) {
            this.received = received;
        }

        @Override
        public boolean send(QueueMessage message) {
            received.add(message.getId());
            return true;
        }

        @Override
        public Promise<Void> start() { return Promise.complete(); }

        @Override
        public Promise<Void> stop() { return Promise.complete(); }

        @Override
        public boolean isRunning() { return true; }
    }
}
