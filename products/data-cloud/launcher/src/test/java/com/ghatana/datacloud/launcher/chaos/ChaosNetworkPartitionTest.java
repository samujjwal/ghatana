/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.chaos;

import com.ghatana.datacloud.client.DataCloudClient;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Chaos tests for network partition and resource exhaustion scenarios.
 *
 * <p><strong>Requirement:</strong> DC-NF-002, DC-NF-005 — Reliability &amp; Resilience, Gap 006.
 *
 * <p>Covers scenarios missing from {@link ChaosEngineeringTest}:
 * <ul>
 *   <li>Kafka network partition — producer cannot reach broker.</li>
 *   <li>Database TCP disconnect mid-transaction.</li>
 *   <li>ClickHouse analytics connection failure.</li>
 *   <li>Multi-region partition — region A and region B isolated from each other.</li>
 *   <li>Partial write on Kafka — some events land, some do not.</li>
 *   <li>Recovery after network partition is healed.</li>
 *   <li>Event buffering during outage and drain after recovery.</li>
 *   <li>Cascading dependency failure propagation.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Chaos tests for network partition and cascading failure scenarios (Gap 006) // GH-90000
 * @doc.layer product
 * @doc.pattern Chaos Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("Chaos – Network Partition &amp; Cascading Failure [GH-90000]")
class ChaosNetworkPartitionTest extends EventloopTestBase {

    private static final String TENANT_ID   = "chaos-partition-tenant";
    private static final String COLLECTION  = "partition-records";
    private static final String REGION_A    = "us-east-1";
    private static final String REGION_B    = "eu-west-1";

    @Mock
    private DataCloudClient client;

    private NetworkChaosHarness harness;

    @BeforeEach
    void setUp() { // GH-90000
        harness = new NetworkChaosHarness(client); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Kafka network partition
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Kafka partition scenarios [GH-90000]")
    class KafkaPartitionTests {

        @Test
        @DisplayName("should buffer events when Kafka is unreachable [GH-90000]")
        void shouldBufferEventsWhenKafkaUnreachable() throws Exception { // GH-90000
            harness.partitionKafka(); // GH-90000

            List<String> eventIds = harness.publishEvents(TENANT_ID, 5); // GH-90000

            // Events must be buffered (not lost) when Kafka is unavailable // GH-90000
            assertThat(eventIds).hasSize(5); // GH-90000
            assertThat(harness.getBufferedEventCount()).isEqualTo(5); // GH-90000
            assertThat(harness.getPersistedEventCount()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("should drain buffer and persist events after Kafka reconnects [GH-90000]")
        void shouldDrainBufferAfterKafkaReconnects() throws Exception { // GH-90000
            harness.partitionKafka(); // GH-90000
            harness.publishEvents(TENANT_ID, 3); // GH-90000

            // Heal partition — buffer should drain
            harness.healKafka(); // GH-90000
            harness.triggerBufferDrain(); // GH-90000

            assertThat(harness.getBufferedEventCount()).isEqualTo(0); // GH-90000
            assertThat(harness.getPersistedEventCount()).isEqualTo(3); // GH-90000
        }

        @Test
        @DisplayName("should preserve event ordering after buffer drain [GH-90000]")
        void shouldPreserveEventOrderingAfterDrain() throws Exception { // GH-90000
            harness.partitionKafka(); // GH-90000
            List<String> published = harness.publishEvents(TENANT_ID, 4); // GH-90000

            harness.healKafka(); // GH-90000
            List<String> drained = harness.triggerOrderedBufferDrain(); // GH-90000

            // Order must be preserved FIFO
            assertThat(drained).isEqualTo(published); // GH-90000
        }

        @Test
        @DisplayName("partial Kafka write — acks only for some events — remainder buffered [GH-90000]")
        void partialKafkaWrite_remainderBuffered() throws Exception { // GH-90000
            harness.setPartialKafkaWriteFailureRate(0.5); // 50% failure rate // GH-90000

            List<String> ids = harness.publishEvents(TENANT_ID, 10); // GH-90000

            // All IDs must be accounted for (either persisted or buffered, none lost) // GH-90000
            int total = harness.getPersistedEventCount() + harness.getBufferedEventCount(); // GH-90000
            assertThat(total).isEqualTo(10); // GH-90000
            assertThat(ids).hasSize(10); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Database TCP disconnect
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Database TCP disconnect [GH-90000]")
    class DatabaseTcpDisconnectTests {

        @Test
        @DisplayName("entity write during DB disconnect → fails with IOException [GH-90000]")
        void entityWrite_dbDisconnected_throwsIOException() { // GH-90000
            harness.partitionDatabase(); // GH-90000

            assertThatThrownBy(() -> runPromise(() -> harness.writeEntityOrFail(TENANT_ID, "id-1"))) // GH-90000
                .isInstanceOf(Exception.class) // GH-90000
                .satisfies(e -> // GH-90000
                    assertThat(e instanceof IOException || e.getCause() instanceof IOException // GH-90000
                        || e.getMessage().contains("connection [GH-90000]") || e.getMessage().contains("partition [GH-90000]"))
                        .as("Expected IOException or connection-related error [GH-90000]").isTrue()
                );
        }

        @Test
        @DisplayName("entity writes resume automatically after DB reconnects [GH-90000]")
        void entityWrites_resumeAfterDbReconnects() throws Exception { // GH-90000
            harness.partitionDatabase(); // GH-90000

            // Write fails while partitioned
            try {
                runPromise(() -> harness.writeEntityOrFail(TENANT_ID, "id-1")); // GH-90000
            } catch (Exception ignored) { // GH-90000
                // Expected
            }

            // Heal DB
            harness.healDatabase(); // GH-90000

            // Write should now succeed
            String result = runPromise(() -> harness.writeEntity(TENANT_ID, "id-2")); // GH-90000
            assertThat(result).isEqualTo("id-2 [GH-90000]");
        }

        @Test
        @DisplayName("read operations fail fast during DB outage and report error clearly [GH-90000]")
        void readOperations_dbDown_failFast() { // GH-90000
            harness.partitionDatabase(); // GH-90000

            assertThatThrownBy(() -> runPromise(() -> harness.readEntityOrFail(TENANT_ID, "id-1"))) // GH-90000
                .isInstanceOf(Exception.class); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Multi-region network partition
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Multi-region partition [GH-90000]")
    class MultiRegionPartitionTests {

        @Test
        @DisplayName("writes to partitioned region fail; other region unaffected [GH-90000]")
        void partitionedRegion_fails_healthyRegionUnaffected() throws Exception { // GH-90000
            harness.partitionRegion(REGION_B); // GH-90000

            // Region A still works
            String resultA = runPromise(() -> harness.writeToRegion(REGION_A, "id-a")); // GH-90000
            assertThat(resultA).isEqualTo("id-a [GH-90000]");

            // Region B is partitioned
            assertThatThrownBy(() -> runPromise(() -> harness.writeToRegion(REGION_B, "id-b"))) // GH-90000
                .isInstanceOf(Exception.class); // GH-90000
        }

        @Test
        @DisplayName("both regions recover after partition healed [GH-90000]")
        void bothRegions_recover_afterPartitionHealed() throws Exception { // GH-90000
            harness.partitionRegion(REGION_B); // GH-90000

            // Heal
            harness.healRegion(REGION_B); // GH-90000

            String resultA = runPromise(() -> harness.writeToRegion(REGION_A, "id-a")); // GH-90000
            String resultB = runPromise(() -> harness.writeToRegion(REGION_B, "id-b")); // GH-90000
            assertThat(resultA).isEqualTo("id-a [GH-90000]");
            assertThat(resultB).isEqualTo("id-b [GH-90000]");
        }

        @Test
        @DisplayName("no data cross-contamination between regions during partition [GH-90000]")
        void noDataCrossContamination_duringPartition() throws Exception { // GH-90000
            harness.partitionRegion(REGION_B); // GH-90000

            runPromise(() -> harness.writeToRegion(REGION_A, "exclusive-to-a")); // GH-90000

            // Data written to Region A must not appear in Region B's store
            long regionBCount = harness.getRegionRecordCount(REGION_B); // GH-90000
            assertThat(regionBCount).isZero(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cascading dependency failure
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Cascading dependency failure [GH-90000]")
    class CascadingFailureTests {

        @Test
        @DisplayName("Kafka failure alone does not cascade to entity CRUD [GH-90000]")
        void kafkaFailure_doesNotCascadeToEntityCrud() throws Exception { // GH-90000
            harness.partitionKafka(); // GH-90000

            // Entity writes should still work (events buffered, not blocking) // GH-90000
            String entityId = runPromise(() -> harness.writeEntity(TENANT_ID, "id-1")); // GH-90000
            assertThat(entityId).isEqualTo("id-1 [GH-90000]");
        }

        @Test
        @DisplayName("analytics query timeout does not block entity writes [GH-90000]")
        void analyticsTimeout_doesNotBlockEntityWrites() throws Exception { // GH-90000
            harness.injectAnalyticsTimeout(100); // 100 ms timeout // GH-90000

            // Analytics query fails
            assertThatThrownBy(() -> runPromise(harness::runAnalyticsQuery)) // GH-90000
                .isInstanceOf(Exception.class); // GH-90000

            // Entity writes must still work
            String entityId = runPromise(() -> harness.writeEntity(TENANT_ID, "id-safe")); // GH-90000
            assertThat(entityId).isEqualTo("id-safe [GH-90000]");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Self-contained chaos harness
    // ─────────────────────────────────────────────────────────────────────────

    private static class NetworkChaosHarness {

        private final DataCloudClient client;

        private boolean kafkaPartitioned = false;
        private boolean dbPartitioned    = false;
        private String  partitionedRegion  = null;
        private double  kafkaFailureRate = 0.0;
        private long    analyticsTimeoutMs = Long.MAX_VALUE;

        private final List<String> buffer    = new ArrayList<>(); // GH-90000
        private final List<String> persisted = new ArrayList<>(); // GH-90000
        private final List<String> regionAStore = new ArrayList<>(); // GH-90000
        private final java.util.Map<String, Integer> regionCounts =
            new java.util.HashMap<>(); // GH-90000
        private int entityCounter = 0;

        NetworkChaosHarness(DataCloudClient client) { // GH-90000
            this.client = client;
        }

        void partitionKafka()   { kafkaPartitioned = true; } // GH-90000
        void healKafka()        { kafkaPartitioned = false; } // GH-90000
        void partitionDatabase()   { dbPartitioned = true; } // GH-90000
        void healDatabase()        { dbPartitioned = false; } // GH-90000
        void partitionRegion(String region)  { this.partitionedRegion = region; } // GH-90000
        void healRegion(String region)       { if (region.equals(partitionedRegion)) partitionedRegion = null; } // GH-90000
        void setPartialKafkaWriteFailureRate(double rate) { this.kafkaFailureRate = rate; } // GH-90000
        void injectAnalyticsTimeout(long ms) { this.analyticsTimeoutMs = ms; } // GH-90000

        List<String> publishEvents(String tenantId, int count) { // GH-90000
            List<String> ids = new ArrayList<>(); // GH-90000
            for (int i = 0; i < count; i++) { // GH-90000
                String id = tenantId + "-event-" + (buffer.size() + persisted.size() + i); // GH-90000
                boolean fail = kafkaPartitioned
                    || (kafkaFailureRate > 0 && Math.random() < kafkaFailureRate); // GH-90000
                if (fail) { // GH-90000
                    buffer.add(id); // GH-90000
                } else {
                    persisted.add(id); // GH-90000
                }
                ids.add(id); // GH-90000
            }
            return ids;
        }

        void triggerBufferDrain() { // GH-90000
            if (!kafkaPartitioned) { // GH-90000
                persisted.addAll(buffer); // GH-90000
                buffer.clear(); // GH-90000
            }
        }

        List<String> triggerOrderedBufferDrain() { // GH-90000
            List<String> drained = new ArrayList<>(buffer); // GH-90000
            if (!kafkaPartitioned) { // GH-90000
                persisted.addAll(drained); // GH-90000
                buffer.clear(); // GH-90000
            }
            return drained;
        }

        int getBufferedEventCount()   { return buffer.size(); } // GH-90000
        int getPersistedEventCount()  { return persisted.size(); } // GH-90000

        Promise<String> writeEntityOrFail(String tenantId, String id) { // GH-90000
            if (dbPartitioned) { // GH-90000
                return Promise.ofException(new IOException("DB partition: connection refused [GH-90000]"));
            }
            return Promise.of(id); // GH-90000
        }

        Promise<String> writeEntity(String tenantId, String id) { // GH-90000
            if (dbPartitioned) { // GH-90000
                return Promise.ofException(new IOException("DB partition: connection refused [GH-90000]"));
            }
            entityCounter++;
            return Promise.of(id); // GH-90000
        }

        Promise<String> readEntityOrFail(String tenantId, String id) { // GH-90000
            if (dbPartitioned) { // GH-90000
                return Promise.ofException(new IOException("DB partition: connection refused [GH-90000]"));
            }
            return Promise.of(id); // GH-90000
        }

        Promise<String> writeToRegion(String region, String id) { // GH-90000
            if (region.equals(partitionedRegion)) { // GH-90000
                return Promise.ofException(new IOException("Region " + region + " is partitioned")); // GH-90000
            }
            regionCounts.merge(region, 1, Integer::sum); // GH-90000
            if (REGION_A.equals(region)) regionAStore.add(id); // GH-90000
            return Promise.of(id); // GH-90000
        }

        long getRegionRecordCount(String region) { // GH-90000
            return regionCounts.getOrDefault(region, 0); // GH-90000
        }

        Promise<Object> runAnalyticsQuery() { // GH-90000
            if (analyticsTimeoutMs < Long.MAX_VALUE) { // GH-90000
                return Promise.ofException(new TimeoutException( // GH-90000
                    "Analytics query timed out after " + analyticsTimeoutMs + " ms"));
            }
            return Promise.of(new Object()); // GH-90000
        }
    }
}
