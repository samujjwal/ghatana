/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.storage;

import com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry;
import com.ghatana.platform.domain.eventstore.EventLogStore.Subscription;
import com.ghatana.platform.domain.eventstore.TenantContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.types.identity.Offset;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link WarmTierEventLogStore} using a real PostgreSQL
 * container (Testcontainers). Verifies the full event log lifecycle: append, // GH-90000
 * batch append, read, readByType, readByTimeRange, offset management,
 * multi-tenant isolation, and tail subscription.
 *
 * <p>Schema from {@code V005__create_event_log.sql} is applied inline to keep
 * the tests independent of Flyway migration ordering.
 *
 * @doc.type class
 * @doc.purpose Integration tests for PostgreSQL warm-tier EventLogStore
 * @doc.layer product
 * @doc.pattern Test
 */
@Testcontainers(disabledWithoutDocker = true) // GH-90000
@DisplayName("WarmTierEventLogStore [GH-90000]")
class WarmTierEventLogStoreTest extends EventloopTestBase {

    @Container
    @SuppressWarnings("resource [GH-90000]")
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine [GH-90000]")
                    .withDatabaseName("dc_test [GH-90000]")
                    .withUsername("dc_user [GH-90000]")
                    .withPassword("dc_pass [GH-90000]");

    /** DDL from V005__create_event_log.sql — applied once per class, truncated between tests. */
    private static final String DDL = """
            CREATE EXTENSION IF NOT EXISTS "pgcrypto";
            CREATE TABLE IF NOT EXISTS event_log ( // GH-90000
                offset_value     BIGINT          GENERATED ALWAYS AS IDENTITY,
                tenant_id        VARCHAR(255)    NOT NULL, // GH-90000
                event_id         UUID            NOT NULL,
                event_type       VARCHAR(255)    NOT NULL, // GH-90000
                event_version    VARCHAR(64)     NOT NULL DEFAULT '1.0.0', // GH-90000
                payload          BYTEA           NOT NULL,
                content_type     VARCHAR(128)    NOT NULL DEFAULT 'application/json', // GH-90000
                headers          JSONB           NOT NULL DEFAULT '{}',
                idempotency_key  VARCHAR(255), // GH-90000
                created_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(), // GH-90000
                CONSTRAINT pk_event_log      PRIMARY KEY (offset_value), // GH-90000
                CONSTRAINT uk_event_log_id   UNIQUE (tenant_id, event_id), // GH-90000
                CONSTRAINT uk_event_log_idem UNIQUE (tenant_id, idempotency_key) // GH-90000
            );
            CREATE INDEX IF NOT EXISTS idx_event_log_tenant_offset
                ON event_log (tenant_id, offset_value); // GH-90000
            CREATE INDEX IF NOT EXISTS idx_event_log_type
                ON event_log (tenant_id, event_type, offset_value); // GH-90000
            CREATE INDEX IF NOT EXISTS idx_event_log_created_at
                ON event_log (tenant_id, created_at DESC); // GH-90000
            """;

    private static HikariDataSource dataSource;

    private static final TenantContext TENANT_A = TenantContext.of("tenant-a [GH-90000]");
    private static final TenantContext TENANT_B = TenantContext.of("tenant-b [GH-90000]");

    @BeforeAll
    static void createSchema() throws Exception { // GH-90000
        HikariConfig cfg = new HikariConfig(); // GH-90000
        cfg.setJdbcUrl(POSTGRES.getJdbcUrl()); // GH-90000
        cfg.setUsername(POSTGRES.getUsername()); // GH-90000
        cfg.setPassword(POSTGRES.getPassword()); // GH-90000
        cfg.setMaximumPoolSize(5); // GH-90000
        dataSource = new HikariDataSource(cfg); // GH-90000

        try (Connection conn = dataSource.getConnection(); // GH-90000
             Statement stmt = conn.createStatement()) { // GH-90000
            stmt.execute(DDL); // GH-90000
        }
    }

    @AfterAll
    static void tearDownDataSource() { // GH-90000
        if (dataSource != null) { // GH-90000
            dataSource.close(); // GH-90000
        }
    }

    @BeforeEach
    void truncate() throws Exception { // GH-90000
        try (Connection conn = dataSource.getConnection(); // GH-90000
             Statement stmt = conn.createStatement()) { // GH-90000
            // RESTART IDENTITY resets the generated-identity sequence so that
            // offset values are predictable across test methods.
            stmt.execute("TRUNCATE TABLE event_log RESTART IDENTITY [GH-90000]");
        }
    }

    private WarmTierEventLogStore store() { // GH-90000
        return new WarmTierEventLogStore(dataSource); // GH-90000
    }

    private static EventEntry entry(String eventType) { // GH-90000
        return EventEntry.builder() // GH-90000
                .eventType(eventType) // GH-90000
                .payload("{\"key\":\"value\"}".getBytes()) // GH-90000
                .build(); // GH-90000
    }

    private static EventEntry entryWithIdempotencyKey(String eventType, String key) { // GH-90000
        return EventEntry.builder() // GH-90000
                .eventType(eventType) // GH-90000
                .payload("{\"key\":\"value\"}".getBytes()) // GH-90000
                .idempotencyKey(key) // GH-90000
                .build(); // GH-90000
    }

    // =========================================================================
    //  Append
    // =========================================================================

    @Nested
    @DisplayName("append() [GH-90000]")
    class Append {

        @Test
        @DisplayName("happy path — returns non-zero offset [GH-90000]")
        void append_returnsNonZeroOffset() { // GH-90000
            Offset offset = runPromise(() -> store().append(TENANT_A, entry("user.created [GH-90000]")));

            assertThat(offset).isNotNull(); // GH-90000
            long offsetVal = Long.parseLong(offset.value()); // GH-90000
            assertThat(offsetVal).isGreaterThan(0); // GH-90000
        }

        @Test
        @DisplayName("successive appends return monotonically increasing offsets [GH-90000]")
        void append_offsetsIncreaseMonotonically() { // GH-90000
            Offset o1 = runPromise(() -> store().append(TENANT_A, entry("event.a [GH-90000]")));
            Offset o2 = runPromise(() -> store().append(TENANT_A, entry("event.b [GH-90000]")));
            Offset o3 = runPromise(() -> store().append(TENANT_A, entry("event.c [GH-90000]")));

            long v1 = Long.parseLong(o1.value()); // GH-90000
            long v2 = Long.parseLong(o2.value()); // GH-90000
            long v3 = Long.parseLong(o3.value()); // GH-90000

            assertThat(v2).isGreaterThan(v1); // GH-90000
            assertThat(v3).isGreaterThan(v2); // GH-90000
        }

        @Test
        @DisplayName("null entry throws NullPointerException [GH-90000]")
        void append_nullEntry_throwsNpe() { // GH-90000
            assertThatNullPointerException() // GH-90000
                    .isThrownBy(() -> runPromise(() -> store().append(TENANT_A, null))); // GH-90000
            clearFatalError(); // GH-90000
        }

        @Test
        @DisplayName("duplicate idempotency key throws exception [GH-90000]")
        void append_duplicateIdempotencyKey_throws() { // GH-90000
            runPromise(() -> store().append(TENANT_A, entryWithIdempotencyKey("e.x", "idem-1"))); // GH-90000

            assertThatThrownBy(() -> runPromise(() -> // GH-90000
                    store().append(TENANT_A, entryWithIdempotencyKey("e.y", "idem-1")))) // GH-90000
                    .isInstanceOf(Exception.class); // GH-90000
            clearFatalError(); // GH-90000
        }

        @Test
        @DisplayName("headers are persisted and returned [GH-90000]")
        void append_headersRoundTrip() { // GH-90000
            EventEntry withHeaders = EventEntry.builder() // GH-90000
                    .eventType("user.updated [GH-90000]")
                    .payload("{\"name\":\"Alice\"}".getBytes()) // GH-90000
                    .headers(Map.of("source", "api", "version", "v2")) // GH-90000
                    .build(); // GH-90000

            Offset offset = runPromise(() -> store().append(TENANT_A, withHeaders)); // GH-90000
            List<EventEntry> read = runPromise(() -> store().read(TENANT_A, Offset.of(0), 10)); // GH-90000

            assertThat(read).hasSize(1); // GH-90000
            assertThat(read.get(0).headers()).containsEntry("source", "api").containsEntry("version", "v2"); // GH-90000
        }
    }

    // =========================================================================
    //  AppendBatch
    // =========================================================================

    @Nested
    @DisplayName("appendBatch() [GH-90000]")
    class AppendBatch {

        @Test
        @DisplayName("returns one offset per entry [GH-90000]")
        void appendBatch_returnsCorrectCount() { // GH-90000
            List<EventEntry> entries = List.of( // GH-90000
                    entry("e.one [GH-90000]"), entry("e.two [GH-90000]"), entry("e.three [GH-90000]")
            );

            List<Offset> offsets = runPromise(() -> store().appendBatch(TENANT_A, entries)); // GH-90000

            assertThat(offsets).hasSize(3); // GH-90000
        }

        @Test
        @DisplayName("all batch offsets are monotonically increasing [GH-90000]")
        void appendBatch_monotonicallyIncreasing() { // GH-90000
            List<EventEntry> entries = List.of(entry("e.a [GH-90000]"), entry("e.b [GH-90000]"), entry("e.c [GH-90000]"));

            List<Offset> offsets = runPromise(() -> store().appendBatch(TENANT_A, entries)); // GH-90000

            for (int i = 1; i < offsets.size(); i++) { // GH-90000
                long prev = Long.parseLong(offsets.get(i - 1).value()); // GH-90000
                long curr = Long.parseLong(offsets.get(i).value()); // GH-90000
                assertThat(curr).isGreaterThan(prev); // GH-90000
            }
        }
    }

    // =========================================================================
    //  Read
    // =========================================================================

    @Nested
    @DisplayName("read() [GH-90000]")
    class Read {

        @Test
        @DisplayName("reads events from offset 0 [GH-90000]")
        void read_fromZero_returnsAll() { // GH-90000
            runPromise(() -> store().append(TENANT_A, entry("e.one [GH-90000]")));
            runPromise(() -> store().append(TENANT_A, entry("e.two [GH-90000]")));

            List<EventEntry> events = runPromise(() -> store().read(TENANT_A, Offset.of(0), 10)); // GH-90000

            assertThat(events).hasSize(2); // GH-90000
        }

        @Test
        @DisplayName("limit is respected [GH-90000]")
        void read_limit_respected() { // GH-90000
            for (int i = 0; i < 5; i++) { // GH-90000
                runPromise(() -> store().append(TENANT_A, entry("e.bulk [GH-90000]")));
            }

            List<EventEntry> events = runPromise(() -> store().read(TENANT_A, Offset.of(0), 3)); // GH-90000

            assertThat(events).hasSize(3); // GH-90000
        }

        @Test
        @DisplayName("returns empty list when no events [GH-90000]")
        void read_empty_returnsEmpty() { // GH-90000
            List<EventEntry> events = runPromise(() -> store().read(TENANT_A, Offset.of(0), 10)); // GH-90000

            assertThat(events).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("paginates using from-offset [GH-90000]")
        void read_paginates() { // GH-90000
            Offset o1 = runPromise(() -> store().append(TENANT_A, entry("e.one [GH-90000]")));
            Offset o2 = runPromise(() -> store().append(TENANT_A, entry("e.two [GH-90000]")));
            Offset o3 = runPromise(() -> store().append(TENANT_A, entry("e.three [GH-90000]")));

            // Read only from o2 onward
            List<EventEntry> page = runPromise(() -> // GH-90000
                    store().read(TENANT_A, o2, 10)); // GH-90000

            // Should include e.two and e.three
            assertThat(page).hasSizeGreaterThanOrEqualTo(2); // GH-90000
            assertThat(page.get(0).eventType()).isEqualTo("e.two [GH-90000]");
        }
    }

    // =========================================================================
    //  ReadByType
    // =========================================================================

    @Nested
    @DisplayName("readByType() [GH-90000]")
    class ReadByType {

        @Test
        @DisplayName("filters by event type [GH-90000]")
        void readByType_filtersCorrectly() { // GH-90000
            runPromise(() -> store().append(TENANT_A, entry("order.placed [GH-90000]")));
            runPromise(() -> store().append(TENANT_A, entry("user.created [GH-90000]")));
            runPromise(() -> store().append(TENANT_A, entry("order.placed [GH-90000]")));

            List<EventEntry> orders = runPromise(() -> // GH-90000
                    store().readByType(TENANT_A, "order.placed", Offset.of(0), 10)); // GH-90000

            assertThat(orders).hasSize(2); // GH-90000
            assertThat(orders).allMatch(e -> e.eventType().equals("order.placed [GH-90000]"));
        }

        @Test
        @DisplayName("returns empty for unknown type [GH-90000]")
        void readByType_unknownType_returnsEmpty() { // GH-90000
            runPromise(() -> store().append(TENANT_A, entry("order.placed [GH-90000]")));

            List<EventEntry> result = runPromise(() -> // GH-90000
                    store().readByType(TENANT_A, "no.such.type", Offset.of(0), 10)); // GH-90000

            assertThat(result).isEmpty(); // GH-90000
        }
    }

    // =========================================================================
    //  ReadByTimeRange
    // =========================================================================

    @Nested
    @DisplayName("readByTimeRange() [GH-90000]")
    class ReadByTimeRange {

        @Test
        @DisplayName("filters events within time window [GH-90000]")
        void readByTimeRange_returnsEventsInRange() { // GH-90000
            Instant before = Instant.now().minus(1, ChronoUnit.SECONDS); // GH-90000
            runPromise(() -> store().append(TENANT_A, entry("window.event [GH-90000]")));
            Instant after = Instant.now().plus(1, ChronoUnit.SECONDS); // GH-90000

            List<EventEntry> result = runPromise(() -> // GH-90000
                    store().readByTimeRange(TENANT_A, before, after, 10)); // GH-90000

            assertThat(result).hasSize(1); // GH-90000
            assertThat(result.get(0).eventType()).isEqualTo("window.event [GH-90000]");
        }

        @Test
        @DisplayName("returns empty when window has no events [GH-90000]")
        void readByTimeRange_emptyWindow_returnsEmpty() { // GH-90000
            runPromise(() -> store().append(TENANT_A, entry("old.event [GH-90000]")));

            Instant futureStart = Instant.now().plus(1, ChronoUnit.HOURS); // GH-90000
            Instant futureEnd   = futureStart.plus(1, ChronoUnit.HOURS); // GH-90000

            List<EventEntry> result = runPromise(() -> // GH-90000
                    store().readByTimeRange(TENANT_A, futureStart, futureEnd, 10)); // GH-90000

            assertThat(result).isEmpty(); // GH-90000
        }
    }

    // =========================================================================
    //  Offset Management
    // =========================================================================

    @Nested
    @DisplayName("getLatestOffset() / getEarliestOffset() [GH-90000]")
    class OffsetManagement {

        @Test
        @DisplayName("latestOffset returns Offset.zero() for empty store [GH-90000]")
        void latestOffset_empty_returnsZero() { // GH-90000
            Offset latest = runPromise(() -> store().getLatestOffset(TENANT_A)); // GH-90000
            assertThat(latest).isEqualTo(Offset.zero()); // GH-90000
        }

        @Test
        @DisplayName("earliestOffset returns Offset.zero() for empty store [GH-90000]")
        void earliestOffset_empty_returnsZero() { // GH-90000
            Offset earliest = runPromise(() -> store().getEarliestOffset(TENANT_A)); // GH-90000
            assertThat(earliest).isEqualTo(Offset.zero()); // GH-90000
        }

        @Test
        @DisplayName("latestOffset increases after each append [GH-90000]")
        void latestOffset_increasesWithAppends() { // GH-90000
            Offset o1 = runPromise(() -> store().append(TENANT_A, entry("e.one [GH-90000]")));
            Offset latest = runPromise(() -> store().getLatestOffset(TENANT_A)); // GH-90000

            assertThat(latest.value()).isEqualTo(o1.value()); // GH-90000

            Offset o2 = runPromise(() -> store().append(TENANT_A, entry("e.two [GH-90000]")));
            latest = runPromise(() -> store().getLatestOffset(TENANT_A)); // GH-90000

            assertThat(latest.value()).isEqualTo(o2.value()); // GH-90000
        }

        @Test
        @DisplayName("earliestOffset stays at first appended offset [GH-90000]")
        void earliestOffset_staysAtFirst() { // GH-90000
            Offset first = runPromise(() -> store().append(TENANT_A, entry("e.first [GH-90000]")));
            runPromise(() -> store().append(TENANT_A, entry("e.second [GH-90000]")));
            runPromise(() -> store().append(TENANT_A, entry("e.third [GH-90000]")));

            Offset earliest = runPromise(() -> store().getEarliestOffset(TENANT_A)); // GH-90000

            assertThat(earliest.value()).isEqualTo(first.value()); // GH-90000
        }
    }

    // =========================================================================
    //  Multi-tenant isolation
    // =========================================================================

    @Nested
    @DisplayName("multi-tenant isolation [GH-90000]")
    class MultiTenantIsolation {

        @Test
        @DisplayName("tenants cannot read each other's events [GH-90000]")
        void tenantIsolation_readDoesNotLeakAcrossTenants() { // GH-90000
            runPromise(() -> store().append(TENANT_A, entry("tenant-a.event [GH-90000]")));
            runPromise(() -> store().append(TENANT_B, entry("tenant-b.event [GH-90000]")));

            List<EventEntry> aEvents = runPromise(() -> store().read(TENANT_A, Offset.of(0), 10)); // GH-90000
            List<EventEntry> bEvents = runPromise(() -> store().read(TENANT_B, Offset.of(0), 10)); // GH-90000

            assertThat(aEvents).hasSize(1); // GH-90000
            assertThat(aEvents.get(0).eventType()).isEqualTo("tenant-a.event [GH-90000]");

            assertThat(bEvents).hasSize(1); // GH-90000
            assertThat(bEvents.get(0).eventType()).isEqualTo("tenant-b.event [GH-90000]");
        }

        @Test
        @DisplayName("latestOffset is scoped per tenant [GH-90000]")
        void tenantIsolation_latestOffset_scopedPerTenant() { // GH-90000
            // Append 1 event for B first, then 3 for A — A's events therefore
            // have higher global offsets, proving getLatestOffset is per-tenant scoped.
            runPromise(() -> store().append(TENANT_B, entry("b.event [GH-90000]")));
            for (int i = 0; i < 3; i++) { // GH-90000
                runPromise(() -> store().append(TENANT_A, entry("a.event [GH-90000]")));
            }

            Offset aLatest = runPromise(() -> store().getLatestOffset(TENANT_A)); // GH-90000
            Offset bLatest = runPromise(() -> store().getLatestOffset(TENANT_B)); // GH-90000

            assertThat(Long.parseLong(aLatest.value())) // GH-90000
                    .isGreaterThan(Long.parseLong(bLatest.value())); // GH-90000
        }
    }

    // =========================================================================
    //  Tail (polling subscription) // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("tail() [GH-90000]")
    class Tail {

        @Test
        @DisplayName("delivers newly appended events to handler [GH-90000]")
        void tail_deliversNewEvents() throws InterruptedException { // GH-90000
            CopyOnWriteArrayList<EventEntry> received = new CopyOnWriteArrayList<>(); // GH-90000

            Subscription sub = runPromise(() -> // GH-90000
                    store().tail(TENANT_A, Offset.of(0), received::add)); // GH-90000

            // Append an event after subscription starts
            runPromise(() -> store().append(TENANT_A, entry("tail.event [GH-90000]")));

            // Wait up to 2 seconds for the poll to fire
            long deadline = System.currentTimeMillis() + 2_000; // GH-90000
            while (received.isEmpty() && System.currentTimeMillis() < deadline) { // GH-90000
                Thread.sleep(100); // GH-90000
            }

            sub.cancel(); // GH-90000
            assertThat(sub.isCancelled()).isTrue(); // GH-90000
            assertThat(received).isNotEmpty(); // GH-90000
            assertThat(received.get(0).eventType()).isEqualTo("tail.event [GH-90000]");
        }
    }
}
