package com.ghatana.appplatform.refdata.service;

import com.ghatana.appplatform.refdata.domain.Instrument;
import com.ghatana.appplatform.refdata.domain.InstrumentStatus;
import com.ghatana.appplatform.refdata.port.InstrumentStore;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * @doc.type       Application Service
 * @doc.purpose    Instrument status lifecycle management (D11-003).
 *                 Enforces the allowed transition graph:
 *                   PENDING_APPROVAL → ACTIVE
 *                   ACTIVE           → SUSPENDED
 *                   SUSPENDED        → ACTIVE
 *                   ACTIVE           → DELISTED
 *                 Each transition creates a new SCD Type-2 version (closes current
 *                 row, inserts new) and publishes a domain event via the supplied
 *                 event publisher callback.
 * @doc.layer      Application Service
 * @doc.pattern    State Machine + SCD Type-2 versioning
 */
public class InstrumentLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(InstrumentLifecycleService.class);

    private final InstrumentStore store;
    private final Executor executor;
    /** Publisher callback; in production wired to KafkaEventPublisher. */
    private final Consumer<InstrumentStatusChangedEvent> eventPublisher;

    public InstrumentLifecycleService(InstrumentStore store, Executor executor,
                                      Consumer<InstrumentStatusChangedEvent> eventPublisher) {
        this.store = store;
        this.executor = executor;
        this.eventPublisher = eventPublisher;
    }

    /** Approve: PENDING_APPROVAL → ACTIVE */
    public Promise<Void> approve(UUID instrumentId, String approverActorId) {
        return transition(instrumentId, InstrumentStatus.PENDING_APPROVAL,
                InstrumentStatus.ACTIVE, approverActorId, "INSTRUMENT_APPROVED");
    }

    /** Suspend: ACTIVE → SUSPENDED */
    public Promise<Void> suspend(UUID instrumentId, String reason, String actorId) {
        return transition(instrumentId, InstrumentStatus.ACTIVE,
                InstrumentStatus.SUSPENDED, actorId, "INSTRUMENT_SUSPENDED");
    }

    /** Reactivate: SUSPENDED → ACTIVE */
    public Promise<Void> reactivate(UUID instrumentId, String actorId) {
        return transition(instrumentId, InstrumentStatus.SUSPENDED,
                InstrumentStatus.ACTIVE, actorId, "INSTRUMENT_REACTIVATED");
    }

    /** Delist: ACTIVE → DELISTED (terminal state, no further transitions allowed). */
    public Promise<Void> delist(UUID instrumentId, String actorId) {
        return transition(instrumentId, InstrumentStatus.ACTIVE,
                InstrumentStatus.DELISTED, actorId, "INSTRUMENT_DELISTED");
    }

    // ── Private transition implementation ────────────────────────────────────

    private Promise<Void> transition(UUID instrumentId,
                                     InstrumentStatus expectedCurrent,
                                     InstrumentStatus newStatus,
                                     String actorId,
                                     String eventType) {
        return Promise.ofBlocking(executor, () -> {
            Instrument current = store.findCurrentById(instrumentId).get()
                    .orElseThrow(() -> new InstrumentNotFoundException(instrumentId));

            if (current.status() != expectedCurrent) {
                throw new InvalidStatusTransitionException(
                        instrumentId, current.status(), newStatus);
            }

            LocalDate today = LocalDate.now();
            // Close the current version (set effectiveTo = today)
            Instrument closed = new Instrument(
                    current.id(), current.symbol(), current.exchange(), current.isin(),
                    current.name(), current.type(), current.status(),
                    current.sector(), current.lotSize(), current.tickSize(), current.currency(),
                    current.effectiveFrom(), today,
                    current.createdAtUtc(), current.createdAtBs(), current.metadata());

            // Open a new version with the new status
            Instrument next = new Instrument(
                    current.id(), current.symbol(), current.exchange(), current.isin(),
                    current.name(), current.type(), newStatus,
                    current.sector(), current.lotSize(), current.tickSize(), current.currency(),
                    today, null, Instant.now(), current.createdAtBs(), current.metadata());

            store.saveNewVersion(closed, next).get();

            log.info("instrument.status_changed id={} {}→{} actor={}",
                    instrumentId, expectedCurrent, newStatus, actorId);

            eventPublisher.accept(new InstrumentStatusChangedEvent(
                    instrumentId, expectedCurrent, newStatus, actorId, Instant.now(), eventType));
            return null;
        });
    }

    // ── Event record ─────────────────────────────────────────────────────────

    public record InstrumentStatusChangedEvent(
            UUID instrumentId,
            InstrumentStatus previousStatus,
            InstrumentStatus newStatus,
            String actorId,
            Instant occurredAt,
            String eventType
    ) {}

    // ── Exceptions ────────────────────────────────────────────────────────────

    public static final class InstrumentNotFoundException extends RuntimeException {
        public InstrumentNotFoundException(UUID id) {
            super("Instrument not found: " + id);
        }
    }

    public static final class InvalidStatusTransitionException extends RuntimeException {
        public InvalidStatusTransitionException(UUID id,
                                                InstrumentStatus from, InstrumentStatus to) {
            super("Invalid status transition for " + id + ": " + from + " → " + to);
        }
    }
}
