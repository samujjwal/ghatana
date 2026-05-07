/*
 * Copyright (c) 2026 Ghatana Inc. 
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
@DisplayName("Evidence Plane (WP5)")
class HashChainedTraceAppenderTest extends EventloopTestBase {

    private HashChainedTraceAppender appender;

    @Override
    protected Duration eventloopTimeout() { 
        return Duration.ofSeconds(15); 
    }

    @BeforeEach
    void setUp() { 
        appender = new HashChainedTraceAppender(); 
    }

    // =========================================================================
    // Basic append and retrieval
    // =========================================================================

    @Nested
    @DisplayName("append and retrieval")
    class AppendAndRetrieval {

        @Test
        @DisplayName("should append and retrieve events by trace")
        void shouldAppendAndRetrieveByTrace() { 
            TraceEventBuilder builder = new TraceEventBuilder( 
                    "trace-1", "agent-1", "tenant-1", "");

            TraceEvent event = builder.build( 
                    TraceEventType.ACTION_EXECUTED, "Called API",
                    Map.of("toolId", "weather-lookup")); 

            runPromise(() -> appender.append(event)); 

            List<TraceEvent> retrieved = runPromise( 
                    () -> appender.getByTrace("trace-1", "tenant-1")); 

            assertThat(retrieved).hasSize(1); 
            assertThat(retrieved.getFirst().summary()).isEqualTo("Called API");
            assertThat(retrieved.getFirst().eventType()).isEqualTo(TraceEventType.ACTION_EXECUTED); 
            assertThat(retrieved.getFirst().payload()).containsEntry("toolId", "weather-lookup"); 
        }

        @Test
        @DisplayName("should retrieve events by agent")
        void shouldRetrieveByAgent() { 
            TraceEventBuilder builder = new TraceEventBuilder( 
                    "trace-1", "agent-1", "tenant-1", "");

            runPromise(() -> appender.append( 
                    builder.build(TraceEventType.ACTION_EXECUTED, "Action 1"))); 
            runPromise(() -> appender.append( 
                    builder.build(TraceEventType.TURN_COMPLETED, "Done"))); 

            List<TraceEvent> byAgent = runPromise( 
                    () -> appender.getByAgent("agent-1", "tenant-1", null, null, 10)); 

            assertThat(byAgent).hasSize(2); 
        }

        @Test
        @DisplayName("should retrieve events by type")
        void shouldRetrieveByType() { 
            TraceEventBuilder builder = new TraceEventBuilder( 
                    "trace-1", "agent-1", "tenant-1", "");

            runPromise(() -> appender.append( 
                    builder.build(TraceEventType.ACTION_EXECUTED, "Action"))); 
            runPromise(() -> appender.append( 
                    builder.build(TraceEventType.ACTION_DENIED, "Denied"))); 
            runPromise(() -> appender.append( 
                    builder.build(TraceEventType.ACTION_EXECUTED, "Action 2"))); 

            List<TraceEvent> executed = runPromise( 
                    () -> appender.getByType(TraceEventType.ACTION_EXECUTED, "tenant-1", 
                            null, null, 10));

            assertThat(executed).hasSize(2); 
            assertThat(executed).allMatch(e -> e.eventType() == TraceEventType.ACTION_EXECUTED); 
        }

        @Test
        @DisplayName("should enforce time range filters on getByAgent")
        void shouldFilterByTimeRange() { 
            Instant now = Instant.now(); 
            TraceEventBuilder builder = new TraceEventBuilder( 
                    "trace-1", "agent-1", "tenant-1", "");

            TraceEvent event = builder.build(TraceEventType.ACTION_EXECUTED, "Action"); 
            runPromise(() -> appender.append(event)); 

            // Query with future start should return nothing
            List<TraceEvent> futureOnly = runPromise( 
                    () -> appender.getByAgent("agent-1", "tenant-1", 
                            now.plusSeconds(3600), null, 10)); 

            assertThat(futureOnly).isEmpty(); 
        }

        @Test
        @DisplayName("should respect limit parameter")
        void shouldRespectLimit() { 
            TraceEventBuilder builder = new TraceEventBuilder( 
                    "trace-1", "agent-1", "tenant-1", "");

            for (int i = 0; i < 5; i++) { 
                runPromise(() -> appender.append( 
                        builder.build(TraceEventType.ACTION_EXECUTED, "Action"))); 
            }

            List<TraceEvent> limited = runPromise( 
                    () -> appender.getByAgent("agent-1", "tenant-1", null, null, 3)); 

            assertThat(limited).hasSize(3); 
        }
    }

    // =========================================================================
    // Hash chain integrity
    // =========================================================================

    @Nested
    @DisplayName("hash chain integrity")
    class HashChainIntegrity {

        @Test
        @DisplayName("events should be hash-chained (previousHash links)")
        void eventsShouldBeHashChained() { 
            TraceEventBuilder builder = new TraceEventBuilder( 
                    "trace-1", "agent-1", "tenant-1", "");

            TraceEvent event1 = builder.build(TraceEventType.TURN_STARTED, "Start"); 
            TraceEvent event2 = builder.build(TraceEventType.ACTION_EXECUTED, "Action"); 
            TraceEvent event3 = builder.build(TraceEventType.TURN_COMPLETED, "Done"); 

            runPromise(() -> appender.append(event1)); 
            runPromise(() -> appender.append(event2)); 
            runPromise(() -> appender.append(event3)); 

            // event2's previousHash should be event1's eventHash
            assertThat(event2.previousHash()).isEqualTo(event1.eventHash()); 
            // event3's previousHash should be event2's eventHash
            assertThat(event3.previousHash()).isEqualTo(event2.eventHash()); 
        }

        @Test
        @DisplayName("first event should have empty previousHash (genesis)")
        void firstEventShouldHaveEmptyPreviousHash() { 
            TraceEventBuilder builder = new TraceEventBuilder( 
                    "trace-1", "agent-1", "tenant-1", "");

            TraceEvent event = builder.build(TraceEventType.TURN_STARTED, "Start"); 

            assertThat(event.previousHash()).isEmpty(); 
        }

        @Test
        @DisplayName("verifyChain should return true for valid chain")
        void verifyChainShouldReturnTrueForValid() { 
            TraceEventBuilder builder = new TraceEventBuilder( 
                    "trace-1", "agent-1", "tenant-1", "");

            TraceEvent e1 = builder.build(TraceEventType.TURN_STARTED, "Start"); 
            TraceEvent e2 = builder.build(TraceEventType.ACTION_EXECUTED, "Action"); 
            TraceEvent e3 = builder.build(TraceEventType.TURN_COMPLETED, "Done"); 

            runPromise(() -> appender.append(e1)); 
            runPromise(() -> appender.append(e2)); 
            runPromise(() -> appender.append(e3)); 

            assertThat(appender.verifyChain(List.of(e1, e2, e3))).isTrue(); 
        }

        @Test
        @DisplayName("verifyChain should return true for empty list")
        void verifyChainShouldReturnTrueForEmpty() { 
            assertThat(appender.verifyChain(List.of())).isTrue(); 
        }

        @Test
        @DisplayName("verifyChain should detect tampered event hash")
        void verifyChainShouldDetectTamperedHash() { 
            TraceEventBuilder builder = new TraceEventBuilder( 
                    "trace-1", "agent-1", "tenant-1", "");

            TraceEvent e1 = builder.build(TraceEventType.TURN_STARTED, "Start"); 
            TraceEvent e2 = builder.build(TraceEventType.ACTION_EXECUTED, "Action"); 

            // Create tampered version of e2 with wrong eventHash
            TraceEvent tampered = new TraceEvent( 
                    e2.eventId(), e2.traceId(), e2.sequenceNumber(), 
                    e2.eventType(), e2.agentId(), e2.tenantId(), 
                    e2.previousHash(), "sha256:tampered", 
                    e2.summary(), e2.payload(), e2.timestamp()); 

            assertThat(appender.verifyChain(List.of(e1, tampered))).isFalse(); 
        }

        @Test
        @DisplayName("verifyChain should detect broken previousHash link")
        void verifyChainShouldDetectBrokenLink() { 
            TraceEventBuilder builder = new TraceEventBuilder( 
                    "trace-1", "agent-1", "tenant-1", "");

            TraceEvent e1 = builder.build(TraceEventType.TURN_STARTED, "Start"); 
            TraceEvent e2 = builder.build(TraceEventType.ACTION_EXECUTED, "Action"); 

            // Create e2 variant with wrong previousHash
            TraceEvent broken = new TraceEvent( 
                    e2.eventId(), e2.traceId(), e2.sequenceNumber(), 
                    e2.eventType(), e2.agentId(), e2.tenantId(), 
                    "sha256:wrong-previous", e2.eventHash(), 
                    e2.summary(), e2.payload(), e2.timestamp()); 

            assertThat(appender.verifyChain(List.of(e1, broken))).isFalse(); 
        }
    }

    // =========================================================================
    // Rejection of invalid events
    // =========================================================================

    @Nested
    @DisplayName("rejection")
    class Rejection {

        @Test
        @DisplayName("should reject event with wrong previousHash")
        void shouldRejectWrongPreviousHash() { 
            TraceEventBuilder builder = new TraceEventBuilder( 
                    "trace-1", "agent-1", "tenant-1", "");

            TraceEvent valid = builder.build(TraceEventType.TURN_STARTED, "Start"); 
            runPromise(() -> appender.append(valid)); 

            // Create an event with wrong previousHash
            TraceEventBuilder rogue = new TraceEventBuilder( 
                    "trace-2", "agent-1", "tenant-1", "sha256:not-the-real-previous");
            TraceEvent rogue1 = rogue.build(TraceEventType.ACTION_EXECUTED, "Rogue"); 

            try {
                runPromise(() -> appender.append(rogue1)); 
                fail("Should have thrown due to broken hash chain");
            } catch (Exception e) { 
                assertThat(e).hasMessageContaining("Hash chain broken");
            }
            clearFatalError(); 
        }

        @Test
        @DisplayName("should reject event with tampered hash")
        void shouldRejectTamperedHash() { 
            TraceEvent tampered = new TraceEvent( 
                    "evt-1", "trace-1", 0,
                    TraceEventType.TURN_STARTED, "agent-1", "tenant-1",
                    "", "sha256:fake-hash", "Test",
                    Map.of(), Instant.now()); 

            try {
                runPromise(() -> appender.append(tampered)); 
                fail("Should have thrown due to hash mismatch");
            } catch (Exception e) { 
                assertThat(e).hasMessageContaining("hash mismatch");
            }
            clearFatalError(); 
        }
    }

    // =========================================================================
    // Tenant isolation
    // =========================================================================

    @Nested
    @DisplayName("tenant isolation")
    class TenantIsolation {

        @Test
        @DisplayName("different tenants should have separate ledger partitions")
        void differentTenantsShouldBeIsolated() { 
            TraceEventBuilder builder1 = new TraceEventBuilder( 
                    "trace-1", "agent-1", "tenant-A", "");
            TraceEventBuilder builder2 = new TraceEventBuilder( 
                    "trace-2", "agent-2", "tenant-B", "");

            runPromise(() -> appender.append( 
                    builder1.build(TraceEventType.ACTION_EXECUTED, "A action"))); 
            runPromise(() -> appender.append( 
                    builder2.build(TraceEventType.ACTION_EXECUTED, "B action"))); 

            List<TraceEvent> tenantA = runPromise( 
                    () -> appender.getByTrace("trace-1", "tenant-A")); 
            List<TraceEvent> tenantB = runPromise( 
                    () -> appender.getByTrace("trace-2", "tenant-B")); 
            List<TraceEvent> crossTenant = runPromise( 
                    () -> appender.getByTrace("trace-1", "tenant-B")); 

            assertThat(tenantA).hasSize(1); 
            assertThat(tenantB).hasSize(1); 
            assertThat(crossTenant).isEmpty(); 
        }
    }

    // =========================================================================
    // computeHash determinism
    // =========================================================================

    @Nested
    @DisplayName("hash computation")
    class HashComputation {

        @Test
        @DisplayName("computeHash should be deterministic")
        void computeHashShouldBeDeterministic() { 
            Instant now = Instant.parse("2026-03-22T12:00:00Z");
            TraceEvent event = new TraceEvent( 
                    "evt-1", "trace-1", 0,
                    TraceEventType.ACTION_EXECUTED, "agent-1", "tenant-1",
                    "", "", "summary", Map.of(), now); 

            String hash1 = HashChainedTraceAppender.computeHash(event); 
            String hash2 = HashChainedTraceAppender.computeHash(event); 

            assertThat(hash1).isEqualTo(hash2); 
            assertThat(hash1).isNotBlank(); 
            assertThat(hash1).hasSize(64); // SHA-256 hex = 64 chars 
        }

        @Test
        @DisplayName("different events should produce different hashes")
        void differentEventsShouldProduceDifferentHashes() { 
            Instant now = Instant.now(); 
            TraceEvent e1 = new TraceEvent( 
                    "evt-1", "trace-1", 0,
                    TraceEventType.ACTION_EXECUTED, "agent-1", "tenant-1",
                    "", "", "Action 1", Map.of(), now); 
            TraceEvent e2 = new TraceEvent( 
                    "evt-2", "trace-1", 1,
                    TraceEventType.ACTION_DENIED, "agent-1", "tenant-1",
                    "", "", "Action 2", Map.of(), now); 

            assertThat(HashChainedTraceAppender.computeHash(e1)) 
                    .isNotEqualTo(HashChainedTraceAppender.computeHash(e2)); 
        }
    }
}
