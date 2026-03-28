package com.ghatana.aep.connector.strategy;

import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("QueueProducerStrategy batching")
class QueueProducerStrategyBatchTest {

    @Test
    @DisplayName("default sendBatch() delegates each message through send()")
    void shouldDelegateEachMessageThroughSend() {
        AtomicInteger sendCount = new AtomicInteger();
        QueueProducerStrategy strategy = new StubProducer() {
            @Override
            public boolean send(QueueMessage message) {
                sendCount.incrementAndGet();
                return true;
            }
        };

        boolean result = strategy.sendBatch(List.of(
            new QueueMessage("k1", "p1", Map.of()),
            new QueueMessage("k2", "p2", Map.of()),
            new QueueMessage("k3", "p3", Map.of())
        ));

        assertThat(result).isTrue();
        assertThat(sendCount.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("default sendBatch() stops when a message send fails")
    void shouldStopWhenAnyMessageFails() {
        AtomicInteger sendCount = new AtomicInteger();
        QueueProducerStrategy strategy = new StubProducer() {
            @Override
            public boolean send(QueueMessage message) {
                return sendCount.incrementAndGet() < 3;
            }
        };

        boolean result = strategy.sendBatch(List.of(
            new QueueMessage("k1", "p1", Map.of()),
            new QueueMessage("k2", "p2", Map.of()),
            new QueueMessage("k3", "p3", Map.of()),
            new QueueMessage("k4", "p4", Map.of())
        ));

        assertThat(result).isFalse();
        assertThat(sendCount.get()).isEqualTo(3);
    }

    private abstract static class StubProducer implements QueueProducerStrategy {
        @Override
        public Promise<Void> start() {
            return Promise.complete();
        }

        @Override
        public Promise<Void> stop() {
            return Promise.complete();
        }

        @Override
        public boolean isRunning() {
            return true;
        }
    }
}