/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.storage;

import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.spi.EventLogStore.EventEntry;
import com.ghatana.datacloud.spi.EventLogStore.Subscription;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.types.identity.Offset;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link WarmTierEventLogStore} using a real PostgreSQL
 * container (Testcontainers). Verifies the full event log lifecycle: append,
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
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("WarmTierEventLogStore")
class WarmTierEventLogStoreTest extends EventloopTestBase {

    @Container
    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("dc_test")
                    .withUsername("dc_user")
                    .withPassword("dc_pass");

    /** DDL from V005__create_event_log.sql — applied once per class, truncated between tests. */
    private static final String DDL = """
            CREATE EXTENSION IF NOT EXISTS "pgcrypto";
            CREATE TABLE IF NOT EXISTS event_log (
                offset_value     BIGINT          GENERATED ALWAYS AS IDENTITY,
                tenant_id        VARCHAR(255)    NOT NULL,
                event_id         UUID            NOT NULL,
                event_type       VARCHAR(255)    NOT NULL,
                event_version    VARCHAR(64)     NOT NULL DEFAULT '1.0.0',
                payload          BYTEA           NOT NULL,
                content_type     VARCHAR(128)    NOT NULL DEFAULT 'application/json',
                headers          JSONB           NOT NULL DEFAULT '{}',
                idempotency_key  VARCHAR(255),
                created_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
                CONSTRAINT pk_event_log      PRIMARY KEY (offset_value),
                CONSTRAINT uk_event_log_id   UNIQUE (tenant_id, event_id),
                CONSTRAINT uk_event_log_idem UNIQUE (tenant_id, idempotency_key)
            );
            CREATE INDEX IF NOT EXISTS idx_event_log_tenant_offset
                ON event_log (tenant_id, offset_value);
            CREATE INDEX IF NOT EXISTS idx_event_log_type
                ON event_log (tenant_id, event_type, offset_value);
            CREATE INDEX IF NOT EXISTS idx_event_log_created_at
                ON event_log (tenant_id, created_at DESC);
            """;

    private static HikariDataSource dataSource;

    private static final TenantContext TENANT_A = TenantContext.of("tenant-a");
    private static final TenantContext TENANT_B = TenantContext.of("tenant-b");

    @BeforeAll
    static void createSchema() throws Exception {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(POSTGRES.getJdbcUrl());
        cfg.setUsername(POSTGRES.getUsername());
        cfg.setPassword(POSTGRES.getPassword());
        cfg.setMaximumPoolSize(5);
        dataSource = new HikariDataSource(cfg);

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(DDL);
        }
    }

    @AfterAll
    static void tearDownDataSource() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @BeforeEach
    void truncate() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            // RESTART IDENTITY resets the generated-identity sequence so that
            // offset values are predictable across test methods.
            stmt.execute("TRUNCATE TABLE event_log RESTART IDENTITY");
        }
    }

    private WarmTierEventLogStore store() {
        return new WarmTierEventLogStore(dataSource);
    }

    private static EventEntry entry(String eventType) {
        return EventEntry.builder()
                .eventType(eventType)
                .payload("{\"key\":\"value\"}".getBytes())
                .build();
    }

    private static EventEntry entryWithIdempotencyKey(String eventType, String key) {
        return EventEntry.builder()
                .eventType(eventType)
                .payload("{\"key\":\"value\"}".getBytes())
                .idempotencyKey(key)
                .build();
    }

    // =========================================================================
    //  Append
    // =========================================================================

    @Nested
    @DisplayName("append()")
    class Append {

        @Test
        @DisplayName("happy path — returns non-zero offset")
        void append_returnsNonZeroOffset() {
            Offset offset = runPromise(() -> store().append(TENANT_A, entry("user.created")));

            assertThat(offset).isNotNull();
            long offsetVal = Long.parseLong(offset.value());
            assertThat(offsetVal).isGreaterThan(0);
        }

        @Test
        @DisplayName("successive appends return monotonically increasing offsets")
        void append_offsetsIncreaseMonotonically() {
            Offset o1 = runPromise(() -> store().append(TENANT_A, entry("event.a")));
            Offset o2 = runPromise(() -> store().append(TENANT_A, entry("event.b")));
            Offset o3 = runPromise(() -> store().append(TENANT_A, entry("event.c")));

            long v1 = Long.parseLong(o1.value());
            long v2 = Long.parseLong(o2.value());
            long v3 = Long.parseLong(o3.value());

            assertThat(v2).isGreaterThan(v1);
            assertThat(v3).isGreaterThan(v2);
        }

        @Test
        @DisplayName("null entry throws NullPointerException")
        void append_nullEntry_throwsNpe() {
            assertThatNullPointerException()
                    .isThrownBy(() -> runPromise(() -> store().append(TENANT_A, null)));
            clearFatalError();
        }

        @Test
        @DisplayName("duplicate idempotency key throws exception")
        void append_duplicateIdempotencyKey_throws() {
            runPromise(() -> store().append(TENANT_A, entryWithIdempotencyKey("e.x", "idem-1")));

            assertThatThrownBy(() -> runPromise(() ->
                    store().append(TENANT_A, entryWithIdempotencyKey("e.y", "idem-1"))))
                    .isInstanceOf(Exception.class);
            clearFatalError();
        }

        @Test
        @DisplayName("headers are persisted and returned")
        void append_headersRoundTrip() {
            EventEntry withHeaders = EventEntry.builder()
                    .eventType("user.updated")
                    .payload("{\"name\":\"Alice\"}".getBytes())
                    .headers(Map.of("source", "api", "version", "v2"))
                    .build();

            Offset offset = runPromise(() -> store().append(TENANT_A, withHeaders));
            List<EventEntry> read = runPromise(() -> store().read(TENANT_A, Offset.of(0), 10));

            assertThat(read).hasSize(1);
            assertThat(read.get(0).headers()).containsEntry("source", "api").containsEntry("version", "v2");
        }
    }

    // =========================================================================
    //  AppendBatch
    // =========================================================================

    @Nested
    @DisplayName("appendBatch()")
    class AppendBatch {

        @Test
        @DisplayName("returns one offset per entry")
        void appendBatch_returnsCorrectCount() {
            List<EventEntry> entries = List.of(
                    entry("e.one"), entry("e.two"), entry("e.three")
            );

            List<Offset> offsets = runPromise(() -> store().appendBatch(TENANT_A, entries));

            assertThat(offsets).hasSize(3);
        }

        @Test
        @DisplayName("all batch offsets are monotonically increasing")
        void appendBatch_monotonicallyIncreasing() {
            List<EventEntry> entries = List.of(entry("e.a"), entry("e.b"), entry("e.c"));

            List<Offset> offsets = runPromise(() -> store().appendBatch(TENANT_A, entries));

            for (int i = 1; i < offsets.size(); i++) {
                long prev = Long.parseLong(offsets.get(i - 1).value());
                long curr = Long.parseLong(offsets.get(i).value());
                assertThat(curr).isGreaterThan(prev);
            }
        }
    }

    // =========================================================================
    //  Read
    // =========================================================================

    @Nested
    @DisplayName("read()")
    class Read {

        @Test
        @DisplayName("reads events from offset 0")
        void read_fromZero_returnsAll() {
            runPromise(() -> store().append(TENANT_A, entry("e.one")));
            runPromise(() -> store().append(TENANT_A, entry("e.two")));

            List<EventEntry> events = runPromise(() -> store().read(TENANT_A, Offset.of(0), 10));

            assertThat(events).hasSize(2);
        }

        @Test
        @DisplayName("limit is respected")
        void read_limit_respected() {
            for (int i = 0; i < 5; i++) {
                runPromise(() -> store().append(TENANT_A, entry("e.bulk")));
            }

            List<EventEntry> events = runPromise(() -> store().read(TENANT_A, Offset.of(0), 3));

            assertThat(events).hasSize(3);
        }

        @Test
        @DisplayName("returns empty list when no events")
        void read_empty_returnsEmpty() {
            List<EventEntry> events = runPromise(() -> store().read(TENANT_A, Offset.of(0), 10));

            assertThat(events).isEmpty();
        }

        @Test
        @DisplayName("paginates using from-offset")
        void read_paginates() {
            Offset o1 = runPromise(() -> store().append(TENANT_A, entry("e.one")));
            Offset o2 = runPromise(() -> store().append(TENANT_A, entry("e.two")));
            Offset o3 = runPromise(() -> store().append(TENANT_A, entry("e.three")));

            // Read only from o2 onward
            List<EventEntry> page = runPromise(() ->
                    store().read(TENANT_A, o2, 10));

            // Should include e.two and e.three
            assertThat(page).hasSizeGreaterThanOrEqualTo(2);
            assertThat(page.get(0).eventType()).isEqualTo("e.two");
        }
    }

    // =========================================================================
    //  ReadByType
    // =========================================================================

    @Nested
    @DisplayName("readByType()")
    class ReadByType {

        @Test
        @DisplayName("filters by event type")
        void readByType_filtersCorrectly() {
            runPromise(() -> store().append(TENANT_A, entry("order.placed")));
            runPromise(() -> store().append(TENANT_A, entry("user.created")));
            runPromise(() -> store().append(TENANT_A, entry("order.placed")));

            List<EventEntry> orders = runPromise(() ->
                    store().readByType(TENANT_A, "order.placed", Offset.of(0), 10));

            assertThat(orders).hasSize(2);
            assertThat(orders).allMatch(e -> e.eventType().equals("order.placed"));
        }

        @Test
        @DisplayName("returns empty for unknown type")
        void readByType_unknownType_returnsEmpty() {
            runPromise(() -> store().append(TENANT_A, entry("order.placed")));

            List<EventEntry> result = runPromise(() ->
                    store().readByType(TENANT_A, "no.such.type", Offset.of(0), 10));

            assertThat(result).isEmpty();
        }
    }

    // =========================================================================
    //  ReadByTimeRange
    // =========================================================================

    @Nested
    @DisplayName("readByTimeRange()")
    class ReadByTimeRange {

        @Test
        @DisplayName("filters events within time window")
        void readByTimeRange_returnsEventsInRange() {
            Instant before = Instant.now().minus(1, ChronoUnit.SECONDS);
            runPromise(() -> store().append(TENANT_A, entry("window.event")));
            Instant after = Instant.now().plus(1, ChronoUnit.SECONDS);

            List<EventEntry> result = runPromise(() ->
                    store().readByTimeRange(TENANT_A, before, after, 10));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).eventType()).isEqualTo("window.event");
        }

        @Test
        @DisplayName("returns empty when window has no events")
        void readByTimeRange_emptyWindow_returnsEmpty() {
            runPromise(() -> store().append(TENANT_A, entry("old.event")));

            Instant futureStart = Instant.now().plus(1, ChronoUnit.HOURS);
            Instant futureEnd   = futureStart.plus(1, ChronoUnit.HOURS);

            List<EventEntry> result = runPromise(() ->
                    store().readByTimeRange(TENANT_A, futureStart, futureEnd, 10));

            assertThat(result).isEmpty();
        }
    }

    // =========================================================================
    //  Offset Management
    // =========================================================================

    @Nested
    @DisplayName("getLatestOffset() / getEarliestOffset()")
    class OffsetManagement {

        @Test
        @DisplayName("latestOffset returns Offset.zero() for empty store")
        void latestOffset_empty_returnsZero() {
            Offset latest = runPromise(() -> store().getLatestOffset(TENANT_A));
            assertThat(latest).isEqualTo(Offset.zero());
        }

        @Test
        @DisplayName("earliestOffset returns Offset.zero() for empty store")
        void earliestOffset_empty_returnsZero() {
            Offset earliest = runPromise(() -> store().getEarliestOffset(TENANT_A));
            assertThat(earliest).isEqualTo(Offset.zero());
        }

        @Test
        @DisplayName("latestOffset increases after each append")
        void latestOffset_increasesWithAppends() {
            Offset o1 = runPromise(() -> store().append(TENANT_A, entry("e.one")));
            Offset latest = runPromise(() -> store().getLatestOffset(TENANT_A));

            assertThat(latest.value()).isEqualTo(o1.value());

            Offset o2 = runPromise(() -> store().append(TENANT_A, entry("e.two")));
            latest = runPromise(() -> store().getLatestOffset(TENANT_A));

            assertThat(latest.value()).isEqualTo(o2.value());
        }

        @Test
        @DisplayName("earliestOffset stays at first appended offset")
        void earliestOffset_staysAtFirst() {
            Offset first = runPromise(() -> store().append(TENANT_A, entry("e.first")));
            runPromise(() -> store().append(TENANT_A, entry("e.second")));
            runPromise(() -> store().append(TENANT_A, entry("e.third")));

            Offset earliest = runPromise(() -> store().getEarliestOffset(TENANT_A));

            assertThat(earliest.value()).isEqualTo(first.value());
        }
    }

    // =========================================================================
    //  Multi-tenant isolation
    // =========================================================================

    @Nested
    @DisplayName("multi-tenant isolation")
    class MultiTenantIsolation {

        @Test
        @DisplayName("tenants cannot read each other's events")
        void tenantIsolation_readDoesNotLeakAcrossTenants() {
            runPromise(() -> store().append(TENANT_A, entry("tenant-a.event")));
            runPromise(() -> store().append(TENANT_B, entry("tenant-b.event")));

            List<EventEntry> aEvents = runPromise(() -> store().read(TENANT_A, Offset.of(0), 10));
            List<EventEntry> bEvents = runPromise(() -> store().read(TENANT_B, Offset.of(0), 10));

            assertThat(aEvents).hasSize(1);
            assertThat(aEvents.get(0).eventType()).isEqualTo("tenant-a.event");

            assertThat(bEvents).hasSize(1);
            assertThat(bEvents.get(0).eventType()).isEqualTo("tenant-b.event");
        }

        @Test
        @DisplayName("latestOffset is scoped per tenant")
        void tenantIsolation_latestOffset_scopedPerTenant() {
            // Append 1 event for B first, then 3 for A — A's events therefore
            // have higher global offsets, proving getLatestOffset is per-tenant scoped.
            runPromise(() -> store().append(TENANT_B, entry("b.event")));
            for (int i = 0; i < 3; i++) {
                runPromise(() -> store().append(TENANT_A, entry("a.event")));
            }

            Offset aLatest = runPromise(() -> store().getLatestOffset(TENANT_A));
            Offset bLatest = runPromise(() -> store().getLatestOffset(TENANT_B));

            assertThat(Long.parseLong(aLatest.value()))
                    .isGreaterThan(Long.parseLong(bLatest.value()));
        }
    }

    // =========================================================================
    //  Tail (polling subscription)
    // =========================================================================

    @Nested
    @DisplayName("tail()")
    class Tail {

        @Test
        @DisplayName("delivers newly appended events to handler")
        void tail_deliversNewEvents() throws InterruptedException {
            CopyOnWriteArrayList<EventEntry> received = new CopyOnWriteArrayList<>();

            Subscription sub = runPromise(() ->
                    store().tail(TENANT_A, Offset.of(0), received::add));

            // Append an event after subscription starts
            runPromise(() -> store().append(TENANT_A, entry("tail.event")));

            // Wait up to 2 seconds for the poll to fire
            long deadline = System.currentTimeMillis() + 2_000;
            while (received.isEmpty() && System.currentTimeMillis() < deadline) {
                Thread.sleep(100);
            }

            sub.cancel();
            assertThat(sub.isCancelled()).isTrue();
            assertThat(received).isNotEmpty();
            assertThat(received.get(0).eventType()).isEqualTo("tail.event");
        }
    }
}
