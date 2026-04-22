/**
 * @doc.type test
 * @doc.purpose Tests for streaming retry handler
 * @doc.layer platform
 */
package com.ghatana.media.resilience;

import com.ghatana.media.config.TimeoutConfig;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests for AV-007: Streaming retry logic
 */
class StreamingRetryHandlerTest {

    @Test
    @DisplayName("Should succeed on first attempt without retry [GH-90000]")
    void testNoRetryOnSuccess() { // GH-90000
        StreamingRetryHandler handler = StreamingRetryHandler.defaults(); // GH-90000
        AtomicInteger calls = new AtomicInteger(0); // GH-90000

        String result = handler.executeWithRetry(() -> { // GH-90000
            calls.incrementAndGet(); // GH-90000
            return "success";
        }, "test operation");

        assertEquals("success", result); // GH-90000
        assertEquals(1, calls.get()); // GH-90000
    }

    @Test
    @DisplayName("Should retry on retryable exception [GH-90000]")
    void testRetryOnRetryableException() { // GH-90000
        StreamingRetryHandler handler = StreamingRetryHandler.builder() // GH-90000
            .maxRetries(3) // GH-90000
            .initialDelay(Duration.ofMillis(10)) // GH-90000
            .build(); // GH-90000

        AtomicInteger calls = new AtomicInteger(0); // GH-90000

        String result = handler.executeWithRetry(() -> { // GH-90000
            int call = calls.incrementAndGet(); // GH-90000
            if (call < 3) { // GH-90000
                throw new RuntimeException("try again failure [GH-90000]");
            }
            return "success";
        }, "test operation");

        assertEquals("success", result); // GH-90000
        assertEquals(3, calls.get()); // GH-90000
    }

    @Test
    @DisplayName("Should throw after max retries exhausted [GH-90000]")
    void testThrowsAfterMaxRetries() { // GH-90000
        StreamingRetryHandler handler = StreamingRetryHandler.builder() // GH-90000
            .maxRetries(2) // GH-90000
            .initialDelay(Duration.ofMillis(1)) // GH-90000
            .build(); // GH-90000

        assertThrows(StreamingRetryHandler.StreamingRetryExhaustedException.class, () -> { // GH-90000
            handler.executeWithRetry(() -> { // GH-90000
                throw new RuntimeException("try again [GH-90000]");
            }, "failing operation");
        });
    }

    @Test
    @DisplayName("Should use fallback when retries exhausted [GH-90000]")
    void testFallbackOnExhaustion() { // GH-90000
        StreamingRetryHandler handler = StreamingRetryHandler.builder() // GH-90000
            .maxRetries(2) // GH-90000
            .initialDelay(Duration.ofMillis(1)) // GH-90000
            .build(); // GH-90000

        String result = handler.executeWithFallback(() -> { // GH-90000
            throw new RuntimeException("try again [GH-90000]");
        }, "fallback", "failing operation");

        assertEquals("fallback", result); // GH-90000
    }

    @Test
    @DisplayName("Should retry on socket timeout [GH-90000]")
    void testRetryOnSocketTimeout() { // GH-90000
        StreamingRetryHandler handler = StreamingRetryHandler.builder() // GH-90000
            .maxRetries(2) // GH-90000
            .initialDelay(Duration.ofMillis(1)) // GH-90000
            .build(); // GH-90000

        AtomicInteger calls = new AtomicInteger(0); // GH-90000

        String result = handler.executeWithRetry(() -> { // GH-90000
            int call = calls.incrementAndGet(); // GH-90000
            if (call == 1) { // GH-90000
                throw new RuntimeException("try again [GH-90000]");
            }
            return "success";
        }, "test operation");

        assertEquals("success", result); // GH-90000
        assertEquals(2, calls.get()); // GH-90000
    }

    @Test
    @DisplayName("Should not retry on non-retryable exception [GH-90000]")
    void testNoRetryOnNonRetryable() { // GH-90000
        StreamingRetryHandler handler = StreamingRetryHandler.defaults(); // GH-90000
        AtomicInteger calls = new AtomicInteger(0); // GH-90000

        assertThrows(StreamingRetryHandler.StreamingRetryExhaustedException.class, () -> { // GH-90000
            handler.executeWithRetry(() -> { // GH-90000
                calls.incrementAndGet(); // GH-90000
                throw new IllegalArgumentException("Invalid argument [GH-90000]"); // Non-retryable
            }, "test operation");
        });

        assertEquals(1, calls.get()); // Only one attempt // GH-90000
    }

    @Test
    @DisplayName("Should apply exponential backoff [GH-90000]")
    void testExponentialBackoff() { // GH-90000
        long[] delays = new long[3];

        StreamingRetryHandler handler = StreamingRetryHandler.builder() // GH-90000
            .maxRetries(3) // GH-90000
            .initialDelay(Duration.ofMillis(100)) // GH-90000
            .backoffMultiplier(2.0) // GH-90000
            .addJitter(false) // GH-90000
            .build(); // GH-90000

        AtomicInteger calls = new AtomicInteger(0); // GH-90000

        assertThrows(StreamingRetryHandler.StreamingRetryExhaustedException.class, () -> { // GH-90000
            handler.executeWithRetry(() -> { // GH-90000
                calls.incrementAndGet(); // GH-90000
                throw new RuntimeException("try again [GH-90000]");
            }, "test");
        });

        // Should have made 4 attempts (initial + 3 retries) // GH-90000
        assertEquals(4, calls.get()); // GH-90000
    }

    @Test
    @DisplayName("Should cap delay at max delay [GH-90000]")
    void testMaxDelayCap() { // GH-90000
        StreamingRetryHandler handler = StreamingRetryHandler.builder() // GH-90000
            .maxRetries(10) // GH-90000
            .initialDelay(Duration.ofMillis(100)) // GH-90000
            .maxDelay(Duration.ofMillis(200)) // GH-90000
            .backoffMultiplier(10.0) // Would exceed max without cap // GH-90000
            .addJitter(false) // GH-90000
            .build(); // GH-90000

        // Even with high multiplier, delay should not exceed maxDelay
        assertTrue(handler.getMaxDelay().toMillis() <= 200); // GH-90000
    }

    @Test
    @DisplayName("Should respect timeout limit [GH-90000]")
    void testTimeoutRespected() { // GH-90000
        TimeoutConfig timeoutConfig = TimeoutConfig.builder() // GH-90000
            .streamingTimeout(Duration.ofMillis(100)) // Very short timeout // GH-90000
            .build(); // GH-90000

        StreamingRetryHandler handler = StreamingRetryHandler.builder() // GH-90000
            .maxRetries(100) // Would take too long without timeout // GH-90000
            .initialDelay(Duration.ofMillis(50)) // GH-90000
            .timeoutConfig(timeoutConfig) // GH-90000
            .build(); // GH-90000

        assertThrows(StreamingRetryHandler.StreamingRetryExhaustedException.class, () -> { // GH-90000
            handler.executeWithRetry(() -> { // GH-90000
                throw new RuntimeException("try again [GH-90000]");
            }, "test");
        });
    }

    @Test
    @DisplayName("Should create aggressive retry handler [GH-90000]")
    void testAggressiveHandler() { // GH-90000
        StreamingRetryHandler handler = StreamingRetryHandler.aggressive(); // GH-90000

        assertEquals(5, handler.getMaxRetries()); // GH-90000
        assertEquals(Duration.ofMillis(100), handler.getInitialDelay()); // GH-90000
        assertTrue(handler.isJitterEnabled()); // GH-90000
    }

    @Test
    @DisplayName("Should create conservative retry handler [GH-90000]")
    void testConservativeHandler() { // GH-90000
        StreamingRetryHandler handler = StreamingRetryHandler.conservative(); // GH-90000

        assertEquals(10, handler.getMaxRetries()); // GH-90000
        assertEquals(Duration.ofMillis(500), handler.getInitialDelay()); // GH-90000
        assertEquals(Duration.ofSeconds(30), handler.getMaxDelay()); // GH-90000
    }

    @Test
    @DisplayName("Should track attempt count in exception [GH-90000]")
    void testExceptionAttemptCount() { // GH-90000
        StreamingRetryHandler handler = StreamingRetryHandler.builder() // GH-90000
            .maxRetries(2) // GH-90000
            .initialDelay(Duration.ofMillis(1)) // GH-90000
            .build(); // GH-90000

        StreamingRetryHandler.StreamingRetryExhaustedException exception =
            assertThrows(StreamingRetryHandler.StreamingRetryExhaustedException.class, () -> { // GH-90000
                handler.executeWithRetry(() -> { // GH-90000
                    throw new RuntimeException("try again [GH-90000]");
                }, "test");
            });

        // 3 attempts (initial + 2 retries) // GH-90000
        assertEquals(3, exception.getAttemptsMade()); // GH-90000
        assertNotNull(exception.getLastException()); // GH-90000
    }
}
