package com.ghatana.appplatform.ledger.dtc;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.logging.Logger;

/**
 * Detects and resolves write conflicts caused by concurrent distributed writes.
 *
 * <p>A conflict occurs when two writes on the same aggregate produce
 * {@link VersionVector.Ordering#CONCURRENT} version vectors—neither write
 * causally precedes the other.
 *
 * <h2>Resolution strategies</h2>
 * <ul>
 *   <li><b>Last-writer-wins (LWW)</b> — the default. Whichever write has the
 *       later wall-clock timestamp wins. Ties are broken by lexicographic order
 *       of the write ID.</li>
 *   <li><b>Custom resolvers</b> — register per aggregate type via
 *       {@link #registerResolver(String, ConflictResolver)} to apply domain-specific
 *       merge or priority logic.</li>
 * </ul>
 *
 * <h2>Audit events</h2>
 * <p>When a conflict is detected, a {@link ConflictDetectedEvent} is emitted to
 * all registered {@link ConflictEventListener}s. Register listeners via
 * {@link #addListener(ConflictEventListener)}.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * ConflictDetector detector = new ConflictDetector();
 * // Optional: register a custom resolver
 * detector.registerResolver("LedgerJournal", (incoming, existing) ->
 *     existing.timestamp().isAfter(incoming.timestamp()) ? existing : incoming);
 * // Optional: register an audit listener
 * detector.addListener(event -> auditService.log(event));
 *
 * ConflictResolution resolution =
 *     detector.detect(incomingWrite, localWrite, "LedgerJournal");
 * if (resolution.conflictDetected()) {
 *     applyWrite(resolution.winner());
 * }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Conflict detection and resolution for concurrent distributed writes (K17-006)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class ConflictDetector {

    private static final Logger LOG = Logger.getLogger(ConflictDetector.class.getName());

    // ── Domain types ───────────────────────────────────────────────────────────

    /**
     * Represents a write operation being evaluated for conflict.
     *
     * @param writeId      unique identifier of this write (used for tie-breaking)
     * @param vector       version vector at the time of the write
     * @param timestamp    wall-clock instant of the write
     * @param aggregateId  identifier of the aggregate being written
     * @param payload      write payload (opaque to the detector)
     */
    public record Write(
        String writeId,
        VersionVector vector,
        Instant timestamp,
        String aggregateId,
        Object payload
    ) {
        public Write {
            Objects.requireNonNull(writeId, "writeId");
            Objects.requireNonNull(vector, "vector");
            Objects.requireNonNull(timestamp, "timestamp");
            Objects.requireNonNull(aggregateId, "aggregateId");
        }
    }

    /**
     * Outcome of a conflict detection call.
     *
     * @param conflictDetected {@code true} when the two writes were concurrent
     * @param winner           the write chosen by the resolver (always non-null)
     * @param loser            the write that was discarded (null when no conflict)
     */
    public record ConflictResolution(
        boolean conflictDetected,
        Write winner,
        Write loser
    ) {
        /** Factory: creates a no-conflict result (no winner/loser distinction). */
        static ConflictResolution noConflict(Write write) {
            return new ConflictResolution(false, write, null);
        }

        /** Factory: creates a conflict result with a chosen winner. */
        static ConflictResolution conflict(Write winner, Write loser) {
            return new ConflictResolution(true, winner, loser);
        }
    }

    /**
     * Event emitted whenever a conflict is detected—suitable for audit logs.
     *
     * @param aggregateId   the aggregate that experienced the conflict
     * @param aggregateType the domain type (same string used for resolver lookup)
     * @param incoming      the incoming (challenger) write
     * @param existing      the existing (local) write
     * @param winner        the write selected by the resolver
     * @param detectedAt    when the conflict was detected
     */
    public record ConflictDetectedEvent(
        String aggregateId,
        String aggregateType,
        Write incoming,
        Write existing,
        Write winner,
        Instant detectedAt
    ) {}

    /**
     * Pluggable conflict resolver: given (incoming, existing), returns the winner.
     */
    @FunctionalInterface
    public interface ConflictResolver {
        Write resolve(Write incoming, Write existing);
    }

    /**
     * Listener for {@link ConflictDetectedEvent}s; used for audit and metrics.
     */
    @FunctionalInterface
    public interface ConflictEventListener {
        void onConflict(ConflictDetectedEvent event);
    }

    // ── State ──────────────────────────────────────────────────────────────────

    private final Map<String, ConflictResolver> resolvers = new HashMap<>();
    private final List<ConflictEventListener> listeners = new CopyOnWriteArrayList<>();

    // ── Resolver registry ──────────────────────────────────────────────────────

    /**
     * Registers a custom conflict resolver for a specific aggregate type.
     *
     * <p>If no resolver is registered for an aggregate type, the default
     * last-writer-wins strategy applies.
     *
     * @param aggregateType the aggregate type string (e.g. "LedgerJournal", "TransactionOrder")
     * @param resolver      the resolver function
     */
    public void registerResolver(String aggregateType, ConflictResolver resolver) {
        Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(resolver, "resolver");
        resolvers.put(aggregateType, resolver);
    }

    /**
     * Adds a listener that receives {@link ConflictDetectedEvent} notifications.
     */
    public void addListener(ConflictEventListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    // ── Detection API ──────────────────────────────────────────────────────────

    /**
     * Detects whether {@code incoming} and {@code existing} are concurrent, and if
     * so resolves the conflict.
     *
     * <ul>
     *   <li>{@code incoming} BEFORE {@code existing}: no conflict, {@code existing} stands.</li>
     *   <li>{@code incoming} AFTER {@code existing}: no conflict, {@code incoming} wins normally.</li>
     *   <li>{@code CONCURRENT}: conflict detected, resolver is invoked.</li>
     * </ul>
     *
     * @param incoming      the newly received write
     * @param existing      the locally stored write
     * @param aggregateType aggregate type tag used to look up custom resolvers
     * @return the resolution decision (always non-null)
     */
    public ConflictResolution detect(Write incoming, Write existing, String aggregateType) {
        Objects.requireNonNull(incoming, "incoming");
        Objects.requireNonNull(existing, "existing");
        Objects.requireNonNull(aggregateType, "aggregateType");

        VersionVector.Ordering ordering = incoming.vector().compare(existing.vector());

        switch (ordering) {
            case BEFORE -> {
                // incoming is causally older than existing — no conflict, existing wins
                LOG.fine("[ConflictDetector] BEFORE: incoming predates existing, no conflict.");
                return ConflictResolution.noConflict(existing);
            }
            case AFTER -> {
                // incoming is causally newer — normal update, no conflict
                LOG.fine("[ConflictDetector] AFTER: incoming supersedes existing, no conflict.");
                return ConflictResolution.noConflict(incoming);
            }
            case CONCURRENT -> {
                // Genuine write conflict — invoke resolver
                LOG.warning("[ConflictDetector] CONCURRENT conflict detected for aggregate="
                    + incoming.aggregateId() + " type=" + aggregateType);

                ConflictResolver resolver = resolvers.getOrDefault(
                    aggregateType, ConflictDetector::lastWriterWins);
                Write winner = resolver.resolve(incoming, existing);
                Write loser  = (winner == incoming) ? existing : incoming;

                ConflictDetectedEvent event = new ConflictDetectedEvent(
                    incoming.aggregateId(), aggregateType, incoming, existing,
                    winner, Instant.now());

                emit(event);
                return ConflictResolution.conflict(winner, loser);
            }
            default -> throw new IllegalStateException("Unknown ordering: " + ordering);
        }
    }

    // ── Default resolver ───────────────────────────────────────────────────────

    /**
     * Last-writer-wins resolver: the write with the later wall-clock timestamp wins.
     * If timestamps are equal, the write with the lexicographically larger writeId wins.
     */
    public static Write lastWriterWins(Write incoming, Write existing) {
        int cmp = incoming.timestamp().compareTo(existing.timestamp());
        if (cmp > 0) return incoming;
        if (cmp < 0) return existing;
        // Tie-break by writeId string ordering (deterministic)
        return incoming.writeId().compareTo(existing.writeId()) >= 0 ? incoming : existing;
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private void emit(ConflictDetectedEvent event) {
        for (ConflictEventListener listener : listeners) {
            try {
                listener.onConflict(event);
            } catch (Exception e) {
                LOG.warning("[ConflictDetector] Listener threw: " + e.getMessage());
            }
        }
    }
}
