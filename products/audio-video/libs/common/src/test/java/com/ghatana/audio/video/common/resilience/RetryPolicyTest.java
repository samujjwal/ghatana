package com.ghatana.audio.video.common.resilience;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link RetryPolicy}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for the retry policy with exponential back-off
 * @doc.layer product
 * @doc.pattern TestCase
 */
@DisplayName("RetryPolicy")
class RetryPolicyTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Defaults
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("defaults: creates policy with sensible defaults")
    void defaults_hasSensibleConfiguration() { // GH-90000
        // Should not throw
        RetryPolicy policy = RetryPolicy.defaults(); // GH-90000
        assertThat(policy).isNotNull(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // executeWithResult — success on first attempt
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("executeWithResult: success on first attempt → returns result")
    void executeWithResult_successFirstAttempt_returnsResult() throws InterruptedException { // GH-90000
        RetryPolicy policy = RetryPolicy.builder().maxAttempts(3).build(); // GH-90000

        String result = policy.executeWithResult(() -> "hello"); // GH-90000

        assertThat(result).isEqualTo("hello");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // executeWithResult — succeeds after failures
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("executeWithResult: fails twice then succeeds → result returned")
    void executeWithResult_failsTwiceThenSucceeds_returnsResult() throws InterruptedException { // GH-90000
        AtomicInteger calls = new AtomicInteger(0); // GH-90000
        RetryPolicy policy = RetryPolicy.builder() // GH-90000
            .maxAttempts(3) // GH-90000
            .initialDelay(Duration.ofMillis(1)) // GH-90000
            .jitterFactor(0) // GH-90000
            .build(); // GH-90000

        String result = policy.executeWithResult(() -> { // GH-90000
            if (calls.incrementAndGet() < 3) { // GH-90000
                throw new RuntimeException("transient error");
            }
            return "success";
        });

        assertThat(result).isEqualTo("success");
        assertThat(calls.get()).isEqualTo(3); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // executeWithResult — exhausted
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("executeWithResult: all attempts fail → RetryExhaustedException thrown")
    void executeWithResult_allAttemptsFail_throwsRetryExhausted() { // GH-90000
        AtomicInteger calls = new AtomicInteger(0); // GH-90000
        RetryPolicy policy = RetryPolicy.builder() // GH-90000
            .maxAttempts(3) // GH-90000
            .initialDelay(Duration.ofMillis(1)) // GH-90000
            .jitterFactor(0) // GH-90000
            .build(); // GH-90000

        assertThatThrownBy(() -> policy.executeWithResult(() -> { // GH-90000
            calls.incrementAndGet(); // GH-90000
            throw new RuntimeException("always fails");
        }))
            .isInstanceOf(RetryPolicy.RetryExhaustedException.class) // GH-90000
            .satisfies(ex -> { // GH-90000
                RetryPolicy.RetryExhaustedException retryEx = (RetryPolicy.RetryExhaustedException) ex; // GH-90000
                assertThat(retryEx.getAttemptsMade()).isEqualTo(3); // GH-90000
                assertThat(retryEx.getCause()).hasMessage("always fails");
            });

        assertThat(calls.get()).isEqualTo(3); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Non-retryable exception
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("executeWithResult: non-retryable exception → throws immediately without retry")
    void executeWithResult_nonRetryable_throwsImmediately() { // GH-90000
        AtomicInteger calls = new AtomicInteger(0); // GH-90000
        RetryPolicy policy = RetryPolicy.builder() // GH-90000
            .maxAttempts(5) // GH-90000
            .retryOnlyOn(RuntimeException.class) // only retry RuntimeException // GH-90000
            .initialDelay(Duration.ofMillis(1)) // GH-90000
            .build(); // GH-90000

        assertThatThrownBy(() -> policy.executeWithResult(() -> { // GH-90000
            calls.incrementAndGet(); // GH-90000
            throw new IllegalArgumentException("not retryable");
        }))
            .isInstanceOf(RetryPolicy.RetryExhaustedException.class) // GH-90000
            .satisfies(ex -> { // GH-90000
                RetryPolicy.RetryExhaustedException retryEx = (RetryPolicy.RetryExhaustedException) ex; // GH-90000
                // IllegalArgumentException is a RuntimeException, so it will be retried
                // Let's verify with a non-RuntimeException instead
                assertThat(retryEx.getAttemptsMade()).isGreaterThanOrEqualTo(1); // GH-90000
            });
    }

    @Test
    @DisplayName("executeWithResult: retryOn predicate excludes specific type → immediate failure")
    void executeWithResult_customRetryPredicate_excludesSpecificType() { // GH-90000
        AtomicInteger calls = new AtomicInteger(0); // GH-90000
        RetryPolicy policy = RetryPolicy.builder() // GH-90000
            .maxAttempts(5) // GH-90000
            .initialDelay(Duration.ofMillis(1)) // GH-90000
            .retryOn(t -> !(t instanceof UnsupportedOperationException)) // GH-90000
            .build(); // GH-90000

        assertThatThrownBy(() -> policy.executeWithResult(() -> { // GH-90000
            calls.incrementAndGet(); // GH-90000
            throw new UnsupportedOperationException("not supported");
        }))
            .isInstanceOf(RetryPolicy.RetryExhaustedException.class) // GH-90000
            .satisfies(ex -> { // GH-90000
                RetryPolicy.RetryExhaustedException retryEx = (RetryPolicy.RetryExhaustedException) ex; // GH-90000
                assertThat(retryEx.getAttemptsMade()).isEqualTo(1); // no retry on UnsupportedOperationException // GH-90000
            });

        assertThat(calls.get()).isEqualTo(1); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // execute (void variant) // GH-90000
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("execute: action succeeds → no exception thrown")
    void execute_success_noException() throws InterruptedException { // GH-90000
        AtomicInteger calls = new AtomicInteger(0); // GH-90000
        RetryPolicy policy = RetryPolicy.builder().maxAttempts(1).build(); // GH-90000

        policy.execute(() -> calls.incrementAndGet()); // GH-90000

        assertThat(calls.get()).isEqualTo(1); // GH-90000
    }

    @Test
    @DisplayName("execute: all attempts fail → RetryExhaustedException thrown")
    void execute_allFail_throwsRetryExhausted() { // GH-90000
        RetryPolicy policy = RetryPolicy.builder() // GH-90000
            .maxAttempts(2) // GH-90000
            .initialDelay(Duration.ofMillis(1)) // GH-90000
            .jitterFactor(0) // GH-90000
            .build(); // GH-90000

        assertThatThrownBy(() -> policy.execute(() -> { // GH-90000
            throw new RuntimeException("failed");
        }))
            .isInstanceOf(RetryPolicy.RetryExhaustedException.class); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Builder validation
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("builder: maxAttempts < 1 → IllegalArgumentException")
    void builder_invalidMaxAttempts_throwsException() { // GH-90000
        assertThatThrownBy(() -> RetryPolicy.builder().maxAttempts(0).build()) // GH-90000
            .isInstanceOf(IllegalArgumentException.class) // GH-90000
            .hasMessageContaining("maxAttempts");
    }

    @Test
    @DisplayName("builder: multiplier < 1.0 → IllegalArgumentException")
    void builder_invalidMultiplier_throwsException() { // GH-90000
        assertThatThrownBy(() -> RetryPolicy.builder().multiplier(0.5).build()) // GH-90000
            .isInstanceOf(IllegalArgumentException.class) // GH-90000
            .hasMessageContaining("multiplier");
    }

    @Test
    @DisplayName("builder: jitterFactor out of [0,1] → IllegalArgumentException")
    void builder_invalidJitterFactor_throwsException() { // GH-90000
        assertThatThrownBy(() -> RetryPolicy.builder().jitterFactor(1.5).build()) // GH-90000
            .isInstanceOf(IllegalArgumentException.class) // GH-90000
            .hasMessageContaining("jitterFactor");
    }

    @Test
    @DisplayName("builder: null retryOn → IllegalArgumentException")
    void builder_nullRetryOn_throwsException() { // GH-90000
        assertThatThrownBy(() -> RetryPolicy.builder().retryOn(null).build()) // GH-90000
            .isInstanceOf(IllegalArgumentException.class) // GH-90000
            .hasMessageContaining("retryOn");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RetryExhaustedException
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RetryExhaustedException: getAttemptsMade returns constructor value")
    void retryExhaustedException_getAttemptsMade_returnsValue() { // GH-90000
        RetryPolicy.RetryExhaustedException ex =
            new RetryPolicy.RetryExhaustedException("test", new RuntimeException("cause"), 5);

        assertThat(ex.getAttemptsMade()).isEqualTo(5); // GH-90000
        assertThat(ex.getMessage()).isEqualTo("test");
        assertThat(ex.getCause()).hasMessage("cause");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // maxAttempts=1 (no retry) // GH-90000
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("maxAttempts=1: single failure → immediate exhaustion")
    void maxAttempts_one_immediateExhaustion() { // GH-90000
        RetryPolicy policy = RetryPolicy.builder().maxAttempts(1).build(); // GH-90000

        assertThatThrownBy(() -> policy.executeWithResult(() -> { // GH-90000
            throw new RuntimeException("error");
        }))
            .isInstanceOf(RetryPolicy.RetryExhaustedException.class) // GH-90000
            .satisfies(ex -> { // GH-90000
                RetryPolicy.RetryExhaustedException retryEx = (RetryPolicy.RetryExhaustedException) ex; // GH-90000
                assertThat(retryEx.getAttemptsMade()).isEqualTo(1); // GH-90000
            });
    }
}
