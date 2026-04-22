package com.ghatana.platform.messaging;

import com.ghatana.platform.messaging.config.RetryConfig;
import com.ghatana.platform.messaging.strategy.QueueMessage;
import com.ghatana.platform.messaging.strategy.QueueProducerStrategy;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link RetryingConnectorDecorator}.
 *
 * <p>Uses fast retry config (zero delays) to keep tests fast. // GH-90000
 *
 * @doc.type class
 * @doc.purpose Verify retry logic, backoff, and delegation in RetryingConnectorDecorator
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("RetryingConnectorDecorator [GH-90000]")
class RetryingConnectorDecoratorTest {

    private static final QueueMessage MSG = new QueueMessage("key", "body", Map.of()); // GH-90000

    /** No-delay RetryConfig for fast tests. */
    private static RetryConfig fastRetry(int maxAttempts) { // GH-90000
        return RetryConfig.builder() // GH-90000
            .maxAttempts(maxAttempts) // GH-90000
            .initialDelay(Duration.ZERO) // GH-90000
            .backoffMultiplier(1.0) // GH-90000
            .maxDelay(Duration.ZERO) // GH-90000
            .build(); // GH-90000
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Success path
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Successful send [GH-90000]")
    class SuccessTests {

        @Test
        @DisplayName("delegates to underlying strategy when first attempt succeeds [GH-90000]")
        void shouldDelegateAndSucceedOnFirstAttempt() { // GH-90000
            AtomicInteger callCount = new AtomicInteger(); // GH-90000
            QueueProducerStrategy delegate = alwaysSucceed(callCount); // GH-90000

            RetryingConnectorDecorator decorator = new RetryingConnectorDecorator( // GH-90000
                delegate, fastRetry(3)); // GH-90000

            boolean result = decorator.send(MSG); // GH-90000

            assertThat(result).isTrue(); // GH-90000
            assertThat(callCount.get()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("succeeds on second attempt after first returns false [GH-90000]")
        void shouldRetryOnFalseReturn() { // GH-90000
            AtomicInteger callCount = new AtomicInteger(); // GH-90000
            // Returns false on first call, true on second
            QueueProducerStrategy delegate = new StubProducer() { // GH-90000
                @Override public boolean send(QueueMessage msg) { // GH-90000
                    return callCount.incrementAndGet() > 1; // GH-90000
                }
            };

            RetryingConnectorDecorator decorator = new RetryingConnectorDecorator( // GH-90000
                delegate, fastRetry(3)); // GH-90000

            boolean result = decorator.send(MSG); // GH-90000

            assertThat(result).isTrue(); // GH-90000
            assertThat(callCount.get()).isEqualTo(2); // GH-90000
        }

        @Test
        @DisplayName("succeeds after exception on first attempt, success on second [GH-90000]")
        void shouldRetryAfterException() { // GH-90000
            AtomicInteger callCount = new AtomicInteger(); // GH-90000
            QueueProducerStrategy delegate = new StubProducer() { // GH-90000
                @Override public boolean send(QueueMessage msg) { // GH-90000
                    int attempt = callCount.incrementAndGet(); // GH-90000
                    if (attempt == 1) { // GH-90000
                        throw new RuntimeException("transient error [GH-90000]");
                    }
                    return true;
                }
            };

            RetryingConnectorDecorator decorator = new RetryingConnectorDecorator( // GH-90000
                delegate, fastRetry(3)); // GH-90000

            boolean result = decorator.send(MSG); // GH-90000

            assertThat(result).isTrue(); // GH-90000
            assertThat(callCount.get()).isEqualTo(2); // GH-90000
        }

        @Test
        @DisplayName("default sendBatch() uses retrying send() for each message [GH-90000]")
        void shouldApplyRetryingSendToBatchMessages() { // GH-90000
            AtomicInteger callCount = new AtomicInteger(); // GH-90000
            QueueProducerStrategy delegate = new StubProducer() { // GH-90000
                @Override public boolean send(QueueMessage msg) { // GH-90000
                    return callCount.incrementAndGet() % 2 == 0; // GH-90000
                }
            };

            RetryingConnectorDecorator decorator = new RetryingConnectorDecorator( // GH-90000
                delegate, fastRetry(2)); // GH-90000

            boolean result = decorator.sendBatch(List.of(MSG, MSG)); // GH-90000

            assertThat(result).isTrue(); // GH-90000
            assertThat(callCount.get()).isEqualTo(4); // GH-90000
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Exhausted retries
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Exhausted retries [GH-90000]")
    class ExhaustedRetryTests {

        @Test
        @DisplayName("returns false after all attempts return false [GH-90000]")
        void shouldReturnFalseAfterAllAttemptsReturnFalse() { // GH-90000
            AtomicInteger callCount = new AtomicInteger(); // GH-90000
            QueueProducerStrategy delegate = new StubProducer() { // GH-90000
                @Override public boolean send(QueueMessage msg) { // GH-90000
                    callCount.incrementAndGet(); return false; // GH-90000
                }
            };

            RetryingConnectorDecorator decorator = new RetryingConnectorDecorator( // GH-90000
                delegate, fastRetry(3)); // GH-90000

            boolean result = decorator.send(MSG); // GH-90000

            assertThat(result).isFalse(); // GH-90000
            assertThat(callCount.get()).isEqualTo(3); // exactly maxAttempts // GH-90000
        }

        @Test
        @DisplayName("throws RuntimeException after all attempts throw [GH-90000]")
        void shouldThrowAfterAllAttemptsThrow() { // GH-90000
            AtomicInteger callCount = new AtomicInteger(); // GH-90000
            QueueProducerStrategy delegate = new StubProducer() { // GH-90000
                @Override public boolean send(QueueMessage msg) { // GH-90000
                    callCount.incrementAndGet(); // GH-90000
                    throw new RuntimeException("persistent failure [GH-90000]");
                }
            };

            RetryingConnectorDecorator decorator = new RetryingConnectorDecorator( // GH-90000
                delegate, fastRetry(2)); // GH-90000

            assertThatThrownBy(() -> decorator.send(MSG)) // GH-90000
                .isInstanceOf(RuntimeException.class) // GH-90000
                .hasMessageContaining("persistent failure [GH-90000]");

            assertThat(callCount.get()).isEqualTo(2); // exactly maxAttempts // GH-90000
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Lifecycle delegation
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Lifecycle delegation [GH-90000]")
    class LifecycleDelegationTests {

        @Test
        @DisplayName("start() delegates to underlying strategy [GH-90000]")
        void startDelegates() { // GH-90000
            AtomicInteger startCount = new AtomicInteger(); // GH-90000
            QueueProducerStrategy delegate = new StubProducer() { // GH-90000
                @Override
                public Promise<Void> start() { // GH-90000
                    startCount.incrementAndGet(); // GH-90000
                    return Promise.complete(); // GH-90000
                }
            };

            new RetryingConnectorDecorator(delegate, fastRetry(1)).start(); // GH-90000
            assertThat(startCount.get()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("stop() delegates to underlying strategy [GH-90000]")
        void stopDelegates() { // GH-90000
            AtomicInteger stopCount = new AtomicInteger(); // GH-90000
            QueueProducerStrategy delegate = new StubProducer() { // GH-90000
                @Override
                public Promise<Void> stop() { // GH-90000
                    stopCount.incrementAndGet(); // GH-90000
                    return Promise.complete(); // GH-90000
                }
            };

            new RetryingConnectorDecorator(delegate, fastRetry(1)).stop(); // GH-90000
            assertThat(stopCount.get()).isEqualTo(1); // GH-90000
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Null guard
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("constructor rejects null delegate [GH-90000]")
    void shouldRejectNullDelegate() { // GH-90000
        assertThatThrownBy(() -> new RetryingConnectorDecorator(null, RetryConfig.DEFAULT)) // GH-90000
            .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    @DisplayName("constructor rejects null retryConfig [GH-90000]")
    void shouldRejectNullRetryConfig() { // GH-90000
        assertThatThrownBy(() -> new RetryingConnectorDecorator(new StubProducer(), null)) // GH-90000
            .isInstanceOf(NullPointerException.class); // GH-90000
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private static QueueProducerStrategy alwaysSucceed(AtomicInteger callCount) { // GH-90000
        return new StubProducer() { // GH-90000
            @Override
            public boolean send(QueueMessage msg) { // GH-90000
                callCount.incrementAndGet(); // GH-90000
                return true;
            }
        };
    }

    /** Minimal no-op base – avoids lambda boilerplate for partial overrides. */
    private static class StubProducer implements QueueProducerStrategy {
        @Override
        public boolean send(QueueMessage message) { return true; } // GH-90000
        @Override
        public Promise<Void> start() { return Promise.complete(); } // GH-90000
        @Override
        public Promise<Void> stop()  { return Promise.complete(); } // GH-90000
        @Override
        public boolean isRunning()   { return true; } // GH-90000
    }
}
