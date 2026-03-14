package com.ghatana.appplatform.ledger.service;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Produces time-series balance history for an account over a date range.
 *
 * <p>Supports two query intervals:
 * <ul>
 *   <li>{@link Interval#DAILY} — one balance point per calendar day</li>
 *   <li>{@link Interval#MONTHLY} — one balance point per month-end</li>
 * </ul>
 *
 * <p>The algorithm uses the most recent {@link com.ghatana.appplatform.ledger.domain.BalanceSnapshot}
 * before {@code from} as a starting point, then replays net journal movements
 * accumulated per interval bucket. If no snapshot exists the starting balance is zero.
 *
 * <p>Dual-calendar support: the report stores Gregorian dates natively and delegates
 * BS (Bikram Sambat) date formatting to a {@link Function}{@code <LocalDate, String>}
 * passed in the constructor. Inject the calendar-service converter for full BS support;
 * use {@link #gregorianOnly()} as a no-op placeholder.
 *
 * <p>All queries are blocking. Wrap with {@code Promise.ofBlocking(executor, ...)}
 * when calling from an ActiveJ eventloop.
 *
 * @doc.type class
 * @doc.purpose Time-series balance history report for ledger accounts (K16-009)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class BalanceHistoryReport {

    private static final Logger LOG = Logger.getLogger(BalanceHistoryReport.class.getName());

    // ── SQL ────────────────────────────────────────────────────────────────────

    /** Find the latest snapshot at or before a given instant. */
    private static final String SQL_LATEST_SNAPSHOT = """
            SELECT currency_code, net_balance
            FROM   ledger_balance_snapshot
            WHERE  account_id = ?
              AND  tenant_id  = ?
              AND  snapshot_at <= ?
            ORDER  BY snapshot_at DESC
            LIMIT  50
            """;

    /** Sum net movements (debit=negative, credit=positive) per currency per day. */
    private static final String SQL_DAILY_MOVEMENTS = """
            SELECT
                DATE(je.created_at_utc AT TIME ZONE 'UTC') AS bucket_date,
                je.currency_code,
                SUM(CASE WHEN je.direction = 'CREDIT' THEN je.amount
                         WHEN je.direction = 'DEBIT'  THEN -je.amount
                         ELSE 0 END)                         AS net_movement
            FROM   ledger_journal_entry je
            JOIN   ledger_journal        j  ON j.journal_id = je.journal_id
            WHERE  je.account_id = ?
              AND  j.tenant_id   = ?
              AND  je.created_at_utc >= ?
              AND  je.created_at_utc <  ?
            GROUP  BY bucket_date, je.currency_code
            ORDER  BY bucket_date
            """;

    /** Sum net movements per currency per month. */
    private static final String SQL_MONTHLY_MOVEMENTS = """
            SELECT
                DATE_TRUNC('month', je.created_at_utc AT TIME ZONE 'UTC') AS bucket_date,
                je.currency_code,
                SUM(CASE WHEN je.direction = 'CREDIT' THEN je.amount
                         WHEN je.direction = 'DEBIT'  THEN -je.amount
                         ELSE 0 END) AS net_movement
            FROM   ledger_journal_entry je
            JOIN   ledger_journal        j  ON j.journal_id = je.journal_id
            WHERE  je.account_id = ?
              AND  j.tenant_id   = ?
              AND  je.created_at_utc >= ?
              AND  je.created_at_utc <  ?
            GROUP  BY bucket_date
            ORDER  BY bucket_date
            """;

    // ── Types ──────────────────────────────────────────────────────────────────

    /** Reporting interval granularity. */
    public enum Interval {
        DAILY,
        MONTHLY
    }

    /**
     * A single balance observation in the history series.
     *
     * @param date              Gregorian calendar date this observation represents
     * @param dateBs            Bikram Sambat formatted date (e.g. "2082-12-01")
     * @param balanceByCurrency map of currency code → net balance at the end of this interval
     */
    public record BalanceHistoryPoint(
        LocalDate date,
        String dateBs,
        Map<String, BigDecimal> balanceByCurrency
    ) {}

    // ── Instance ───────────────────────────────────────────────────────────────

    private final DataSource dataSource;
    private final Function<LocalDate, String> toBs;   // Gregorian → BS date string

    /**
     * @param dataSource JDBC source with access to {@code ledger_journal_entry},
     *                   {@code ledger_journal}, and {@code ledger_balance_snapshot}
     * @param toBs       function converting a Gregorian date to its BS (Bikram Sambat)
     *                   equivalent string; use {@link #gregorianOnly()} for testing
     */
    public BalanceHistoryReport(DataSource dataSource, Function<LocalDate, String> toBs) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.toBs       = Objects.requireNonNull(toBs, "toBs");
    }

    /**
     * No-op BS converter that just returns the Gregorian date string.
     * Use when the calendar-service is unavailable (e.g. unit tests).
     */
    public static Function<LocalDate, String> gregorianOnly() {
        return date -> date.toString();
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Generates the balance history time series for the given account.
     *
     * @param accountId the account to report on
     * @param tenantId  tenant isolation filter
     * @param from      first day of the report range (inclusive, Gregorian)
     * @param to        last day of the report range (inclusive, Gregorian)
     * @param interval  DAILY or MONTHLY
     * @return list of balance history points, ordered chronologically
     * @throws SQLException on database access failure
     */
    public List<BalanceHistoryPoint> generate(
            UUID accountId, UUID tenantId,
            LocalDate from, LocalDate to,
            Interval interval) throws SQLException {

        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(interval, "interval");
        if (from.isAfter(to)) throw new IllegalArgumentException("from must not be after to");

        LOG.info("[BalanceHistoryReport] accountId=" + accountId
            + " from=" + from + " to=" + to + " interval=" + interval);

        // 1. Starting balances (latest snapshot strictly before `from`)
        Map<String, BigDecimal> runningBalance = loadStartingBalance(accountId, tenantId, from);

        // 2. Net movements per bucket
        Map<LocalDate, Map<String, BigDecimal>> movements =
            loadMovements(accountId, tenantId, from, to, interval);

        // 3. Build series by walking through each interval bucket
        List<LocalDate> buckets = buildBuckets(from, to, interval);
        List<BalanceHistoryPoint> result = new ArrayList<>(buckets.size());

        for (LocalDate bucket : buckets) {
            // Apply movements for this bucket (may be empty if no activity)
            Map<String, BigDecimal> bucketMovement = movements.getOrDefault(bucket, Map.of());
            for (Map.Entry<String, BigDecimal> m : bucketMovement.entrySet()) {
                runningBalance.merge(m.getKey(), m.getValue(), BigDecimal::add);
            }
            result.add(new BalanceHistoryPoint(
                bucket,
                toBs.apply(bucket),
                Map.copyOf(runningBalance)   // immutable snapshot of balance at bucket end
            ));
        }

        return result;
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Loads the latest balance snapshot strictly before {@code from}.
     * Returns a mutable map of currency → net balance (zero if no snapshot found).
     */
    private Map<String, BigDecimal> loadStartingBalance(
            UUID accountId, UUID tenantId, LocalDate from) throws SQLException {

        Map<String, BigDecimal> balances = new HashMap<>();
        Instant cutoff = from.atStartOfDay(ZoneOffset.UTC).toInstant();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_LATEST_SNAPSHOT)) {
            ps.setObject(1, accountId);
            ps.setObject(2, tenantId);
            ps.setTimestamp(3, Timestamp.from(cutoff));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String code    = rs.getString("currency_code");
                    BigDecimal bal = rs.getBigDecimal("net_balance");
                    // Use the latest snapshot per currency (query is ordered by snapshot_at DESC)
                    balances.putIfAbsent(code, bal);
                }
            }
        }
        return balances;
    }

    /**
     * Loads net movements per day or month bucket within [from, to].
     */
    private Map<LocalDate, Map<String, BigDecimal>> loadMovements(
            UUID accountId, UUID tenantId,
            LocalDate from, LocalDate to,
            Interval interval) throws SQLException {

        String sql = (interval == Interval.DAILY) ? SQL_DAILY_MOVEMENTS : SQL_MONTHLY_MOVEMENTS;
        Instant start = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end   = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();  // exclusive upper bound

        Map<LocalDate, Map<String, BigDecimal>> result = new LinkedHashMap<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, accountId);
            ps.setObject(2, tenantId);
            ps.setTimestamp(3, Timestamp.from(start));
            ps.setTimestamp(4, Timestamp.from(end));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    // For DAILY: DATE(...); for MONTHLY: DATE_TRUNC returns a timestamp
                    LocalDate bucket = rs.getDate("bucket_date").toLocalDate();
                    // Normalise MONTHLY bucket to first day of the month (DATE_TRUNC returns first day)
                    if (interval == Interval.MONTHLY) {
                        bucket = bucket.withDayOfMonth(1);
                    }
                    String currencyCode = rs.getString("currency_code");
                    BigDecimal movement = rs.getBigDecimal("net_movement");

                    result.computeIfAbsent(bucket, k -> new HashMap<>())
                          .merge(currencyCode, movement, BigDecimal::add);
                }
            }
        }
        return result;
    }

    /**
     * Generates the ordered list of date keys for each interval bucket.
     * DAILY: every day in [from, to].
     * MONTHLY: the last day of each month in the range (for human-friendly reporting).
     */
    private List<LocalDate> buildBuckets(LocalDate from, LocalDate to, Interval interval) {
        List<LocalDate> buckets = new ArrayList<>();
        if (interval == Interval.DAILY) {
            LocalDate cursor = from;
            while (!cursor.isAfter(to)) {
                buckets.add(cursor);
                cursor = cursor.plusDays(1);
            }
        } else {
            // MONTHLY: use the first day of each month as the bucket key (matching DATE_TRUNC)
            YearMonth start = YearMonth.from(from);
            YearMonth end   = YearMonth.from(to);
            YearMonth cursor = start;
            while (!cursor.isAfter(end)) {
                buckets.add(cursor.atDay(1));
                cursor = cursor.plusMonths(1);
            }
        }
        return buckets;
    }
}
