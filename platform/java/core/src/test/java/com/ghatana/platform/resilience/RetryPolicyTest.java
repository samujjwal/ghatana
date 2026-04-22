package com.ghatana.platform.resilience;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Unit tests for RetryPolicy retry-on-failure and backoff logic
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("RetryPolicy — retry-on-failure, backoff, predicate filtering [GH-90000]")
class RetryPolicyTest extends EventloopTestBase {

    @Test
    @DisplayName("executes operation and returns result on first success [GH-90000]")
    void successOnFirstAttempt() { // GH-90000
        RetryPolicy policy = RetryPolicy.builder().maxRetries(3).build(); // GH-90000

        String result = runPromise(() -> // GH-90000
                policy.execute(eventloop(), () -> Promise.of("hello [GH-90000]")));

        assertThat(result).isEqualTo("hello [GH-90000]");
    }

    @Test
    @DisplayName("retries when operation fails and eventually succeeds [GH-90000]")
    void retriesAndEventuallySucceeds() { // GH-90000
        AtomicInteger attempts = new AtomicInteger(0); // GH-90000
        RetryPolicy policy = RetryPolicy.builder() // GH-90000
                .maxRetries(3) // GH-90000
                .initialDelay(Duration.ofMillis(1)) // GH-90000
                .build(); // GH-90000

        String result = runPromise(() -> policy.execute(eventloop(), () -> { // GH-90000
            int attempt = attempts.incrementAndGet(); // GH-90000
            if (attempt < 3) { // GH-90000
                return Promise.ofException(new RuntimeException("fail [GH-90000]"));
            }
            return Promise.of("success [GH-90000]");
        }));

        assertThat(result).isEqualTo("success [GH-90000]");
        assertThat(attempts.get()).isEqualTo(3); // GH-90000
    }

    @Test
    @DisplayName("fails after exhausting max retries [GH-90000]")
    void failsAfterMaxRetries() { // GH-90000
        AtomicInteger attempts = new AtomicInteger(0); // GH-90000
        RetryPolicy policy = RetryPolicy.builder() // GH-90000
                .maxRetries(2) // GH-90000
                .initialDelay(Duration.ofMillis(1)) // GH-90000
                .build(); // GH-90000

        assertThatThrownBy(() -> runPromise(() -> policy.execute(eventloop(), () -> { // GH-90000
            attempts.incrementAndGet(); // GH-90000
            return Promise.ofException(new RuntimeException("always fails [GH-90000]"));
        }))).isInstanceOf(RuntimeException.class) // GH-90000
                .hasMessage("always fails [GH-90000]");

        // maxRetries = 2 means 1 initial attempt + 2 retries = 3 total
        assertThat(attempts.get()).isEqualTo(3); // GH-90000
    }

    @Test
    @DisplayName("does not retry when predicate returns false [GH-90000]")
    void doesNotRetryWhenPredicateFalse() { // GH-90000
        AtomicInteger attempts = new AtomicInteger(0); // GH-90000
        // Only retry on IllegalArgumentException, not RuntimeException
        RetryPolicy policy = RetryPolicy.builder() // GH-90000
                .maxRetries(3) // GH-90000
                .initialDelay(Duration.ofMillis(1)) // GH-90000
                .retryIf(e -> e instanceof IllegalArgumentException) // GH-90000
                .build(); // GH-90000

        assertThatThrownBy(() -> runPromise(() -> policy.execute(eventloop(), () -> { // GH-90000
            attempts.incrementAndGet(); // GH-90000
            return Promise.ofException(new RuntimeException("not retryable [GH-90000]"));
        }))).isInstanceOf(RuntimeException.class); // GH-90000

        assertThat(attempts.get()).isEqualTo(1); // No retries // GH-90000
    }

    @Test
    @DisplayName("defaultPolicy creates a usable policy [GH-90000]")
    void defaultPolicyIsUsable() { // GH-90000
        RetryPolicy policy = RetryPolicy.defaultPolicy(); // GH-90000
        String result = runPromise(() -> policy.execute(eventloop(), () -> Promise.of("ok [GH-90000]")));
        assertThat(result).isEqualTo("ok [GH-90000]");
    }

    @Test
    @DisplayName("executeWithContext provides RetryContext with correct attempt numbers [GH-90000]")
    void executeWithContextProvidesContext() { // GH-90000
        AtomicInteger attempts = new AtomicInteger(0); // GH-90000
        AtomicInteger lastAttemptNumber = new AtomicInteger(0); // GH-90000

        RetryPolicy policy = RetryPolicy.builder() // GH-90000
                .maxRetries(2) // GH-90000
                .initialDelay(Duration.ofMillis(1)) // GH-90000
                .build(); // GH-90000

        assertThatThrownBy(() -> runPromise(() -> // GH-90000
                policy.executeWithContext(eventloop(), ctx -> { // GH-90000
                    attempts.incrementAndGet(); // GH-90000
                    lastAttemptNumber.set(ctx.getAttemptNumber()); // GH-90000
                    return Promise.ofException(new RuntimeException("fail [GH-90000]"));
                }))).isInstanceOf(RuntimeException.class); // GH-90000

        assertThat(lastAttemptNumber.get()).isGreaterThan(1); // had retries // GH-90000
    }

    @Test
    @DisplayName("executeWithContext succeeds on first try [GH-90000]")
    void executeWithContextSucceedsOnFirstTry() { // GH-90000
        RetryPolicy policy = RetryPolicy.builder().maxRetries(3).build(); // GH-90000

        String result = runPromise(() -> // GH-90000
                policy.executeWithContext(eventloop(), ctx -> { // GH-90000
                    assertThat(ctx.isRetry()).isFalse(); // GH-90000
                    assertThat(ctx.getAttemptNumber()).isEqualTo(1); // GH-90000
                    return Promise.of("done [GH-90000]");
                }));

        assertThat(result).isEqualTo("done [GH-90000]");
    }

    @Test
    @DisplayName("zero retries means only one attempt [GH-90000]")
    void zeroRetriesMeansOneAttempt() { // GH-90000
        AtomicInteger attempts = new AtomicInteger(0); // GH-90000
        RetryPolicy policy = RetryPolicy.builder().maxRetries(0).build(); // GH-90000

        assertThatThrownBy(() -> runPromise(() -> policy.execute(eventloop(), () -> { // GH-90000
            attempts.incrementAndGet(); // GH-90000
            return Promise.ofException(new RuntimeException("fail [GH-90000]"));
        }))).isInstanceOf(RuntimeException.class); // GH-90000

        assertThat(attempts.get()).isEqualTo(1); // GH-90000
    }
}
