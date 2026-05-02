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
 * <p>Uses fast retry config (zero delays) to keep tests fast. 
 *
 * @doc.type class
 * @doc.purpose Verify retry logic, backoff, and delegation in RetryingConnectorDecorator
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("RetryingConnectorDecorator")
class RetryingConnectorDecoratorTest {

    private static final QueueMessage MSG = new QueueMessage("key", "body", Map.of()); 

    /** No-delay RetryConfig for fast tests. */
    private static RetryConfig fastRetry(int maxAttempts) { 
        return RetryConfig.builder() 
            .maxAttempts(maxAttempts) 
            .initialDelay(Duration.ZERO) 
            .backoffMultiplier(1.0) 
            .maxDelay(Duration.ZERO) 
            .build(); 
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Success path
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Successful send")
    class SuccessTests {

        @Test
        @DisplayName("delegates to underlying strategy when first attempt succeeds")
        void shouldDelegateAndSucceedOnFirstAttempt() { 
            AtomicInteger callCount = new AtomicInteger(); 
            QueueProducerStrategy delegate = alwaysSucceed(callCount); 

            RetryingConnectorDecorator decorator = new RetryingConnectorDecorator( 
                delegate, fastRetry(3)); 

            boolean result = decorator.send(MSG); 

            assertThat(result).isTrue(); 
            assertThat(callCount.get()).isEqualTo(1); 
        }

        @Test
        @DisplayName("succeeds on second attempt after first returns false")
        void shouldRetryOnFalseReturn() { 
            AtomicInteger callCount = new AtomicInteger(); 
            // Returns false on first call, true on second
            QueueProducerStrategy delegate = new StubProducer() { 
                @Override public boolean send(QueueMessage msg) { 
                    return callCount.incrementAndGet() > 1; 
                }
            };

            RetryingConnectorDecorator decorator = new RetryingConnectorDecorator( 
                delegate, fastRetry(3)); 

            boolean result = decorator.send(MSG); 

            assertThat(result).isTrue(); 
            assertThat(callCount.get()).isEqualTo(2); 
        }

        @Test
        @DisplayName("succeeds after exception on first attempt, success on second")
        void shouldRetryAfterException() { 
            AtomicInteger callCount = new AtomicInteger(); 
            QueueProducerStrategy delegate = new StubProducer() { 
                @Override public boolean send(QueueMessage msg) { 
                    int attempt = callCount.incrementAndGet(); 
                    if (attempt == 1) { 
                        throw new RuntimeException("transient error");
                    }
                    return true;
                }
            };

            RetryingConnectorDecorator decorator = new RetryingConnectorDecorator( 
                delegate, fastRetry(3)); 

            boolean result = decorator.send(MSG); 

            assertThat(result).isTrue(); 
            assertThat(callCount.get()).isEqualTo(2); 
        }

        @Test
        @DisplayName("default sendBatch() uses retrying send() for each message")
        void shouldApplyRetryingSendToBatchMessages() { 
            AtomicInteger callCount = new AtomicInteger(); 
            QueueProducerStrategy delegate = new StubProducer() { 
                @Override public boolean send(QueueMessage msg) { 
                    return callCount.incrementAndGet() % 2 == 0; 
                }
            };

            RetryingConnectorDecorator decorator = new RetryingConnectorDecorator( 
                delegate, fastRetry(2)); 

            boolean result = decorator.sendBatch(List.of(MSG, MSG)); 

            assertThat(result).isTrue(); 
            assertThat(callCount.get()).isEqualTo(4); 
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Exhausted retries
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Exhausted retries")
    class ExhaustedRetryTests {

        @Test
        @DisplayName("returns false after all attempts return false")
        void shouldReturnFalseAfterAllAttemptsReturnFalse() { 
            AtomicInteger callCount = new AtomicInteger(); 
            QueueProducerStrategy delegate = new StubProducer() { 
                @Override public boolean send(QueueMessage msg) { 
                    callCount.incrementAndGet(); return false; 
                }
            };

            RetryingConnectorDecorator decorator = new RetryingConnectorDecorator( 
                delegate, fastRetry(3)); 

            boolean result = decorator.send(MSG); 

            assertThat(result).isFalse(); 
            assertThat(callCount.get()).isEqualTo(3); // exactly maxAttempts 
        }

        @Test
        @DisplayName("throws RuntimeException after all attempts throw")
        void shouldThrowAfterAllAttemptsThrow() { 
            AtomicInteger callCount = new AtomicInteger(); 
            QueueProducerStrategy delegate = new StubProducer() { 
                @Override public boolean send(QueueMessage msg) { 
                    callCount.incrementAndGet(); 
                    throw new RuntimeException("persistent failure");
                }
            };

            RetryingConnectorDecorator decorator = new RetryingConnectorDecorator( 
                delegate, fastRetry(2)); 

            assertThatThrownBy(() -> decorator.send(MSG)) 
                .isInstanceOf(RuntimeException.class) 
                .hasMessageContaining("persistent failure");

            assertThat(callCount.get()).isEqualTo(2); // exactly maxAttempts 
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Lifecycle delegation
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Lifecycle delegation")
    class LifecycleDelegationTests {

        @Test
        @DisplayName("start() delegates to underlying strategy")
        void startDelegates() { 
            AtomicInteger startCount = new AtomicInteger(); 
            QueueProducerStrategy delegate = new StubProducer() { 
                @Override
                public Promise<Void> start() { 
                    startCount.incrementAndGet(); 
                    return Promise.complete(); 
                }
            };

            new RetryingConnectorDecorator(delegate, fastRetry(1)).start(); 
            assertThat(startCount.get()).isEqualTo(1); 
        }

        @Test
        @DisplayName("stop() delegates to underlying strategy")
        void stopDelegates() { 
            AtomicInteger stopCount = new AtomicInteger(); 
            QueueProducerStrategy delegate = new StubProducer() { 
                @Override
                public Promise<Void> stop() { 
                    stopCount.incrementAndGet(); 
                    return Promise.complete(); 
                }
            };

            new RetryingConnectorDecorator(delegate, fastRetry(1)).stop(); 
            assertThat(stopCount.get()).isEqualTo(1); 
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Null guard
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("constructor rejects null delegate")
    void shouldRejectNullDelegate() { 
        assertThatThrownBy(() -> new RetryingConnectorDecorator(null, RetryConfig.DEFAULT)) 
            .isInstanceOf(NullPointerException.class); 
    }

    @Test
    @DisplayName("constructor rejects null retryConfig")
    void shouldRejectNullRetryConfig() { 
        assertThatThrownBy(() -> new RetryingConnectorDecorator(new StubProducer(), null)) 
            .isInstanceOf(NullPointerException.class); 
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private static QueueProducerStrategy alwaysSucceed(AtomicInteger callCount) { 
        return new StubProducer() { 
            @Override
            public boolean send(QueueMessage msg) { 
                callCount.incrementAndGet(); 
                return true;
            }
        };
    }

    /** Minimal no-op base – avoids lambda boilerplate for partial overrides. */
    private static class StubProducer implements QueueProducerStrategy {
        @Override
        public boolean send(QueueMessage message) { return true; } 
        @Override
        public Promise<Void> start() { return Promise.complete(); } 
        @Override
        public Promise<Void> stop()  { return Promise.complete(); } 
        @Override
        public boolean isRunning()   { return true; } 
    }
}
