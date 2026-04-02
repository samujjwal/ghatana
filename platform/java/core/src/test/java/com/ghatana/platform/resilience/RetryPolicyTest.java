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
@DisplayName("RetryPolicy — retry-on-failure, backoff, predicate filtering")
class RetryPolicyTest extends EventloopTestBase {

    @Test
    @DisplayName("executes operation and returns result on first success")
    void successOnFirstAttempt() {
        RetryPolicy policy = RetryPolicy.builder().maxRetries(3).build();

        String result = runPromise(() ->
                policy.execute(eventloop(), () -> Promise.of("hello")));

        assertThat(result).isEqualTo("hello");
    }

    @Test
    @DisplayName("retries when operation fails and eventually succeeds")
    void retriesAndEventuallySucceeds() {
        AtomicInteger attempts = new AtomicInteger(0);
        RetryPolicy policy = RetryPolicy.builder()
                .maxRetries(3)
                .initialDelay(Duration.ofMillis(1))
                .build();

        String result = runPromise(() -> policy.execute(eventloop(), () -> {
            int attempt = attempts.incrementAndGet();
            if (attempt < 3) {
                return Promise.ofException(new RuntimeException("fail"));
            }
            return Promise.of("success");
        }));

        assertThat(result).isEqualTo("success");
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("fails after exhausting max retries")
    void failsAfterMaxRetries() {
        AtomicInteger attempts = new AtomicInteger(0);
        RetryPolicy policy = RetryPolicy.builder()
                .maxRetries(2)
                .initialDelay(Duration.ofMillis(1))
                .build();

        assertThatThrownBy(() -> runPromise(() -> policy.execute(eventloop(), () -> {
            attempts.incrementAndGet();
            return Promise.ofException(new RuntimeException("always fails"));
        }))).isInstanceOf(RuntimeException.class)
                .hasMessage("always fails");

        // maxRetries = 2 means 1 initial attempt + 2 retries = 3 total
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("does not retry when predicate returns false")
    void doesNotRetryWhenPredicateFalse() {
        AtomicInteger attempts = new AtomicInteger(0);
        // Only retry on IllegalArgumentException, not RuntimeException
        RetryPolicy policy = RetryPolicy.builder()
                .maxRetries(3)
                .initialDelay(Duration.ofMillis(1))
                .retryIf(e -> e instanceof IllegalArgumentException)
                .build();

        assertThatThrownBy(() -> runPromise(() -> policy.execute(eventloop(), () -> {
            attempts.incrementAndGet();
            return Promise.ofException(new RuntimeException("not retryable"));
        }))).isInstanceOf(RuntimeException.class);

        assertThat(attempts.get()).isEqualTo(1); // No retries
    }

    @Test
    @DisplayName("defaultPolicy creates a usable policy")
    void defaultPolicyIsUsable() {
        RetryPolicy policy = RetryPolicy.defaultPolicy();
        String result = runPromise(() -> policy.execute(eventloop(), () -> Promise.of("ok")));
        assertThat(result).isEqualTo("ok");
    }

    @Test
    @DisplayName("executeWithContext provides RetryContext with correct attempt numbers")
    void executeWithContextProvidesContext() {
        AtomicInteger attempts = new AtomicInteger(0);
        AtomicInteger lastAttemptNumber = new AtomicInteger(0);

        RetryPolicy policy = RetryPolicy.builder()
                .maxRetries(2)
                .initialDelay(Duration.ofMillis(1))
                .build();

        assertThatThrownBy(() -> runPromise(() ->
                policy.executeWithContext(eventloop(), ctx -> {
                    attempts.incrementAndGet();
                    lastAttemptNumber.set(ctx.getAttemptNumber());
                    return Promise.ofException(new RuntimeException("fail"));
                }))).isInstanceOf(RuntimeException.class);

        assertThat(lastAttemptNumber.get()).isGreaterThan(1); // had retries
    }

    @Test
    @DisplayName("executeWithContext succeeds on first try")
    void executeWithContextSucceedsOnFirstTry() {
        RetryPolicy policy = RetryPolicy.builder().maxRetries(3).build();

        String result = runPromise(() ->
                policy.executeWithContext(eventloop(), ctx -> {
                    assertThat(ctx.isRetry()).isFalse();
                    assertThat(ctx.getAttemptNumber()).isEqualTo(1);
                    return Promise.of("done");
                }));

        assertThat(result).isEqualTo("done");
    }

    @Test
    @DisplayName("zero retries means only one attempt")
    void zeroRetriesMeansOneAttempt() {
        AtomicInteger attempts = new AtomicInteger(0);
        RetryPolicy policy = RetryPolicy.builder().maxRetries(0).build();

        assertThatThrownBy(() -> runPromise(() -> policy.execute(eventloop(), () -> {
            attempts.incrementAndGet();
            return Promise.ofException(new RuntimeException("fail"));
        }))).isInstanceOf(RuntimeException.class);

        assertThat(attempts.get()).isEqualTo(1);
    }
}
