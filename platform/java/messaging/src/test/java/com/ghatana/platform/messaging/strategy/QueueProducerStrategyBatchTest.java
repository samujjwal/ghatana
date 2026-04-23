package com.ghatana.platform.messaging.strategy;

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
    void shouldDelegateEachMessageThroughSend() { // GH-90000
        AtomicInteger sendCount = new AtomicInteger(); // GH-90000
        QueueProducerStrategy strategy = new StubProducer() { // GH-90000
            @Override
            public boolean send(QueueMessage message) { // GH-90000
                sendCount.incrementAndGet(); // GH-90000
                return true;
            }
        };

        boolean result = strategy.sendBatch(List.of( // GH-90000
            new QueueMessage("k1", "p1", Map.of()), // GH-90000
            new QueueMessage("k2", "p2", Map.of()), // GH-90000
            new QueueMessage("k3", "p3", Map.of()) // GH-90000
        ));

        assertThat(result).isTrue(); // GH-90000
        assertThat(sendCount.get()).isEqualTo(3); // GH-90000
    }

    @Test
    @DisplayName("default sendBatch() stops when a message send fails")
    void shouldStopWhenAnyMessageFails() { // GH-90000
        AtomicInteger sendCount = new AtomicInteger(); // GH-90000
        QueueProducerStrategy strategy = new StubProducer() { // GH-90000
            @Override
            public boolean send(QueueMessage message) { // GH-90000
                return sendCount.incrementAndGet() < 3; // GH-90000
            }
        };

        boolean result = strategy.sendBatch(List.of( // GH-90000
            new QueueMessage("k1", "p1", Map.of()), // GH-90000
            new QueueMessage("k2", "p2", Map.of()), // GH-90000
            new QueueMessage("k3", "p3", Map.of()), // GH-90000
            new QueueMessage("k4", "p4", Map.of()) // GH-90000
        ));

        assertThat(result).isFalse(); // GH-90000
        assertThat(sendCount.get()).isEqualTo(3); // GH-90000
    }

    private abstract static class StubProducer implements QueueProducerStrategy {
        @Override
        public Promise<Void> start() { // GH-90000
            return Promise.complete(); // GH-90000
        }

        @Override
        public Promise<Void> stop() { // GH-90000
            return Promise.complete(); // GH-90000
        }

        @Override
        public boolean isRunning() { // GH-90000
            return true;
        }
    }
}
