package com.ghatana.appplatform.pms.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Paginated portfolio transaction history: trades, corporate actions, and cash flows.
 *              Uses keyset cursor pagination (last seen transaction ID + date) for efficient
 *              large-dataset traversal. Supports filter by date range, instrument, and
 *              transaction type. CSV export via ExportPort. Satisfies STORY-D03-012.
 * @doc.layer   Domain
 * @doc.pattern Keyset cursor pagination; filter composition; CSV export; Counter for exports.
 */
public class PortfolioTransactionHistoryService {

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final ExportPort       exportPort;
    private final Counter          exportCounter;

    public PortfolioTransactionHistoryService(HikariDataSource dataSource, Executor executor,
                                               ExportPort exportPort, MeterRegistry registry) {
        this.dataSource    = dataSource;
        this.executor      = executor;
        this.exportPort    = exportPort;
        this.exportCounter = Counter.builder("pms.txn_history.exports_total").register(registry);
    }

    // ─── Inner port ───────────────────────────────────────────────────────────

    public interface ExportPort {
        byte[] exportCsv(List<TransactionRow> rows);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record TransactionFilter(String portfolioId, LocalDate fromDate, LocalDate toDate,
                                    String instrumentId, String txnType) {}

    public record TransactionRow(String txnId, LocalDate txnDate, String txnType,
                                 String instrumentId, String instrumentName,
                                 BigDecimal qty, BigDecimal price, BigDecimal grossAmount,
                                 BigDecimal fees, BigDecimal netAmount, String currency,
                                 String description) {}

    public record Page(List<TransactionRow> rows, String nextCursorTxnId,
                       LocalDate nextCursorDate, boolean hasMore) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<Page> listTransactions(TransactionFilter filter, String cursorTxnId,
                                          LocalDate cursorDate, int pageSize) {
        return Promise.ofBlocking(executor, () ->
                queryCursorPage(filter, cursorTxnId, cursorDate, pageSize));
    }

    public Promise<byte[]> exportCsv(TransactionFilter filter) {
        return Promise.ofBlocking(executor, () -> {
            List<TransactionRow> all = queryAll(filter);
            exportCounter.increment();
            return exportPort.exportCsv(all);
        });
    }

    // ─── Keyset pagination ────────────────────────────────────────────────────

    private Page queryCursorPage(TransactionFilter f, String cursorTxnId,
                                  LocalDate cursorDate, int pageSize) throws SQLException {
        boolean hasCursor = cursorTxnId != null && cursorDate != null;
        StringBuilder sql = new StringBuilder("""
                SELECT t.txn_id, t.txn_date, t.txn_type, t.instrument_id,
                       r.name AS instrument_name, t.qty, t.price,
                       t.gross_amount, t.fees, t.net_amount, t.currency, t.description
                FROM portfolio_transactions t
                LEFT JOIN reference_data r ON r.instrument_id = t.instrument_id
                WHERE t.portfolio_id = ?
                """);
        applyFilters(sql, f, hasCursor);
        sql.append(" ORDER BY t.txn_date DESC, t.txn_id DESC LIMIT ?");

        List<TransactionRow> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            ps.setString(idx++, f.portfolioId());
            idx = bindFilters(ps, idx, f, hasCursor, cursorDate, cursorTxnId);
            ps.setInt(idx, pageSize + 1); // fetch one extra to detect hasMore
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapRow(rs));
                }
            }
        }

        boolean hasMore = rows.size() > pageSize;
        if (hasMore) rows = rows.subList(0, pageSize);
        String nextId = hasMore ? rows.get(rows.size() - 1).txnId() : null;
        LocalDate nextDate = hasMore ? rows.get(rows.size() - 1).txnDate() : null;
        return new Page(rows, nextId, nextDate, hasMore);
    }

    private List<TransactionRow> queryAll(TransactionFilter f) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT t.txn_id, t.txn_date, t.txn_type, t.instrument_id,
                       r.name AS instrument_name, t.qty, t.price,
                       t.gross_amount, t.fees, t.net_amount, t.currency, t.description
                FROM portfolio_transactions t
                LEFT JOIN reference_data r ON r.instrument_id = t.instrument_id
                WHERE t.portfolio_id = ?
                """);
        applyFilters(sql, f, false);
        sql.append(" ORDER BY t.txn_date DESC, t.txn_id DESC");

        List<TransactionRow> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            ps.setString(idx++, f.portfolioId());
            bindFilters(ps, idx, f, false, null, null);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.add(mapRow(rs));
            }
        }
        return rows;
    }

    private void applyFilters(StringBuilder sql, TransactionFilter f, boolean hasCursor) {
        if (f.fromDate() != null) sql.append(" AND t.txn_date >= ?");
        if (f.toDate()   != null) sql.append(" AND t.txn_date <= ?");
        if (f.instrumentId() != null) sql.append(" AND t.instrument_id = ?");
        if (f.txnType()  != null) sql.append(" AND t.txn_type = ?");
        if (hasCursor)            sql.append(" AND (t.txn_date, t.txn_id) < (?, ?)");
    }

    private int bindFilters(PreparedStatement ps, int idx, TransactionFilter f, boolean hasCursor,
                             LocalDate cursorDate, String cursorTxnId) throws SQLException {
        if (f.fromDate()     != null) ps.setObject(idx++, f.fromDate());
        if (f.toDate()       != null) ps.setObject(idx++, f.toDate());
        if (f.instrumentId() != null) ps.setString(idx++, f.instrumentId());
        if (f.txnType()      != null) ps.setString(idx++, f.txnType());
        if (hasCursor) {
            ps.setObject(idx++, cursorDate);
            ps.setString(idx++, cursorTxnId);
        }
        return idx;
    }

    private TransactionRow mapRow(ResultSet rs) throws SQLException {
        return new TransactionRow(rs.getString("txn_id"),
                rs.getObject("txn_date", LocalDate.class),
                rs.getString("txn_type"), rs.getString("instrument_id"),
                rs.getString("instrument_name"), rs.getBigDecimal("qty"),
                rs.getBigDecimal("price"), rs.getBigDecimal("gross_amount"),
                rs.getBigDecimal("fees"), rs.getBigDecimal("net_amount"),
                rs.getString("currency"), rs.getString("description"));
    }
}
