package com.ghatana.platform.messaging.resilience;

import com.ghatana.platform.messaging.strategy.QueueMessage;
import com.ghatana.platform.messaging.strategy.QueueProducerStrategy;
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
@DisplayName("CircuitBreakerConnector [GH-90000]")
class CircuitBreakerConnectorTest {

    private static final QueueMessage MESSAGE = new QueueMessage("key", "body", Map.of()); // GH-90000

    @Test
    @DisplayName("opens after repeated send failures and fails fast afterwards [GH-90000]")
    void opensAfterRepeatedFailures() { // GH-90000
        AtomicInteger calls = new AtomicInteger(); // GH-90000
        QueueProducerStrategy delegate = new StubProducer() { // GH-90000
            @Override
            public boolean send(QueueMessage message) { // GH-90000
                calls.incrementAndGet(); // GH-90000
                return false;
            }
        };

        CircuitBreakerConnector connector = CircuitBreakerConnector.builder() // GH-90000
            .delegate(delegate) // GH-90000
            .name("test-cb [GH-90000]")
            .failureThreshold(2) // GH-90000
            .successThreshold(1) // GH-90000
            .resetTimeout(Duration.ofMinutes(1)) // GH-90000
            .build(); // GH-90000

        assertThat(connector.send(MESSAGE)).isFalse(); // GH-90000
        assertThat(connector.send(MESSAGE)).isFalse(); // GH-90000
        assertThat(connector.state()).isEqualTo(CircuitBreaker.State.OPEN); // GH-90000
        assertThat(connector.send(MESSAGE)).isFalse(); // GH-90000
        assertThat(calls.get()).isEqualTo(2); // GH-90000
    }

    @Test
    @DisplayName("delegates successful sends while circuit is closed [GH-90000]")
    void delegatesSuccessfulSends() { // GH-90000
        AtomicInteger calls = new AtomicInteger(); // GH-90000
        QueueProducerStrategy delegate = new StubProducer() { // GH-90000
            @Override
            public boolean send(QueueMessage message) { // GH-90000
                calls.incrementAndGet(); // GH-90000
                return true;
            }
        };

        CircuitBreakerConnector connector = CircuitBreakerConnector.builder() // GH-90000
            .delegate(delegate) // GH-90000
            .name("test-cb-success [GH-90000]")
            .build(); // GH-90000

        assertThat(connector.send(MESSAGE)).isTrue(); // GH-90000
        assertThat(connector.state()).isEqualTo(CircuitBreaker.State.CLOSED); // GH-90000
        assertThat(calls.get()).isEqualTo(1); // GH-90000
    }

    private static class StubProducer implements QueueProducerStrategy {
        @Override
        public boolean send(QueueMessage message) { // GH-90000
            return true;
        }

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
