/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.agent.audit;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for WP5: Evidence plane — HashChainedTraceAppender, TraceEvent,
 * TraceEventBuilder, hash chain integrity and tamper detection.
 */
@DisplayName("Evidence Plane (WP5) [GH-90000]")
class HashChainedTraceAppenderTest extends EventloopTestBase {

    private HashChainedTraceAppender appender;

    @Override
    protected Duration eventloopTimeout() { // GH-90000
        return Duration.ofSeconds(15); // GH-90000
    }

    @BeforeEach
    void setUp() { // GH-90000
        appender = new HashChainedTraceAppender(); // GH-90000
    }

    // =========================================================================
    // Basic append and retrieval
    // =========================================================================

    @Nested
    @DisplayName("append and retrieval [GH-90000]")
    class AppendAndRetrieval {

        @Test
        @DisplayName("should append and retrieve events by trace [GH-90000]")
        void shouldAppendAndRetrieveByTrace() { // GH-90000
            TraceEventBuilder builder = new TraceEventBuilder( // GH-90000
                    "trace-1", "agent-1", "tenant-1", "");

            TraceEvent event = builder.build( // GH-90000
                    TraceEventType.ACTION_EXECUTED, "Called API",
                    Map.of("toolId", "weather-lookup")); // GH-90000

            runPromise(() -> appender.append(event)); // GH-90000

            List<TraceEvent> retrieved = runPromise( // GH-90000
                    () -> appender.getByTrace("trace-1", "tenant-1")); // GH-90000

            assertThat(retrieved).hasSize(1); // GH-90000
            assertThat(retrieved.getFirst().summary()).isEqualTo("Called API [GH-90000]");
            assertThat(retrieved.getFirst().eventType()).isEqualTo(TraceEventType.ACTION_EXECUTED); // GH-90000
            assertThat(retrieved.getFirst().payload()).containsEntry("toolId", "weather-lookup"); // GH-90000
        }

        @Test
        @DisplayName("should retrieve events by agent [GH-90000]")
        void shouldRetrieveByAgent() { // GH-90000
            TraceEventBuilder builder = new TraceEventBuilder( // GH-90000
                    "trace-1", "agent-1", "tenant-1", "");

            runPromise(() -> appender.append( // GH-90000
                    builder.build(TraceEventType.ACTION_EXECUTED, "Action 1"))); // GH-90000
            runPromise(() -> appender.append( // GH-90000
                    builder.build(TraceEventType.TURN_COMPLETED, "Done"))); // GH-90000

            List<TraceEvent> byAgent = runPromise( // GH-90000
                    () -> appender.getByAgent("agent-1", "tenant-1", null, null, 10)); // GH-90000

            assertThat(byAgent).hasSize(2); // GH-90000
        }

        @Test
        @DisplayName("should retrieve events by type [GH-90000]")
        void shouldRetrieveByType() { // GH-90000
            TraceEventBuilder builder = new TraceEventBuilder( // GH-90000
                    "trace-1", "agent-1", "tenant-1", "");

            runPromise(() -> appender.append( // GH-90000
                    builder.build(TraceEventType.ACTION_EXECUTED, "Action"))); // GH-90000
            runPromise(() -> appender.append( // GH-90000
                    builder.build(TraceEventType.ACTION_DENIED, "Denied"))); // GH-90000
            runPromise(() -> appender.append( // GH-90000
                    builder.build(TraceEventType.ACTION_EXECUTED, "Action 2"))); // GH-90000

            List<TraceEvent> executed = runPromise( // GH-90000
                    () -> appender.getByType(TraceEventType.ACTION_EXECUTED, "tenant-1", // GH-90000
                            null, null, 10));

            assertThat(executed).hasSize(2); // GH-90000
            assertThat(executed).allMatch(e -> e.eventType() == TraceEventType.ACTION_EXECUTED); // GH-90000
        }

        @Test
        @DisplayName("should enforce time range filters on getByAgent [GH-90000]")
        void shouldFilterByTimeRange() { // GH-90000
            Instant now = Instant.now(); // GH-90000
            TraceEventBuilder builder = new TraceEventBuilder( // GH-90000
                    "trace-1", "agent-1", "tenant-1", "");

            TraceEvent event = builder.build(TraceEventType.ACTION_EXECUTED, "Action"); // GH-90000
            runPromise(() -> appender.append(event)); // GH-90000

            // Query with future start should return nothing
            List<TraceEvent> futureOnly = runPromise( // GH-90000
                    () -> appender.getByAgent("agent-1", "tenant-1", // GH-90000
                            now.plusSeconds(3600), null, 10)); // GH-90000

            assertThat(futureOnly).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should respect limit parameter [GH-90000]")
        void shouldRespectLimit() { // GH-90000
            TraceEventBuilder builder = new TraceEventBuilder( // GH-90000
                    "trace-1", "agent-1", "tenant-1", "");

            for (int i = 0; i < 5; i++) { // GH-90000
                runPromise(() -> appender.append( // GH-90000
                        builder.build(TraceEventType.ACTION_EXECUTED, "Action"))); // GH-90000
            }

            List<TraceEvent> limited = runPromise( // GH-90000
                    () -> appender.getByAgent("agent-1", "tenant-1", null, null, 3)); // GH-90000

            assertThat(limited).hasSize(3); // GH-90000
        }
    }

    // =========================================================================
    // Hash chain integrity
    // =========================================================================

    @Nested
    @DisplayName("hash chain integrity [GH-90000]")
    class HashChainIntegrity {

        @Test
        @DisplayName("events should be hash-chained (previousHash links) [GH-90000]")
        void eventsShouldBeHashChained() { // GH-90000
            TraceEventBuilder builder = new TraceEventBuilder( // GH-90000
                    "trace-1", "agent-1", "tenant-1", "");

            TraceEvent event1 = builder.build(TraceEventType.TURN_STARTED, "Start"); // GH-90000
            TraceEvent event2 = builder.build(TraceEventType.ACTION_EXECUTED, "Action"); // GH-90000
            TraceEvent event3 = builder.build(TraceEventType.TURN_COMPLETED, "Done"); // GH-90000

            runPromise(() -> appender.append(event1)); // GH-90000
            runPromise(() -> appender.append(event2)); // GH-90000
            runPromise(() -> appender.append(event3)); // GH-90000

            // event2's previousHash should be event1's eventHash
            assertThat(event2.previousHash()).isEqualTo(event1.eventHash()); // GH-90000
            // event3's previousHash should be event2's eventHash
            assertThat(event3.previousHash()).isEqualTo(event2.eventHash()); // GH-90000
        }

        @Test
        @DisplayName("first event should have empty previousHash (genesis) [GH-90000]")
        void firstEventShouldHaveEmptyPreviousHash() { // GH-90000
            TraceEventBuilder builder = new TraceEventBuilder( // GH-90000
                    "trace-1", "agent-1", "tenant-1", "");

            TraceEvent event = builder.build(TraceEventType.TURN_STARTED, "Start"); // GH-90000

            assertThat(event.previousHash()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("verifyChain should return true for valid chain [GH-90000]")
        void verifyChainShouldReturnTrueForValid() { // GH-90000
            TraceEventBuilder builder = new TraceEventBuilder( // GH-90000
                    "trace-1", "agent-1", "tenant-1", "");

            TraceEvent e1 = builder.build(TraceEventType.TURN_STARTED, "Start"); // GH-90000
            TraceEvent e2 = builder.build(TraceEventType.ACTION_EXECUTED, "Action"); // GH-90000
            TraceEvent e3 = builder.build(TraceEventType.TURN_COMPLETED, "Done"); // GH-90000

            runPromise(() -> appender.append(e1)); // GH-90000
            runPromise(() -> appender.append(e2)); // GH-90000
            runPromise(() -> appender.append(e3)); // GH-90000

            assertThat(appender.verifyChain(List.of(e1, e2, e3))).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("verifyChain should return true for empty list [GH-90000]")
        void verifyChainShouldReturnTrueForEmpty() { // GH-90000
            assertThat(appender.verifyChain(List.of())).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("verifyChain should detect tampered event hash [GH-90000]")
        void verifyChainShouldDetectTamperedHash() { // GH-90000
            TraceEventBuilder builder = new TraceEventBuilder( // GH-90000
                    "trace-1", "agent-1", "tenant-1", "");

            TraceEvent e1 = builder.build(TraceEventType.TURN_STARTED, "Start"); // GH-90000
            TraceEvent e2 = builder.build(TraceEventType.ACTION_EXECUTED, "Action"); // GH-90000

            // Create tampered version of e2 with wrong eventHash
            TraceEvent tampered = new TraceEvent( // GH-90000
                    e2.eventId(), e2.traceId(), e2.sequenceNumber(), // GH-90000
                    e2.eventType(), e2.agentId(), e2.tenantId(), // GH-90000
                    e2.previousHash(), "sha256:tampered", // GH-90000
                    e2.summary(), e2.payload(), e2.timestamp()); // GH-90000

            assertThat(appender.verifyChain(List.of(e1, tampered))).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("verifyChain should detect broken previousHash link [GH-90000]")
        void verifyChainShouldDetectBrokenLink() { // GH-90000
            TraceEventBuilder builder = new TraceEventBuilder( // GH-90000
                    "trace-1", "agent-1", "tenant-1", "");

            TraceEvent e1 = builder.build(TraceEventType.TURN_STARTED, "Start"); // GH-90000
            TraceEvent e2 = builder.build(TraceEventType.ACTION_EXECUTED, "Action"); // GH-90000

            // Create e2 variant with wrong previousHash
            TraceEvent broken = new TraceEvent( // GH-90000
                    e2.eventId(), e2.traceId(), e2.sequenceNumber(), // GH-90000
                    e2.eventType(), e2.agentId(), e2.tenantId(), // GH-90000
                    "sha256:wrong-previous", e2.eventHash(), // GH-90000
                    e2.summary(), e2.payload(), e2.timestamp()); // GH-90000

            assertThat(appender.verifyChain(List.of(e1, broken))).isFalse(); // GH-90000
        }
    }

    // =========================================================================
    // Rejection of invalid events
    // =========================================================================

    @Nested
    @DisplayName("rejection [GH-90000]")
    class Rejection {

        @Test
        @DisplayName("should reject event with wrong previousHash [GH-90000]")
        void shouldRejectWrongPreviousHash() { // GH-90000
            TraceEventBuilder builder = new TraceEventBuilder( // GH-90000
                    "trace-1", "agent-1", "tenant-1", "");

            TraceEvent valid = builder.build(TraceEventType.TURN_STARTED, "Start"); // GH-90000
            runPromise(() -> appender.append(valid)); // GH-90000

            // Create an event with wrong previousHash
            TraceEventBuilder rogue = new TraceEventBuilder( // GH-90000
                    "trace-2", "agent-1", "tenant-1", "sha256:not-the-real-previous");
            TraceEvent rogue1 = rogue.build(TraceEventType.ACTION_EXECUTED, "Rogue"); // GH-90000

            try {
                runPromise(() -> appender.append(rogue1)); // GH-90000
                fail("Should have thrown due to broken hash chain [GH-90000]");
            } catch (Exception e) { // GH-90000
                assertThat(e).hasMessageContaining("Hash chain broken [GH-90000]");
            }
            clearFatalError(); // GH-90000
        }

        @Test
        @DisplayName("should reject event with tampered hash [GH-90000]")
        void shouldRejectTamperedHash() { // GH-90000
            TraceEvent tampered = new TraceEvent( // GH-90000
                    "evt-1", "trace-1", 0,
                    TraceEventType.TURN_STARTED, "agent-1", "tenant-1",
                    "", "sha256:fake-hash", "Test",
                    Map.of(), Instant.now()); // GH-90000

            try {
                runPromise(() -> appender.append(tampered)); // GH-90000
                fail("Should have thrown due to hash mismatch [GH-90000]");
            } catch (Exception e) { // GH-90000
                assertThat(e).hasMessageContaining("hash mismatch [GH-90000]");
            }
            clearFatalError(); // GH-90000
        }
    }

    // =========================================================================
    // Tenant isolation
    // =========================================================================

    @Nested
    @DisplayName("tenant isolation [GH-90000]")
    class TenantIsolation {

        @Test
        @DisplayName("different tenants should have separate ledger partitions [GH-90000]")
        void differentTenantsShouldBeIsolated() { // GH-90000
            TraceEventBuilder builder1 = new TraceEventBuilder( // GH-90000
                    "trace-1", "agent-1", "tenant-A", "");
            TraceEventBuilder builder2 = new TraceEventBuilder( // GH-90000
                    "trace-2", "agent-2", "tenant-B", "");

            runPromise(() -> appender.append( // GH-90000
                    builder1.build(TraceEventType.ACTION_EXECUTED, "A action"))); // GH-90000
            runPromise(() -> appender.append( // GH-90000
                    builder2.build(TraceEventType.ACTION_EXECUTED, "B action"))); // GH-90000

            List<TraceEvent> tenantA = runPromise( // GH-90000
                    () -> appender.getByTrace("trace-1", "tenant-A")); // GH-90000
            List<TraceEvent> tenantB = runPromise( // GH-90000
                    () -> appender.getByTrace("trace-2", "tenant-B")); // GH-90000
            List<TraceEvent> crossTenant = runPromise( // GH-90000
                    () -> appender.getByTrace("trace-1", "tenant-B")); // GH-90000

            assertThat(tenantA).hasSize(1); // GH-90000
            assertThat(tenantB).hasSize(1); // GH-90000
            assertThat(crossTenant).isEmpty(); // GH-90000
        }
    }

    // =========================================================================
    // computeHash determinism
    // =========================================================================

    @Nested
    @DisplayName("hash computation [GH-90000]")
    class HashComputation {

        @Test
        @DisplayName("computeHash should be deterministic [GH-90000]")
        void computeHashShouldBeDeterministic() { // GH-90000
            Instant now = Instant.parse("2026-03-22T12:00:00Z [GH-90000]");
            TraceEvent event = new TraceEvent( // GH-90000
                    "evt-1", "trace-1", 0,
                    TraceEventType.ACTION_EXECUTED, "agent-1", "tenant-1",
                    "", "", "summary", Map.of(), now); // GH-90000

            String hash1 = HashChainedTraceAppender.computeHash(event); // GH-90000
            String hash2 = HashChainedTraceAppender.computeHash(event); // GH-90000

            assertThat(hash1).isEqualTo(hash2); // GH-90000
            assertThat(hash1).isNotBlank(); // GH-90000
            assertThat(hash1).hasSize(64); // SHA-256 hex = 64 chars // GH-90000
        }

        @Test
        @DisplayName("different events should produce different hashes [GH-90000]")
        void differentEventsShouldProduceDifferentHashes() { // GH-90000
            Instant now = Instant.now(); // GH-90000
            TraceEvent e1 = new TraceEvent( // GH-90000
                    "evt-1", "trace-1", 0,
                    TraceEventType.ACTION_EXECUTED, "agent-1", "tenant-1",
                    "", "", "Action 1", Map.of(), now); // GH-90000
            TraceEvent e2 = new TraceEvent( // GH-90000
                    "evt-2", "trace-1", 1,
                    TraceEventType.ACTION_DENIED, "agent-1", "tenant-1",
                    "", "", "Action 2", Map.of(), now); // GH-90000

            assertThat(HashChainedTraceAppender.computeHash(e1)) // GH-90000
                    .isNotEqualTo(HashChainedTraceAppender.computeHash(e2)); // GH-90000
        }
    }
}
