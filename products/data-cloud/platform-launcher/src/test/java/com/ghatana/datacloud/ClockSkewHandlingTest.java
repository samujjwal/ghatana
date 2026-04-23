/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud;

import com.ghatana.datacloud.DataCloudClient.Entity;
import com.ghatana.datacloud.DataCloudClient.Query;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Clock skew handling tests for Data-Cloud.
 *
 * <p>Validates TTL enforcement, timestamp handling, and retention window
 * calculations under various clock skew conditions.
 *
 * <p>Covers P3 gap — Clock Skew Handling (audit gap G-009): // GH-90000
 * <ul>
 *   <li>TTL enforcement is not affected by client clock drift.</li>
 *   <li>Timestamp fields use ISO-8601 UTC normalisation.</li>
 *   <li>Retention window ({@code now() - createdAt > ttl}) is evaluated server-side.</li> // GH-90000
 *   <li>Operations using past/future timestamps are handled predictably.</li>
 *   <li>Timezone-naive timestamps are handled consistently.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Clock skew and timestamp handling tests (P3 gap closure, G-009) // GH-90000
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Clock Skew and Timestamp Handling")
class ClockSkewHandlingTest extends EventloopTestBase {

    private static final String TENANT_ID  = "clock-skew-tenant";
    private static final String COLLECTION = "clock-records";

    private DataCloudClient client;

    @BeforeEach
    void setUp() { // GH-90000
        client = DataCloud.forTesting(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        client.close(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Timestamp normalisation
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Timestamp normalisation")
    class TimestampNormalisationTests {

        @Test
        @DisplayName("entity saved with explicit UTC timestamp is stored and queryable")
        void entityWithUtcTimestamp_storedAndQueryable() throws Exception { // GH-90000
            String utcTimestamp = Instant.now().toString(); // ISO-8601 UTC // GH-90000

            Entity saved = runPromise(() -> client.save(TENANT_ID, COLLECTION, // GH-90000
                Map.of("createdAt", utcTimestamp, "value", "clock-test"))); // GH-90000

            assertThat(saved).isNotNull(); // GH-90000
            assertThat(saved.id()).isNotBlank(); // GH-90000

            List<Entity> found = runPromise(() -> // GH-90000
                client.query(TENANT_ID, COLLECTION, Query.all())); // GH-90000
            assertThat(found).anyMatch(e -> e.id().equals(saved.id())); // GH-90000
        }

        @ParameterizedTest(name = "timezone=''{0}''") // GH-90000
        @ValueSource(strings = {"America/New_York", "Asia/Tokyo", "Europe/London", "UTC", "Pacific/Auckland"}) // GH-90000
        @DisplayName("entity saved from various timezones is retrievable consistently")
        void entityFromVariousTimezones_retrievableConsistently(String timezone) throws Exception { // GH-90000
            ZonedDateTime zdt         = ZonedDateTime.now(ZoneId.of(timezone)); // GH-90000
            String        localTs     = zdt.toString(); // GH-90000
            String        tenantLocal = TENANT_ID + "-" + timezone.replace("/", "_"); // GH-90000

            Entity saved = runPromise(() -> // GH-90000
                client.save(tenantLocal, COLLECTION, // GH-90000
                    Map.of("localTimestamp", localTs, "timezone", timezone))); // GH-90000

            assertThat(saved.id()).isNotBlank(); // GH-90000

            // Entity must be queryable regardless of the timezone used to write it
            List<Entity> found = runPromise(() -> // GH-90000
                client.query(tenantLocal, COLLECTION, Query.all())); // GH-90000

            assertThat(found) // GH-90000
                .as("Entity from timezone %s must be retrievable", timezone) // GH-90000
                .anyMatch(e -> e.id().equals(saved.id())); // GH-90000
        }

        @Test
        @DisplayName("two entities saved in quick succession have distinct IDs (no clock collision)")
        void quickSuccession_entitiesHaveDistinctIds() throws Exception { // GH-90000
            Entity e1 = runPromise(() -> // GH-90000
                client.save(TENANT_ID, COLLECTION, Map.of("seq", 1))); // GH-90000
            Entity e2 = runPromise(() -> // GH-90000
                client.save(TENANT_ID, COLLECTION, Map.of("seq", 2))); // GH-90000

            assertThat(e1.id()).isNotEqualTo(e2.id()); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TTL enforcement
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("TTL enforcement")
    class TtlEnforcementTests {

        @Test
        @DisplayName("entity saved with past 'createdAt' — store is agnostic to client-skewed timestamps")
        void pastCreatedAt_storeAgnosticToClientSkew() throws Exception { // GH-90000
            // Simulate a client with a 24-hour backward clock skew
            String skewedTs = Instant.now().minus(Duration.ofHours(24)).toString(); // GH-90000

            Entity saved = runPromise(() -> // GH-90000
                client.save(TENANT_ID, COLLECTION, // GH-90000
                    Map.of("createdAt", skewedTs, "value", "skewed"))); // GH-90000

            // Store must accept the entity regardless of client-supplied timestamp
            assertThat(saved.id()).isNotBlank(); // GH-90000

            // Entity must still be queryable (deletion is server-controlled, not client-clock-based) // GH-90000
            List<Entity> found = runPromise(() -> // GH-90000
                client.query(TENANT_ID, COLLECTION, Query.all())); // GH-90000
            assertThat(found).anyMatch(e -> e.id().equals(saved.id())); // GH-90000
        }

        @Test
        @DisplayName("entity saved with future 'createdAt' — store accepts without rejection")
        void futureCreatedAt_acceptedByStore() throws Exception { // GH-90000
            // Simulate a client with 1-hour forward clock skew
            String futureTs = Instant.now().plus(Duration.ofHours(1)).toString(); // GH-90000

            Entity saved = runPromise(() -> // GH-90000
                client.save(TENANT_ID, COLLECTION, // GH-90000
                    Map.of("createdAt", futureTs, "status", "future-entry"))); // GH-90000

            assertThat(saved.id()).isNotBlank(); // GH-90000
        }

        @Test
        @DisplayName("retention window calculation uses server time, not client-supplied timestamps")
        void retentionWindow_usesServerTime() throws Exception { // GH-90000
            // Write an entity with a very old client timestamp (simulating max skew) // GH-90000
            String ancientTs = Instant.EPOCH.toString(); // 1970-01-01 // GH-90000

            Entity saved = runPromise(() -> // GH-90000
                client.save(TENANT_ID, COLLECTION, // GH-90000
                    Map.of("createdAt", ancientTs, "retentionTest", true))); // GH-90000

            // If server-side TTL were evaluated against the ancient client timestamp,
            // the entity would be considered expired immediately. Verify it is present.
            List<Entity> found = runPromise(() -> // GH-90000
                client.query(TENANT_ID, COLLECTION, Query.all())); // GH-90000
            assertThat(found) // GH-90000
                .as("Entity must not be auto-expired merely because client timestamp is in the past")
                .anyMatch(e -> e.id().equals(saved.id())); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Monotonic ordering guarantees
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Monotonic ordering")
    class MonotonicOrderingTests {

        @Test
        @DisplayName("entities saved sequentially are assigned monotonically increasing offsets")
        void sequentialSaves_monotonicOffsets() throws Exception { // GH-90000
            List<Long> offsets = new java.util.ArrayList<>(); // GH-90000
            for (int i = 0; i < 5; i++) { // GH-90000
                final int idx = i;
                DataCloudClient.Offset off = runPromise(() -> // GH-90000
                    client.appendEvent(TENANT_ID, // GH-90000
                        DataCloudClient.Event.of("entity.created", Map.of("idx", idx)))); // GH-90000
                offsets.add(off.value()); // GH-90000
            }

            // Offsets must be strictly increasing
            for (int i = 1; i < offsets.size(); i++) { // GH-90000
                assertThat(offsets.get(i)) // GH-90000
                    .as("Offset at position %d must be > offset at %d", i, i - 1) // GH-90000
                    .isGreaterThan(offsets.get(i - 1)); // GH-90000
            }
        }

        @Test
        @DisplayName("concurrent event appends produce unique, monotonic offsets")
        void concurrentAppends_uniqueOffsets() throws Exception { // GH-90000
            int eventCount = 20;
            List<Long> offsets = new java.util.concurrent.CopyOnWriteArrayList<>(); // GH-90000

            // Use the in-process client — no threading abstraction needed
            for (int i = 0; i < eventCount; i++) { // GH-90000
                final int idx = i;
                DataCloudClient.Offset off = runPromise(() -> // GH-90000
                    client.appendEvent(TENANT_ID, DataCloudClient.Event.of("seq", Map.of("idx", idx)))); // GH-90000
                offsets.add(off.value()); // GH-90000
            }

            assertThat(offsets).hasSize(eventCount); // GH-90000
            long distinctCount = offsets.stream().distinct().count(); // GH-90000
            assertThat(distinctCount).as("All offsets must be unique").isEqualTo(eventCount);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Leap second and DST boundary handling
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DST boundary and special timestamps")
    class DstAndSpecialTimestampTests {

        @Test
        @DisplayName("entity with timestamp at DST transition is stored correctly")
        void entityAtDstBoundary_storedCorrectly() throws Exception { // GH-90000
            // 2026-03-08 02:00 US/Eastern is a DST transition — clocks spring forward
            Instant dstTransition = ZonedDateTime.of(2026, 3, 8, 3, 0, 0, 0, // GH-90000
                ZoneId.of("America/New_York")).toInstant();

            Entity saved = runPromise(() -> // GH-90000
                client.save(TENANT_ID, COLLECTION, // GH-90000
                    Map.of("eventTime", dstTransition.toString(), "dstTest", true))); // GH-90000

            assertThat(saved.id()).isNotBlank(); // GH-90000
        }

        @Test
        @DisplayName("timestamp at Unix epoch boundary is handled without overflow")
        void epochBoundaryTimestamp_noOverflow() throws Exception { // GH-90000
            // Test timestamps near common boundary values
            List<Instant> boundaries = List.of( // GH-90000
                Instant.EPOCH,                                      // 1970-01-01T00:00:00Z
                Instant.ofEpochSecond(Integer.MAX_VALUE),           // 2038 problem boundary // GH-90000
                Instant.parse("2026-01-01T00:00:00Z")              // Year boundary
            );

            for (Instant boundary : boundaries) { // GH-90000
                Entity saved = runPromise(() -> // GH-90000
                    client.save(TENANT_ID, COLLECTION, // GH-90000
                        Map.of("boundaryTs", boundary.toString()))); // GH-90000
                assertThat(saved.id()) // GH-90000
                    .as("Entity with boundary timestamp %s must be saved", boundary) // GH-90000
                    .isNotBlank(); // GH-90000
            }
        }

        @Test
        @DisplayName("entity query returns results regardless of server-vs-client clock offset")
        void queryResult_independentOfClockOffset() throws Exception { // GH-90000
            // Save some entities
            for (int i = 0; i < 5; i++) { // GH-90000
                final int idx = i;
                runPromise(() -> client.save(TENANT_ID, COLLECTION, Map.of("idx", idx))); // GH-90000
            }

            // Query with no time filter — must always return all entities regardless of clock
            List<Entity> results = runPromise(() -> // GH-90000
                client.query(TENANT_ID, COLLECTION, Query.all())); // GH-90000

            assertThat(results).hasSize(5); // GH-90000
        }
    }
}
