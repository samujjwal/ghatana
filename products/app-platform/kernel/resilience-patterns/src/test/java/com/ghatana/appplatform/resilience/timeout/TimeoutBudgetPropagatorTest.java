package com.ghatana.appplatform.resilience.timeout;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link TimeoutBudgetPropagator}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for cascading timeout budget propagation (K18-008)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("TimeoutBudgetPropagator — Unit Tests")
class TimeoutBudgetPropagatorTest {

    @Test
    @DisplayName("createDeadlineHeader() produces a future ISO-8601 UTC timestamp")
    void createDeadlineHeader_producesValidFutureTimestamp() {
        Instant before = Instant.now();
        String header = TimeoutBudgetPropagator.createDeadlineHeader(Duration.ofMillis(500));
        Instant after = Instant.now();

        Instant deadline = Instant.parse(header);
        assertThat(deadline).isAfter(before);
        // On a well-performing system the deadline is ≤ now + 500ms + a small buffer
        assertThat(deadline).isBefore(after.plusMillis(600));
    }

    @Test
    @DisplayName("remainingBudgetMillis() returns remaining time for a future deadline")
    void remainingBudgetMillis_returnsBudget_forFutureDeadline() {
        // Deadline 2 seconds from now
        String header = TimeoutBudgetPropagator.createDeadlineHeader(Duration.ofSeconds(2));

        OptionalLong remaining = TimeoutBudgetPropagator.remainingBudgetMillis(header);

        assertThat(remaining).isPresent();
        assertThat(remaining.getAsLong()).isGreaterThan(0L);
        assertThat(remaining.getAsLong()).isLessThanOrEqualTo(2000L);
    }

    @Test
    @DisplayName("remainingBudgetMillis() returns empty for absent header (noHeader_default)")
    void remainingBudgetMillis_returnsEmpty_whenHeaderAbsent() {
        assertThat(TimeoutBudgetPropagator.remainingBudgetMillis(null)).isEmpty();
        assertThat(TimeoutBudgetPropagator.remainingBudgetMillis("")).isEmpty();
        assertThat(TimeoutBudgetPropagator.remainingBudgetMillis("  ")).isEmpty();
    }

    @Test
    @DisplayName("remainingBudgetMillis() returns negative value for an expired deadline")
    void remainingBudgetMillis_returnsNegative_forExpiredDeadline() {
        // A deadline 1 second in the past
        String header = Instant.now().minusSeconds(1).toString();

        OptionalLong remaining = TimeoutBudgetPropagator.remainingBudgetMillis(header);

        assertThat(remaining).isPresent();
        assertThat(remaining.getAsLong()).isLessThan(0L);
    }

    @Test
    @DisplayName("isExpired() returns true for a past deadline")
    void isExpired_returnsTrue_forPastDeadline() {
        String expired = Instant.now().minusSeconds(5).toString();
        assertThat(TimeoutBudgetPropagator.isExpired(expired)).isTrue();
    }

    @Test
    @DisplayName("isExpired() returns false for a future deadline")
    void isExpired_returnsFalse_forFutureDeadline() {
        String future = Instant.now().plusSeconds(10).toString();
        assertThat(TimeoutBudgetPropagator.isExpired(future)).isFalse();
    }

    @Test
    @DisplayName("isExpired() returns false when header is absent")
    void isExpired_returnsFalse_whenHeaderAbsent() {
        assertThat(TimeoutBudgetPropagator.isExpired(null)).isFalse();
        assertThat(TimeoutBudgetPropagator.isExpired("")).isFalse();
    }

    @Test
    @DisplayName("propagateDeadline() reduces downstream deadline (timeout_cascading_budgetReduced)")
    void propagateDeadline_reducesDownstreamBudget() {
        // Upstream gave us a 1-second deadline
        String upstreamHeader = TimeoutBudgetPropagator.createDeadlineHeader(Duration.ofSeconds(1));

        // We want 5 seconds for ourselves — but must honour upstream's 1s limit
        String propagated = TimeoutBudgetPropagator.propagateDeadline(
            upstreamHeader, Duration.ofSeconds(5));

        Instant propagatedDeadline = Instant.parse(propagated);
        Instant upstreamDeadline   = Instant.parse(upstreamHeader);

        // Propagated deadline must be <= upstream deadline (budget reduced, not extended)
        assertThat(propagatedDeadline).isBeforeOrEqualTo(upstreamDeadline);
    }

    @Test
    @DisplayName("propagateDeadline() uses own timeout when no upstream header (headerPropagation)")
    void propagateDeadline_usesOwnTimeout_whenNoUpstreamHeader() {
        Instant before = Instant.now();
        String propagated = TimeoutBudgetPropagator.propagateDeadline(null, Duration.ofSeconds(3));
        Instant after = Instant.now();

        Instant deadline = Instant.parse(propagated);
        assertThat(deadline).isAfter(before);
        assertThat(deadline).isBefore(after.plusSeconds(4));
    }

    @Test
    @DisplayName("remainingBudget() returns fallback for absent header")
    void remainingBudget_returnsFallback_whenHeaderAbsent() {
        Duration fallback = Duration.ofSeconds(5);
        Duration result = TimeoutBudgetPropagator.remainingBudget(null, fallback);
        assertThat(result).isEqualTo(fallback);
    }

    @Test
    @DisplayName("createDeadlineHeader() throws for zero/negative timeout")
    void createDeadlineHeader_throwsForNonPositiveTimeout() {
        assertThatThrownBy(() -> TimeoutBudgetPropagator.createDeadlineHeader(Duration.ZERO))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TimeoutBudgetPropagator.createDeadlineHeader(Duration.ofMillis(-1)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
