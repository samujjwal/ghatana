package com.ghatana.aep.connector.resilience;

import com.ghatana.aep.connector.strategy.QueueMessage;
import com.ghatana.aep.connector.strategy.QueueProducerStrategy;
import com.ghatana.platform.resilience.CircuitBreaker;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CircuitBreakerConnector}.
 *
 * @doc.type class
 * @doc.purpose Verify circuit breaker state transitions for queue producer resilience
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("CircuitBreakerConnector")
class CircuitBreakerConnectorTest {

    private static final QueueMessage MESSAGE = new QueueMessage("key", "body", Map.of());

    @Test
    @DisplayName("opens after repeated send failures and fails fast afterwards")
    void opensAfterRepeatedFailures() {
        AtomicInteger calls = new AtomicInteger();
        QueueProducerStrategy delegate = new StubProducer() {
            @Override
            public boolean send(QueueMessage message) {
                calls.incrementAndGet();
                return false;
            }
        };

        CircuitBreakerConnector connector = CircuitBreakerConnector.builder()
            .delegate(delegate)
            .name("test-cb")
            .failureThreshold(2)
            .successThreshold(1)
            .resetTimeout(Duration.ofMinutes(1))
            .build();

        assertThat(connector.send(MESSAGE)).isFalse();
        assertThat(connector.send(MESSAGE)).isFalse();
        assertThat(connector.state()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(connector.send(MESSAGE)).isFalse();
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("delegates successful sends while circuit is closed")
    void delegatesSuccessfulSends() {
        AtomicInteger calls = new AtomicInteger();
        QueueProducerStrategy delegate = new StubProducer() {
            @Override
            public boolean send(QueueMessage message) {
                calls.incrementAndGet();
                return true;
            }
        };

        CircuitBreakerConnector connector = CircuitBreakerConnector.builder()
            .delegate(delegate)
            .name("test-cb-success")
            .build();

        assertThat(connector.send(MESSAGE)).isTrue();
        assertThat(connector.state()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(calls.get()).isEqualTo(1);
    }

    private static class StubProducer implements QueueProducerStrategy {
        @Override
        public boolean send(QueueMessage message) {
            return true;
        }

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
