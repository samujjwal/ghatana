package com.ghatana.platform.database.idempotency;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @doc.type class
 * @doc.purpose Verifies Kernel in-memory idempotency replay and audit semantics
 * @doc.layer platform
 * @doc.pattern Test
 */
class InMemoryIdempotencyStoreTest {

    @Test
    void returnsReplayDecisionForMatchingCompletedMutation() {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-12T00:00:00Z"));
        InMemoryIdempotencyStore<String> store = new InMemoryIdempotencyStore<>(Duration.ofHours(24), clock);

        store.putIfAbsent("finance:transaction.process", "txn-1", "fp-1", "approved");
        IdempotencyReplayDecision<String> replay = store.findReplay(
            "finance:transaction.process",
            "txn-1",
            "fp-1"
        );

        assertThat(replay.shouldReplay()).isTrue();
        assertThat(replay.result()).contains("approved");
        assertThat(replay.auditEvent().decision()).isEqualTo(IdempotencyDecision.COMPLETED);
        assertThat(replay.auditEvent().replayed()).isTrue();
    }

    @Test
    void expiresCompletedMutationBeforeReplay() {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-12T00:00:00Z"));
        InMemoryIdempotencyStore<String> store = new InMemoryIdempotencyStore<>(Duration.ofMinutes(5), clock);

        store.putIfAbsent("phr:appointment.create", "appt-1", "fp-1", "created");
        clock.advance(Duration.ofMinutes(5));

        IdempotencyReplayDecision<String> replay = store.findReplay(
            "phr:appointment.create",
            "appt-1",
            "fp-1"
        );

        assertThat(replay.shouldReplay()).isFalse();
        assertThat(replay.auditEvent().decision()).isEqualTo(IdempotencyDecision.EXPIRED);
        assertThat(replay.auditEvent().expired()).isTrue();
        assertThat(store.size()).isZero();
    }

    @Test
    void rejectsConflictingFingerprintForSameOperationAndKey() {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-12T00:00:00Z"));
        InMemoryIdempotencyStore<String> store = new InMemoryIdempotencyStore<>(Duration.ofHours(24), clock);

        store.putIfAbsent("finance:transaction.process", "txn-1", "fp-1", "approved");

        assertThatThrownBy(() -> store.findReplay("finance:transaction.process", "txn-1", "fp-2"))
            .isInstanceOf(IdempotencyConflictException.class)
            .hasMessage("Idempotency key 'txn-1' for operation 'finance:transaction.process' "
                + "was already used with different content");
        assertThat(store.lastAuditEvent().decision()).isEqualTo(IdempotencyDecision.CONFLICT);
        assertThat(store.lastAuditEvent().conflict()).isTrue();
    }

    @Test
    void scopesKeysByOperation() {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-12T00:00:00Z"));
        InMemoryIdempotencyStore<String> store = new InMemoryIdempotencyStore<>(Duration.ofHours(24), clock);

        store.putIfAbsent("finance:transaction.process", "shared-key", "fp-1", "finance");
        store.putIfAbsent("phr:appointment.create", "shared-key", "fp-2", "phr");

        assertThat(store.findReplay("finance:transaction.process", "shared-key", "fp-1").result())
            .contains("finance");
        assertThat(store.findReplay("phr:appointment.create", "shared-key", "fp-2").result())
            .contains("phr");
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
