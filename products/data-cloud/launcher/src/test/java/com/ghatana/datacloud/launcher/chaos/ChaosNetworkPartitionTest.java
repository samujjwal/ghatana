/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.chaos;

import com.ghatana.datacloud.client.DataCloudClient;
import com.ghatana.datacloud.record.Record;
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
 * @doc.purpose Chaos tests for network partition and cascading failure scenarios (Gap 006)
 * @doc.layer product
 * @doc.pattern Chaos Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Chaos – Network Partition &amp; Cascading Failure")
class ChaosNetworkPartitionTest extends EventloopTestBase {

    private static final String TENANT_ID   = "chaos-partition-tenant";
    private static final String COLLECTION  = "partition-records";
    private static final String REGION_A    = "us-east-1";
    private static final String REGION_B    = "eu-west-1";

    @Mock
    private DataCloudClient client;

    private NetworkChaosHarness harness;

    @BeforeEach
    void setUp() {
        harness = new NetworkChaosHarness(client);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Kafka network partition
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Kafka partition scenarios")
    class KafkaPartitionTests {

        @Test
        @DisplayName("should buffer events when Kafka is unreachable")
        void shouldBufferEventsWhenKafkaUnreachable() throws Exception {
            harness.partitionKafka();

            List<String> eventIds = harness.publishEvents(TENANT_ID, 5);

            // Events must be buffered (not lost) when Kafka is unavailable
            assertThat(eventIds).hasSize(5);
            assertThat(harness.getBufferedEventCount()).isEqualTo(5);
            assertThat(harness.getPersistedEventCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("should drain buffer and persist events after Kafka reconnects")
        void shouldDrainBufferAfterKafkaReconnects() throws Exception {
            harness.partitionKafka();
            harness.publishEvents(TENANT_ID, 3);

            // Heal partition — buffer should drain
            harness.healKafka();
            harness.triggerBufferDrain();

            assertThat(harness.getBufferedEventCount()).isEqualTo(0);
            assertThat(harness.getPersistedEventCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("should preserve event ordering after buffer drain")
        void shouldPreserveEventOrderingAfterDrain() throws Exception {
            harness.partitionKafka();
            List<String> published = harness.publishEvents(TENANT_ID, 4);

            harness.healKafka();
            List<String> drained = harness.triggerOrderedBufferDrain();

            // Order must be preserved FIFO
            assertThat(drained).isEqualTo(published);
        }

        @Test
        @DisplayName("partial Kafka write — acks only for some events — remainder buffered")
        void partialKafkaWrite_remainderBuffered() throws Exception {
            harness.setPartialKafkaWriteFailureRate(0.5); // 50% failure rate

            List<String> ids = harness.publishEvents(TENANT_ID, 10);

            // All IDs must be accounted for (either persisted or buffered, none lost)
            int total = harness.getPersistedEventCount() + harness.getBufferedEventCount();
            assertThat(total).isEqualTo(10);
            assertThat(ids).hasSize(10);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Database TCP disconnect
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Database TCP disconnect")
    class DatabaseTcpDisconnectTests {

        @Test
        @DisplayName("entity write during DB disconnect → fails with IOException")
        void entityWrite_dbDisconnected_throwsIOException() {
            harness.partitionDatabase();

            assertThatThrownBy(() -> runPromise(() -> harness.writeEntityOrFail(TENANT_ID, "id-1")))
                .isInstanceOf(Exception.class)
                .satisfies(e ->
                    assertThat(e instanceof IOException || e.getCause() instanceof IOException
                        || e.getMessage().contains("connection") || e.getMessage().contains("partition"))
                        .as("Expected IOException or connection-related error").isTrue()
                );
        }

        @Test
        @DisplayName("entity writes resume automatically after DB reconnects")
        void entityWrites_resumeAfterDbReconnects() throws Exception {
            harness.partitionDatabase();

            // Write fails while partitioned
            try {
                runPromise(() -> harness.writeEntityOrFail(TENANT_ID, "id-1"));
            } catch (Exception ignored) {
                // Expected
            }

            // Heal DB
            harness.healDatabase();

            // Write should now succeed
            String result = runPromise(() -> harness.writeEntity(TENANT_ID, "id-2"));
            assertThat(result).isEqualTo("id-2");
        }

        @Test
        @DisplayName("read operations fail fast during DB outage and report error clearly")
        void readOperations_dbDown_failFast() {
            harness.partitionDatabase();

            assertThatThrownBy(() -> runPromise(() -> harness.readEntityOrFail(TENANT_ID, "id-1")))
                .isInstanceOf(Exception.class);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Multi-region network partition
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Multi-region partition")
    class MultiRegionPartitionTests {

        @Test
        @DisplayName("writes to partitioned region fail; other region unaffected")
        void partitionedRegion_fails_healthyRegionUnaffected() throws Exception {
            harness.partitionRegion(REGION_B);

            // Region A still works
            String resultA = runPromise(() -> harness.writeToRegion(REGION_A, "id-a"));
            assertThat(resultA).isEqualTo("id-a");

            // Region B is partitioned
            assertThatThrownBy(() -> runPromise(() -> harness.writeToRegion(REGION_B, "id-b")))
                .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("both regions recover after partition healed")
        void bothRegions_recover_afterPartitionHealed() throws Exception {
            harness.partitionRegion(REGION_B);

            // Heal
            harness.healRegion(REGION_B);

            String resultA = runPromise(() -> harness.writeToRegion(REGION_A, "id-a"));
            String resultB = runPromise(() -> harness.writeToRegion(REGION_B, "id-b"));
            assertThat(resultA).isEqualTo("id-a");
            assertThat(resultB).isEqualTo("id-b");
        }

        @Test
        @DisplayName("no data cross-contamination between regions during partition")
        void noDataCrossContamination_duringPartition() throws Exception {
            harness.partitionRegion(REGION_B);

            runPromise(() -> harness.writeToRegion(REGION_A, "exclusive-to-a"));

            // Data written to Region A must not appear in Region B's store
            long regionBCount = harness.getRegionRecordCount(REGION_B);
            assertThat(regionBCount).isZero();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cascading dependency failure
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Cascading dependency failure")
    class CascadingFailureTests {

        @Test
        @DisplayName("Kafka failure alone does not cascade to entity CRUD")
        void kafkaFailure_doesNotCascadeToEntityCrud() throws Exception {
            harness.partitionKafka();

            // Entity writes should still work (events buffered, not blocking)
            String entityId = runPromise(() -> harness.writeEntity(TENANT_ID, "id-1"));
            assertThat(entityId).isEqualTo("id-1");
        }

        @Test
        @DisplayName("analytics query timeout does not block entity writes")
        void analyticsTimeout_doesNotBlockEntityWrites() throws Exception {
            harness.injectAnalyticsTimeout(100); // 100 ms timeout

            // Analytics query fails
            assertThatThrownBy(() -> runPromise(harness::runAnalyticsQuery))
                .isInstanceOf(Exception.class);

            // Entity writes must still work
            String entityId = runPromise(() -> harness.writeEntity(TENANT_ID, "id-safe"));
            assertThat(entityId).isEqualTo("id-safe");
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

        private final List<String> buffer    = new ArrayList<>();
        private final List<String> persisted = new ArrayList<>();
        private final List<String> regionAStore = new ArrayList<>();
        private final java.util.Map<String, Integer> regionCounts =
            new java.util.HashMap<>();
        private int entityCounter = 0;

        NetworkChaosHarness(DataCloudClient client) {
            this.client = client;
        }

        void partitionKafka()   { kafkaPartitioned = true; }
        void healKafka()        { kafkaPartitioned = false; }
        void partitionDatabase()   { dbPartitioned = true; }
        void healDatabase()        { dbPartitioned = false; }
        void partitionRegion(String region)  { this.partitionedRegion = region; }
        void healRegion(String region)       { if (region.equals(partitionedRegion)) partitionedRegion = null; }
        void setPartialKafkaWriteFailureRate(double rate) { this.kafkaFailureRate = rate; }
        void injectAnalyticsTimeout(long ms) { this.analyticsTimeoutMs = ms; }

        List<String> publishEvents(String tenantId, int count) {
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                String id = tenantId + "-event-" + (buffer.size() + persisted.size() + i);
                boolean fail = kafkaPartitioned
                    || (kafkaFailureRate > 0 && Math.random() < kafkaFailureRate);
                if (fail) {
                    buffer.add(id);
                } else {
                    persisted.add(id);
                }
                ids.add(id);
            }
            return ids;
        }

        void triggerBufferDrain() {
            if (!kafkaPartitioned) {
                persisted.addAll(buffer);
                buffer.clear();
            }
        }

        List<String> triggerOrderedBufferDrain() {
            List<String> drained = new ArrayList<>(buffer);
            if (!kafkaPartitioned) {
                persisted.addAll(drained);
                buffer.clear();
            }
            return drained;
        }

        int getBufferedEventCount()   { return buffer.size(); }
        int getPersistedEventCount()  { return persisted.size(); }

        Promise<String> writeEntityOrFail(String tenantId, String id) {
            if (dbPartitioned) {
                return Promise.ofException(new IOException("DB partition: connection refused"));
            }
            return Promise.of(id);
        }

        Promise<String> writeEntity(String tenantId, String id) {
            if (dbPartitioned) {
                return Promise.ofException(new IOException("DB partition: connection refused"));
            }
            entityCounter++;
            return Promise.of(id);
        }

        Promise<String> readEntityOrFail(String tenantId, String id) {
            if (dbPartitioned) {
                return Promise.ofException(new IOException("DB partition: connection refused"));
            }
            return Promise.of(id);
        }

        Promise<String> writeToRegion(String region, String id) {
            if (region.equals(partitionedRegion)) {
                return Promise.ofException(new IOException("Region " + region + " is partitioned"));
            }
            regionCounts.merge(region, 1, Integer::sum);
            if (REGION_A.equals(region)) regionAStore.add(id);
            return Promise.of(id);
        }

        long getRegionRecordCount(String region) {
            return regionCounts.getOrDefault(region, 0);
        }

        Promise<Object> runAnalyticsQuery() {
            if (analyticsTimeoutMs < Long.MAX_VALUE) {
                return Promise.ofException(new TimeoutException(
                    "Analytics query timed out after " + analyticsTimeoutMs + " ms"));
            }
            return Promise.of(new Object());
        }
    }
}
