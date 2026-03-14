package com.ghatana.appplatform.ledger.dtc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link ConflictDetector}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for concurrent write conflict detection and resolution (K17-006)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("ConflictDetector — Unit Tests")
class ConflictDetectorTest {

    private ConflictDetector detector;

    @BeforeEach
    void setUp() {
        detector = new ConflictDetector();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private ConflictDetector.Write write(
            String id, VersionVector vector, Instant ts) {
        return new ConflictDetector.Write(id, vector, ts, "agg-001", "payload-" + id);
    }

    // ── Tests ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("no conflict when incoming is causally AFTER existing (conflict_noConflict_noEvent)")
    void noConflict_whenIncomingAfterExisting() {
        VersionVector v1 = VersionVector.empty().increment("node-A");
        VersionVector v2 = v1.increment("node-A");  // v2 > v1

        ConflictDetector.Write existing = write("w1", v1, Instant.now().minusSeconds(2));
        ConflictDetector.Write incoming = write("w2", v2, Instant.now());

        List<ConflictDetector.ConflictDetectedEvent> emitted = new ArrayList<>();
        detector.addListener(emitted::add);

        ConflictDetector.ConflictResolution result =
            detector.detect(incoming, existing, "LedgerJournal");

        assertThat(result.conflictDetected()).isFalse();
        assertThat(result.winner()).isSameAs(incoming);
        assertThat(emitted).isEmpty();  // no event when no conflict
    }

    @Test
    @DisplayName("no conflict when incoming is causally BEFORE existing")
    void noConflict_whenIncomingBeforeExisting() {
        VersionVector v1 = VersionVector.empty().increment("node-A");
        VersionVector v2 = v1.increment("node-A");  // v2 > v1

        ConflictDetector.Write existing = write("w2", v2, Instant.now());           // newer
        ConflictDetector.Write incoming = write("w1", v1, Instant.now().minusSeconds(2)); // older

        ConflictDetector.ConflictResolution result =
            detector.detect(incoming, existing, "LedgerJournal");

        assertThat(result.conflictDetected()).isFalse();
        assertThat(result.winner()).isSameAs(existing);  // existing stands
    }

    @Test
    @DisplayName("last-writer-wins when concurrent — newer timestamp wins (conflict_lastWriterWins)")
    void conflict_lastWriterWins_newerTimestampWins() {
        // Two writes on different nodes — concurrent (neither causally precedes the other)
        VersionVector v1 = VersionVector.empty().increment("node-A");
        VersionVector v2 = VersionVector.empty().increment("node-B");

        Instant earlier = Instant.parse("2026-01-01T10:00:00Z");
        Instant later   = Instant.parse("2026-01-01T10:00:01Z");

        ConflictDetector.Write existing = write("w-old", v1, earlier);
        ConflictDetector.Write incoming = write("w-new", v2, later);

        ConflictDetector.ConflictResolution result =
            detector.detect(incoming, existing, "LedgerJournal");

        assertThat(result.conflictDetected()).isTrue();
        assertThat(result.winner()).isSameAs(incoming);  // later timestamp wins
        assertThat(result.loser()).isSameAs(existing);
    }

    @Test
    @DisplayName("last-writer-wins breaks ties by writeId lexicographic order")
    void conflict_lastWriterWins_tieBreakByWriteId() {
        VersionVector v1 = VersionVector.empty().increment("node-A");
        VersionVector v2 = VersionVector.empty().increment("node-B");

        Instant sameTime = Instant.parse("2026-01-01T10:00:00Z");

        ConflictDetector.Write w1 = write("zzz-win", v1, sameTime);  // lexicographically larger
        ConflictDetector.Write w2 = write("aaa-lose", v2, sameTime);

        // detect(incoming=w2, existing=w1)
        ConflictDetector.ConflictResolution result =
            detector.detect(w2, w1, "LedgerJournal");

        // writeId "zzz-win" > "aaa-lose" → w1 wins
        assertThat(result.winner()).isSameAs(w1);
    }

    @Test
    @DisplayName("custom resolver is invoked and overrides LWW (conflict_customResolver)")
    void conflict_customResolverIsInvoked() {
        VersionVector v1 = VersionVector.empty().increment("node-A");
        VersionVector v2 = VersionVector.empty().increment("node-B");

        Instant earlier = Instant.now().minusSeconds(5);
        Instant later   = Instant.now();

        ConflictDetector.Write existing = write("w-old", v1, earlier);
        ConflictDetector.Write incoming = write("w-new", v2, later);

        // Custom resolver: always prefer the existing write (opposite of LWW)
        detector.registerResolver("AlwaysKeepExisting",
            (inc, ext) -> ext);

        ConflictDetector.ConflictResolution result =
            detector.detect(incoming, existing, "AlwaysKeepExisting");

        assertThat(result.conflictDetected()).isTrue();
        assertThat(result.winner()).isSameAs(existing);  // custom resolver chose older one
    }

    @Test
    @DisplayName("ConflictDetectedEvent is emitted with correct fields (conflict_event_emitted)")
    void conflict_emitsEventWithCorrectFields() {
        VersionVector v1 = VersionVector.empty().increment("node-A");
        VersionVector v2 = VersionVector.empty().increment("node-B");

        Instant t1 = Instant.parse("2026-01-01T10:00:00Z");
        Instant t2 = Instant.parse("2026-01-01T10:00:02Z");

        ConflictDetector.Write existing = write("w1", v1, t1);
        ConflictDetector.Write incoming = write("w2", v2, t2);

        List<ConflictDetector.ConflictDetectedEvent> events = new ArrayList<>();
        detector.addListener(events::add);

        ConflictDetector.ConflictResolution result =
            detector.detect(incoming, existing, "SomeAggregate");

        assertThat(result.conflictDetected()).isTrue();
        assertThat(events).hasSize(1);

        ConflictDetector.ConflictDetectedEvent event = events.get(0);
        assertThat(event.aggregateId()).isEqualTo("agg-001");
        assertThat(event.aggregateType()).isEqualTo("SomeAggregate");
        assertThat(event.incoming()).isSameAs(incoming);
        assertThat(event.existing()).isSameAs(existing);
        assertThat(event.winner()).isSameAs(incoming);  // LWW: incoming has later timestamp
        assertThat(event.detectedAt()).isNotNull();
    }

    @Test
    @DisplayName("multiple listeners all receive the conflict event")
    void conflict_multipleListenersAllReceiveEvent() {
        VersionVector v1 = VersionVector.empty().increment("node-A");
        VersionVector v2 = VersionVector.empty().increment("node-B");

        List<ConflictDetector.ConflictDetectedEvent> listener1Events = new ArrayList<>();
        List<ConflictDetector.ConflictDetectedEvent> listener2Events = new ArrayList<>();
        detector.addListener(listener1Events::add);
        detector.addListener(listener2Events::add);

        detector.detect(
            write("w1", v1, Instant.now()),
            write("w2", v2, Instant.now().minusSeconds(1)),
            "Something"
        );

        assertThat(listener1Events).hasSize(1);
        assertThat(listener2Events).hasSize(1);
    }

    @Test
    @DisplayName("custom resolver for one type does not affect another type (LWW fallback)")
    void customResolver_doesNotAffectOtherTypes() {
        detector.registerResolver("SpecialType", (inc, ext) -> ext);  // Always prefer existing

        VersionVector v1 = VersionVector.empty().increment("node-A");
        VersionVector v2 = VersionVector.empty().increment("node-B");

        ConflictDetector.Write existing = write("w-old", v1, Instant.now().minusSeconds(5));
        ConflictDetector.Write incoming = write("w-new", v2, Instant.now());

        // For a DIFFERENT type, LWW must apply (not the special-type resolver)
        ConflictDetector.ConflictResolution lwwResult =
            detector.detect(incoming, existing, "DefaultType");

        assertThat(lwwResult.winner()).isSameAs(incoming);  // LWW: incoming is newer
    }
}
