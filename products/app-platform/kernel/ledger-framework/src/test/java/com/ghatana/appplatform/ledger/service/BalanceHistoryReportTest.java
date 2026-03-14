package com.ghatana.appplatform.ledger.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link BalanceHistoryReport}.
 *
 * <p>Uses Mockito to stub JDBC without requiring a running database.
 *
 * @doc.type class
 * @doc.purpose Unit tests for balance history time-series report (K16-009)
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BalanceHistoryReport — Unit Tests")
class BalanceHistoryReportTest {

    @Mock DataSource dataSource;
    @Mock Connection connection;
    @Mock PreparedStatement snapshotStmt;
    @Mock PreparedStatement movementStmt;
    @Mock ResultSet snapshotRs;
    @Mock ResultSet movementRs;

    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID TENANT_ID  = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString()))
            .thenReturn(snapshotStmt, movementStmt);
        when(snapshotStmt.executeQuery()).thenReturn(snapshotRs);
        when(movementStmt.executeQuery()).thenReturn(movementRs);
    }

    @Test
    @DisplayName("history_daily: generates one point per day with correct cumulative balances")
    void historyDaily_oneDayWithMovement() throws Exception {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to   = LocalDate.of(2026, 3, 3);

        // No prior snapshot
        when(snapshotRs.next()).thenReturn(false);

        // One movement on day 2: +100 NPR
        Date day2 = Date.valueOf(from.plusDays(1));
        when(movementRs.next()).thenReturn(true, false);
        when(movementRs.getDate("bucket_date")).thenReturn(day2);
        when(movementRs.getString("currency_code")).thenReturn("NPR");
        when(movementRs.getBigDecimal("net_movement")).thenReturn(new BigDecimal("100.00"));

        BalanceHistoryReport report = new BalanceHistoryReport(
            dataSource, BalanceHistoryReport.gregorianOnly());

        List<BalanceHistoryReport.BalanceHistoryPoint> points =
            report.generate(ACCOUNT_ID, TENANT_ID, from, to, BalanceHistoryReport.Interval.DAILY);

        assertThat(points).hasSize(3);  // March 1, 2, 3

        // March 1: no movement, balance zero
        assertThat(points.get(0).date()).isEqualTo(from);
        assertThat(points.get(0).balanceByCurrency()).isEmpty();

        // March 2: +100 NPR
        assertThat(points.get(1).date()).isEqualTo(from.plusDays(1));
        assertThat(points.get(1).balanceByCurrency().get("NPR"))
            .isEqualByComparingTo(new BigDecimal("100.00"));

        // March 3: balance carries forward
        assertThat(points.get(2).date()).isEqualTo(from.plusDays(2));
        assertThat(points.get(2).balanceByCurrency().get("NPR"))
            .isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("history_monthly: generates one point per month")
    void historyMonthly_threeMonths() throws Exception {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to   = LocalDate.of(2026, 3, 31);

        when(snapshotRs.next()).thenReturn(false);
        when(movementRs.next()).thenReturn(false);

        BalanceHistoryReport report = new BalanceHistoryReport(
            dataSource, BalanceHistoryReport.gregorianOnly());

        List<BalanceHistoryReport.BalanceHistoryPoint> points =
            report.generate(ACCOUNT_ID, TENANT_ID, from, to, BalanceHistoryReport.Interval.MONTHLY);

        // Jan, Feb, Mar → 3 monthly buckets
        assertThat(points).hasSize(3);
        assertThat(points.get(0).date()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(points.get(1).date()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(points.get(2).date()).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test
    @DisplayName("history_usesSnapshots: starting balance comes from latest snapshot")
    void historyUsesSnapshot() throws Exception {
        LocalDate from = LocalDate.of(2026, 3, 14);

        when(snapshotRs.next()).thenReturn(true, false);
        when(snapshotRs.getString("currency_code")).thenReturn("NPR");
        when(snapshotRs.getBigDecimal("net_balance")).thenReturn(new BigDecimal("5000.00"));

        // No movements
        when(movementRs.next()).thenReturn(false);

        BalanceHistoryReport report = new BalanceHistoryReport(
            dataSource, BalanceHistoryReport.gregorianOnly());

        List<BalanceHistoryReport.BalanceHistoryPoint> points =
            report.generate(ACCOUNT_ID, TENANT_ID, from, from, BalanceHistoryReport.Interval.DAILY);

        assertThat(points).hasSize(1);
        assertThat(points.get(0).balanceByCurrency().get("NPR"))
            .isEqualByComparingTo(new BigDecimal("5000.00"));
    }

    @Test
    @DisplayName("history_dualCalendar: dateBs is populated from converter function")
    void historyDualCalendar_populatesBsDate() throws Exception {
        LocalDate from = LocalDate.of(2026, 3, 14);

        when(snapshotRs.next()).thenReturn(false);
        when(movementRs.next()).thenReturn(false);

        // Use a custom BS converter
        BalanceHistoryReport report = new BalanceHistoryReport(
            dataSource, date -> "2082-12-01");  // fixed BS equivalent for test

        List<BalanceHistoryReport.BalanceHistoryPoint> points =
            report.generate(ACCOUNT_ID, TENANT_ID, from, from, BalanceHistoryReport.Interval.DAILY);

        assertThat(points).hasSize(1);
        assertThat(points.get(0).dateBs()).isEqualTo("2082-12-01");
    }

    @Test
    @DisplayName("gregorianOnly() converter returns ISO Gregorian date string")
    void gregorianOnly_returnsGregorianDateString() {
        LocalDate date = LocalDate.of(2026, 3, 14);
        String result = BalanceHistoryReport.gregorianOnly().apply(date);
        assertThat(result).isEqualTo("2026-03-14");
    }

    @Test
    @DisplayName("generate() throws IllegalArgumentException when from is after to")
    void generate_throwsWhenFromAfterTo() {
        BalanceHistoryReport report = new BalanceHistoryReport(
            dataSource, BalanceHistoryReport.gregorianOnly());

        assertThatThrownBy(() ->
            report.generate(ACCOUNT_ID, TENANT_ID,
                LocalDate.of(2026, 3, 14), LocalDate.of(2026, 3, 1),
                BalanceHistoryReport.Interval.DAILY))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("from");
    }

    @Test
    @DisplayName("generate() with same day range produces exactly one point")
    void generate_sameDayRange_producesOnePoint() throws Exception {
        LocalDate day = LocalDate.of(2026, 3, 14);

        when(snapshotRs.next()).thenReturn(false);
        when(movementRs.next()).thenReturn(false);

        BalanceHistoryReport report = new BalanceHistoryReport(
            dataSource, BalanceHistoryReport.gregorianOnly());

        List<BalanceHistoryReport.BalanceHistoryPoint> points =
            report.generate(ACCOUNT_ID, TENANT_ID, day, day, BalanceHistoryReport.Interval.DAILY);

        assertThat(points).hasSize(1);
        assertThat(points.get(0).date()).isEqualTo(day);
    }
}
