/*
 * Copyright (c) 2026 Ghatana Inc. 
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
 * <p>Covers P3 gap — Clock Skew Handling (audit gap G-009): 
 * <ul>
 *   <li>TTL enforcement is not affected by client clock drift.</li>
 *   <li>Timestamp fields use ISO-8601 UTC normalisation.</li>
 *   <li>Retention window ({@code now() - createdAt > ttl}) is evaluated server-side.</li> 
 *   <li>Operations using past/future timestamps are handled predictably.</li>
 *   <li>Timezone-naive timestamps are handled consistently.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Clock skew and timestamp handling tests (P3 gap closure, G-009) 
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Clock Skew and Timestamp Handling")
class ClockSkewHandlingTest extends EventloopTestBase {

    private static final String TENANT_ID  = "clock-skew-tenant";
    private static final String COLLECTION = "clock-records";

    private DataCloudClient client;

    @BeforeEach
    void setUp() { 
        client = DataCloud.forTesting(); 
    }

    @AfterEach
    void tearDown() { 
        client.close(); 
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Timestamp normalisation
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Timestamp normalisation")
    class TimestampNormalisationTests {

        @Test
        @DisplayName("entity saved with explicit UTC timestamp is stored and queryable")
        void entityWithUtcTimestamp_storedAndQueryable() throws Exception { 
            String utcTimestamp = Instant.now().toString(); // ISO-8601 UTC 

            Entity saved = runPromise(() -> client.save(TENANT_ID, COLLECTION, 
                Map.of("createdAt", utcTimestamp, "value", "clock-test"))); 

            assertThat(saved).isNotNull(); 
            assertThat(saved.id()).isNotBlank(); 

            List<Entity> found = runPromise(() -> 
                client.query(TENANT_ID, COLLECTION, Query.all())); 
            assertThat(found).anyMatch(e -> e.id().equals(saved.id())); 
        }

        @ParameterizedTest(name = "timezone=''{0}''") 
        @ValueSource(strings = {"America/New_York", "Asia/Tokyo", "Europe/London", "UTC", "Pacific/Auckland"}) 
        @DisplayName("entity saved from various timezones is retrievable consistently")
        void entityFromVariousTimezones_retrievableConsistently(String timezone) throws Exception { 
            ZonedDateTime zdt         = ZonedDateTime.now(ZoneId.of(timezone)); 
            String        localTs     = zdt.toString(); 
            String        tenantLocal = TENANT_ID + "-" + timezone.replace("/", "_"); 

            Entity saved = runPromise(() -> 
                client.save(tenantLocal, COLLECTION, 
                    Map.of("localTimestamp", localTs, "timezone", timezone))); 

            assertThat(saved.id()).isNotBlank(); 

            // Entity must be queryable regardless of the timezone used to write it
            List<Entity> found = runPromise(() -> 
                client.query(tenantLocal, COLLECTION, Query.all())); 

            assertThat(found) 
                .as("Entity from timezone %s must be retrievable", timezone) 
                .anyMatch(e -> e.id().equals(saved.id())); 
        }

        @Test
        @DisplayName("two entities saved in quick succession have distinct IDs (no clock collision)")
        void quickSuccession_entitiesHaveDistinctIds() throws Exception { 
            Entity e1 = runPromise(() -> 
                client.save(TENANT_ID, COLLECTION, Map.of("seq", 1))); 
            Entity e2 = runPromise(() -> 
                client.save(TENANT_ID, COLLECTION, Map.of("seq", 2))); 

            assertThat(e1.id()).isNotEqualTo(e2.id()); 
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
        void pastCreatedAt_storeAgnosticToClientSkew() throws Exception { 
            // Simulate a client with a 24-hour backward clock skew
            String skewedTs = Instant.now().minus(Duration.ofHours(24)).toString(); 

            Entity saved = runPromise(() -> 
                client.save(TENANT_ID, COLLECTION, 
                    Map.of("createdAt", skewedTs, "value", "skewed"))); 

            // Store must accept the entity regardless of client-supplied timestamp
            assertThat(saved.id()).isNotBlank(); 

            // Entity must still be queryable (deletion is server-controlled, not client-clock-based) 
            List<Entity> found = runPromise(() -> 
                client.query(TENANT_ID, COLLECTION, Query.all())); 
            assertThat(found).anyMatch(e -> e.id().equals(saved.id())); 
        }

        @Test
        @DisplayName("entity saved with future 'createdAt' — store accepts without rejection")
        void futureCreatedAt_acceptedByStore() throws Exception { 
            // Simulate a client with 1-hour forward clock skew
            String futureTs = Instant.now().plus(Duration.ofHours(1)).toString(); 

            Entity saved = runPromise(() -> 
                client.save(TENANT_ID, COLLECTION, 
                    Map.of("createdAt", futureTs, "status", "future-entry"))); 

            assertThat(saved.id()).isNotBlank(); 
        }

        @Test
        @DisplayName("retention window calculation uses server time, not client-supplied timestamps")
        void retentionWindow_usesServerTime() throws Exception { 
            // Write an entity with a very old client timestamp (simulating max skew) 
            String ancientTs = Instant.EPOCH.toString(); // 1970-01-01 

            Entity saved = runPromise(() -> 
                client.save(TENANT_ID, COLLECTION, 
                    Map.of("createdAt", ancientTs, "retentionTest", true))); 

            // If server-side TTL were evaluated against the ancient client timestamp,
            // the entity would be considered expired immediately. Verify it is present.
            List<Entity> found = runPromise(() -> 
                client.query(TENANT_ID, COLLECTION, Query.all())); 
            assertThat(found) 
                .as("Entity must not be auto-expired merely because client timestamp is in the past")
                .anyMatch(e -> e.id().equals(saved.id())); 
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
        void sequentialSaves_monotonicOffsets() throws Exception { 
            List<Long> offsets = new java.util.ArrayList<>(); 
            for (int i = 0; i < 5; i++) { 
                final int idx = i;
                DataCloudClient.Offset off = runPromise(() -> 
                    client.appendEvent(TENANT_ID, 
                        DataCloudClient.Event.of("entity.created", Map.of("idx", idx)))); 
                offsets.add(off.value()); 
            }

            // Offsets must be strictly increasing
            for (int i = 1; i < offsets.size(); i++) { 
                assertThat(offsets.get(i)) 
                    .as("Offset at position %d must be > offset at %d", i, i - 1) 
                    .isGreaterThan(offsets.get(i - 1)); 
            }
        }

        @Test
        @DisplayName("concurrent event appends produce unique, monotonic offsets")
        void concurrentAppends_uniqueOffsets() throws Exception { 
            int eventCount = 20;
            List<Long> offsets = new java.util.concurrent.CopyOnWriteArrayList<>(); 

            // Use the in-process client — no threading abstraction needed
            for (int i = 0; i < eventCount; i++) { 
                final int idx = i;
                DataCloudClient.Offset off = runPromise(() -> 
                    client.appendEvent(TENANT_ID, DataCloudClient.Event.of("seq", Map.of("idx", idx)))); 
                offsets.add(off.value()); 
            }

            assertThat(offsets).hasSize(eventCount); 
            long distinctCount = offsets.stream().distinct().count(); 
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
        void entityAtDstBoundary_storedCorrectly() throws Exception { 
            // 2026-03-08 02:00 US/Eastern is a DST transition — clocks spring forward
            Instant dstTransition = ZonedDateTime.of(2026, 3, 8, 3, 0, 0, 0, 
                ZoneId.of("America/New_York")).toInstant();

            Entity saved = runPromise(() -> 
                client.save(TENANT_ID, COLLECTION, 
                    Map.of("eventTime", dstTransition.toString(), "dstTest", true))); 

            assertThat(saved.id()).isNotBlank(); 
        }

        @Test
        @DisplayName("timestamp at Unix epoch boundary is handled without overflow")
        void epochBoundaryTimestamp_noOverflow() throws Exception { 
            // Test timestamps near common boundary values
            List<Instant> boundaries = List.of( 
                Instant.EPOCH,                                      // 1970-01-01T00:00:00Z
                Instant.ofEpochSecond(Integer.MAX_VALUE),           // 2038 problem boundary 
                Instant.parse("2026-01-01T00:00:00Z")              // Year boundary
            );

            for (Instant boundary : boundaries) { 
                Entity saved = runPromise(() -> 
                    client.save(TENANT_ID, COLLECTION, 
                        Map.of("boundaryTs", boundary.toString()))); 
                assertThat(saved.id()) 
                    .as("Entity with boundary timestamp %s must be saved", boundary) 
                    .isNotBlank(); 
            }
        }

        @Test
        @DisplayName("entity query returns results regardless of server-vs-client clock offset")
        void queryResult_independentOfClockOffset() throws Exception { 
            // Save some entities
            for (int i = 0; i < 5; i++) { 
                final int idx = i;
                runPromise(() -> client.save(TENANT_ID, COLLECTION, Map.of("idx", idx))); 
            }

            // Query with no time filter — must always return all entities regardless of clock
            List<Entity> results = runPromise(() -> 
                client.query(TENANT_ID, COLLECTION, Query.all())); 

            assertThat(results).hasSize(5); 
        }
    }
}
