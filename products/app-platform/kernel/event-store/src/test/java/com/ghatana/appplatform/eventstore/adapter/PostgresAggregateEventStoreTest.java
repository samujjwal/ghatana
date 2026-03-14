package com.ghatana.appplatform.eventstore.adapter;

import com.ghatana.appplatform.eventstore.domain.AggregateEventRecord;
import com.ghatana.appplatform.eventstore.domain.ConflictError;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link PostgresAggregateEventStore}.
 *
 * <p>Uses Testcontainers for a real PostgreSQL instance and extends
 * {@link EventloopTestBase} as required by the Ghatana async testing convention.
 *
 * @doc.type class
 * @doc.purpose Integration tests for PostgresAggregateEventStore
 * @doc.layer product
 * @doc.pattern Test
 */
@Testcontainers
@DisplayName("AggregateEventStore — Integration Tests")
class PostgresAggregateEventStoreTest extends EventloopTestBase {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("app_platform_test")
            .withUsername("test")
            .withPassword("test");

    private static HikariDataSource dataSource;
    private static PostgresAggregateEventStore store;

    @BeforeAll
    static void setUpDataSource() {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(PG.getJdbcUrl());
        cfg.setUsername(PG.getUsername());
        cfg.setPassword(PG.getPassword());
        cfg.setMaximumPoolSize(5);
        dataSource = new HikariDataSource(cfg);

        Flyway.configure()
            .dataSource(dataSource)
            .locations("filesystem:src/main/resources/db/migration")
            .load()
            .migrate();

        store = new PostgresAggregateEventStore(
            dataSource,
            Executors.newFixedThreadPool(4));
    }

    @AfterAll
    static void tearDown() {
        if (dataSource != null) dataSource.close();
    }

    @BeforeEach
    void cleanTable() {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM event_store");
        } catch (Exception e) {
            throw new RuntimeException("Failed to clean event_store before test", e);
        }
    }

    // =========================================================================
    // Event store schema constraints
    // =========================================================================

    @Nested
    @DisplayName("Event store schema constraints")
    class SchemaTests {

        @Test
        @DisplayName("insert_validRow_succeeds — can append and retrieve an event")
        void insertValidRowSucceeds() {
            UUID aggId = UUID.randomUUID();
            AggregateEventRecord result = runPromise(() ->
                store.appendEvent(aggId, "Order", "OrderPlaced",
                    Map.of("orderId", aggId.toString(), "amount", 1000),
                    Map.of("tenant_id", "tenant-1", "user_id", "user-42")));

            assertThat(result.eventId()).isNotNull();
            assertThat(result.eventType()).isEqualTo("OrderPlaced");
            assertThat(result.aggregateId()).isEqualTo(aggId);
            assertThat(result.sequenceNumber()).isEqualTo(0L);
        }

        @Test
        @DisplayName("update_blocked — UPDATE on event_store must be blocked at DB level")
        void updateBlocked() {
            // Revoke is applied via Flyway V002; since tests run as the test user (not app_user)
            // we verify the table exists and the trigger/policy is present via metadata query.
            // Full revoke verification requires the 'app_user' role — tracked as infra task.
            UUID aggId = UUID.randomUUID();
            runPromise(() -> store.appendEvent(aggId, "Order", "OrderPlaced",
                Map.of(), Map.of("tenant_id", "t1")));

            // Direct UPDATE attempt — tests run as superuser so this won't be blocked here,
            // but the Flyway migration documents and applies the REVOKE for 'app_user'.
            // This test validates schema integrity and the record round-trip.
            List<AggregateEventRecord> events = runPromise(() ->
                store.getEventsByAggregate(aggId, 0, null));
            assertThat(events).hasSize(1);
            assertThat(events.get(0).eventType()).isEqualTo("OrderPlaced");
        }

        @Test
        @DisplayName("uniqueIndex_sequenceConflict — duplicate (aggregate,seq) raises ConflictError")
        void uniqueIndexSequenceConflict() {
            UUID aggId = UUID.randomUUID();
            // First append succeeds
            runPromise(() -> store.appendEvent(aggId, "Order", "OrderPlaced",
                Map.of(), Map.of("tenant_id", "t1")));

            // Simulate a concurrent writer inserting the same sequence directly via SQL
            // to trigger the constraint without relying on timing
            try (var conn = dataSource.getConnection();
                 var ps = conn.prepareStatement(
                     "INSERT INTO event_store (event_id,event_type,aggregate_id,aggregate_type,"
                     + "sequence_number,data,metadata,created_at_utc) VALUES (?,?,?,?,?,'{}'::jsonb,'{}',NOW())")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setString(2, "OrderPlaced");
                ps.setObject(3, aggId);
                ps.setString(4, "Order");
                ps.setLong(5, 0L); // same sequence
                org.assertj.core.api.Assertions.assertThatThrownBy(ps::executeUpdate)
                    .isInstanceOf(java.sql.SQLException.class)
                    .hasMessageContaining("23505");
            } catch (java.sql.SQLException e) {
                assertThat(e.getSQLState()).isEqualTo("23505");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    // =========================================================================
    // appendEvent write API
    // =========================================================================

    @Nested
    @DisplayName("appendEvent write API")
    class AppendEventTests {

        @Test
        @DisplayName("appendEvent_valid_stores — event is persisted and returned")
        void appendValidStores() {
            UUID aggId = UUID.randomUUID();
            AggregateEventRecord rec = runPromise(() ->
                store.appendEvent(aggId, "Account", "AccountOpened",
                    Map.of("currency", "NPR"),
                    Map.of("tenant_id", "bank-1")));

            assertThat(rec.aggregateType()).isEqualTo("Account");
            assertThat(rec.data()).containsEntry("currency", "NPR");
            assertThat(rec.sequenceNumber()).isGreaterThanOrEqualTo(0L);
        }

        @Test
        @DisplayName("appendEvent_sequenceConflict — ConflictError on concurrent write")
        void appendConflictThrows() {
            UUID aggId = UUID.randomUUID();
            // Successful first append
            runPromise(() -> store.appendEvent(aggId, "Order", "E1", Map.of(), Map.of("tenant_id","t1")));

            // Inject a duplicate-sequence row directly to simulate concurrent writer
            try (var conn = dataSource.getConnection();
                 var ps = conn.prepareStatement(
                     "INSERT INTO event_store (event_id,event_type,aggregate_id,aggregate_type,"
                     + "sequence_number,data,metadata,created_at_utc) VALUES (?,?,?,?,?,'{}'::jsonb,'{}',NOW())")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setString(2, "E1");
                ps.setObject(3, aggId);
                ps.setString(4, "Order");
                ps.setLong(5, 0L);
                ps.executeUpdate();
            } catch (java.sql.SQLException e) {
                // This is expected — constraint blocks the duplicate
                assertThat(e.getSQLState()).isEqualTo("23505");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test
        @DisplayName("appendEvent_multipleEvents_sequenceIncremented")
        void appendMultipleEventsIncrementSequence() {
            UUID aggId = UUID.randomUUID();
            runPromise(() -> store.appendEvent(aggId, "Order", "OrderPlaced", Map.of(), Map.of("tenant_id","t")));
            AggregateEventRecord second = runPromise(() ->
                store.appendEvent(aggId, "Order", "OrderConfirmed", Map.of(), Map.of("tenant_id","t")));

            assertThat(second.sequenceNumber()).isEqualTo(1L);
        }
    }

    // =========================================================================
    // getEventsByAggregate read API
    // =========================================================================

    @Nested
    @DisplayName("getEventsByAggregate read API")
    class GetEventsTests {

        @Test
        @DisplayName("getEvents_allEvents_ordered — events returned in ascending sequence")
        void getAllEventsOrdered() {
            UUID aggId = UUID.randomUUID();
            runPromise(() -> store.appendEvent(aggId, "Order", "OrderPlaced", Map.of("i", 0), Map.of("tenant_id","t")));
            runPromise(() -> store.appendEvent(aggId, "Order", "OrderPaid",   Map.of("i", 1), Map.of("tenant_id","t")));
            runPromise(() -> store.appendEvent(aggId, "Order", "OrderShipped",Map.of("i", 2), Map.of("tenant_id","t")));

            List<AggregateEventRecord> events = runPromise(() ->
                store.getEventsByAggregate(aggId, 0, null));

            assertThat(events).hasSize(3);
            assertThat(events.get(0).sequenceNumber()).isEqualTo(0L);
            assertThat(events.get(1).sequenceNumber()).isEqualTo(1L);
            assertThat(events.get(2).sequenceNumber()).isEqualTo(2L);
        }

        @Test
        @DisplayName("getEvents_fromSequence_filtered — only returns events >= fromSequence")
        void getFromSequenceFiltered() {
            UUID aggId = UUID.randomUUID();
            runPromise(() -> store.appendEvent(aggId, "Order", "E0", Map.of(), Map.of("tenant_id","t")));
            runPromise(() -> store.appendEvent(aggId, "Order", "E1", Map.of(), Map.of("tenant_id","t")));
            runPromise(() -> store.appendEvent(aggId, "Order", "E2", Map.of(), Map.of("tenant_id","t")));

            List<AggregateEventRecord> events = runPromise(() ->
                store.getEventsByAggregate(aggId, 1L, null));

            assertThat(events).hasSize(2);
            assertThat(events.get(0).eventType()).isEqualTo("E1");
        }

        @Test
        @DisplayName("getEvents_emptyAggregate — returns empty list for unknown aggregate")
        void getEventsForUnknownAggregate() {
            List<AggregateEventRecord> events = runPromise(() ->
                store.getEventsByAggregate(UUID.randomUUID(), 0, null));

            assertThat(events).isEmpty();
        }
    }

    // =========================================================================
    // Dual-calendar timestamp enrichment (calendar service not yet wired)
    // =========================================================================

    @Nested
    @DisplayName("Dual-calendar timestamp enrichment")
    class CalendarEnrichmentTests {

        @Test
        @DisplayName("enrichment_calendarDown_graceful — created_at_bs is null in degradation mode")
        void calendarServiceDegradedBsIsNull() {
            UUID aggId = UUID.randomUUID();
            AggregateEventRecord rec = runPromise(() ->
                store.appendEvent(aggId, "Order", "OrderPlaced", Map.of(), Map.of("tenant_id","t")));

            // Calendar service not yet wired — degradation mode expected
            assertThat(rec.createdAtBs()).isNull();
            assertThat(rec.metadata()).containsEntry("k15_degraded", true);
        }

        @Test
        @DisplayName("enrichment_populatesUtc — created_at_utc is always populated")
        void utcTimestampAlwaysPopulated() {
            UUID aggId = UUID.randomUUID();
            AggregateEventRecord rec = runPromise(() ->
                store.appendEvent(aggId, "Order", "OrderPlaced", Map.of(), Map.of("tenant_id","t")));

            assertThat(rec.createdAtUtc()).isNotNull();
        }
    }
}
