package com.ghatana.appplatform.calendar.adapter;

import com.ghatana.appplatform.calendar.domain.BsDate;
import com.ghatana.appplatform.calendar.domain.BsHoliday;
import com.ghatana.appplatform.calendar.domain.HolidayType;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link PostgresHolidayCalendar}.
 *
 * @doc.type class
 * @doc.purpose Integration tests for the PostgreSQL holiday calendar adapter
 * @doc.layer product
 * @doc.pattern Test
 */
@Testcontainers
@DisplayName("PostgresHolidayCalendar — Integration Tests")
class PostgresHolidayCalendarTest extends EventloopTestBase {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("calendar_test")
        .withUsername("test")
        .withPassword("test");

    private static HikariDataSource dataSource;
    private static PostgresHolidayCalendar store;

    @BeforeAll
    static void setUp() {
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

        store = new PostgresHolidayCalendar(dataSource, Executors.newFixedThreadPool(4));
    }

    @AfterAll
    static void tearDown() {
        if (dataSource != null) dataSource.close();
    }

    @BeforeEach
    void clean() {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM calendar_holidays");
        } catch (Exception e) {
            throw new RuntimeException("Failed to clean calendar_holidays", e);
        }
    }

    private static BsHoliday shivaratri() {
        return new BsHoliday(
            "h-shivaratri-2082",
            BsDate.of(2082, 11, 24),
            LocalDate.of(2026, 3, 8),
            "Maha Shivaratri",
            HolidayType.PUBLIC,
            "NP",
            false);
    }

    @Test
    @DisplayName("addHoliday persists and getHolidays retrieves it")
    void addAndRetrieve() {
        BsHoliday holiday = shivaratri();
        runPromise(() -> store.addHoliday(holiday));

        List<BsHoliday> result = runPromise(() -> store.getHolidays("NP", 2082));

        assertThat(result).hasSize(1);
        BsHoliday found = result.get(0);
        assertThat(found.id()).isEqualTo("h-shivaratri-2082");
        assertThat(found.name()).isEqualTo("Maha Shivaratri");
        assertThat(found.type()).isEqualTo(HolidayType.PUBLIC);
        assertThat(found.gregorianDate()).isEqualTo(LocalDate.of(2026, 3, 8));
        assertThat(found.date().toString()).isEqualTo("2082-11-24");
    }

    @Test
    @DisplayName("getHolidays returns empty list when none exist for jurisdiction+year")
    void getEmptyForUnknownJurisdiction() {
        List<BsHoliday> result = runPromise(() -> store.getHolidays("NP-BAG", 2082));
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("addHoliday is idempotent (ON CONFLICT DO NOTHING)")
    void addIdempotent() {
        BsHoliday holiday = shivaratri();
        runPromise(() -> store.addHoliday(holiday));
        runPromise(() -> store.addHoliday(holiday)); // second insert should not throw

        List<BsHoliday> result = runPromise(() -> store.getHolidays("NP", 2082));
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("deleteHoliday removes the holiday")
    void deleteHoliday() {
        BsHoliday holiday = shivaratri();
        runPromise(() -> store.addHoliday(holiday));
        runPromise(() -> store.deleteHoliday("h-shivaratri-2082"));

        List<BsHoliday> result = runPromise(() -> store.getHolidays("NP", 2082));
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("deleteHoliday is a no-op for unknown ID")
    void deleteUnknownId() {
        // Should not throw
        runPromise(() -> store.deleteHoliday("nonexistent-id"));
    }

    @Test
    @DisplayName("getHolidays filters by jurisdiction correctly")
    void filtersByJurisdiction() {
        BsHoliday np = shivaratri();
        BsHoliday bag = new BsHoliday(
            "h-bag-only",
            BsDate.of(2082, 11, 24),
            LocalDate.of(2026, 3, 8),
            "Local Festival",
            HolidayType.PUBLIC,
            "NP-BAG",
            false);

        runPromise(() -> store.addHoliday(np));
        runPromise(() -> store.addHoliday(bag));

        List<BsHoliday> npResult  = runPromise(() -> store.getHolidays("NP", 2082));
        List<BsHoliday> bagResult = runPromise(() -> store.getHolidays("NP-BAG", 2082));

        assertThat(npResult).hasSize(1).allMatch(h -> h.jurisdiction().equals("NP"));
        assertThat(bagResult).hasSize(1).allMatch(h -> h.jurisdiction().equals("NP-BAG"));
    }
}
