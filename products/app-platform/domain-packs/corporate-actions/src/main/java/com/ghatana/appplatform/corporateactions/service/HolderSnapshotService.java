package com.ghatana.appplatform.corporateactions.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Captures immutable holder position snapshots as-of the CA record date.
 *              Positions are read from the post-trade position table (D-09). Holdings with
 *              trade_date >= ex_date are excluded (ex-date rule). Snapshot is frozen
 *              and immutable after capture — no further writes to the snapshot rows.
 *              Satisfies STORY-D12-002.
 * @doc.layer   Domain
 * @doc.pattern Position snapshot; record-date immutable capture; ex-date exclusion; Counter.
 */
public class HolderSnapshotService {

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final Counter          snapshotCapturedCounter;
    private final Counter          holdersSnapshotedCounter;

    public HolderSnapshotService(HikariDataSource dataSource, Executor executor,
                                  MeterRegistry registry) {
        this.dataSource               = dataSource;
        this.executor                 = executor;
        this.snapshotCapturedCounter  = Counter.builder("ca.snapshot.captured_total").register(registry);
        this.holdersSnapshotedCounter = Counter.builder("ca.snapshot.holders_total").register(registry);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record HolderPosition(String snapshotId, String caId, String clientId,
                                  String instrumentId, double quantity, LocalDate recordDate,
                                  LocalDateTime capturedAt) {}

    public record SnapshotSummary(String caId, LocalDate recordDate, long holderCount,
                                   double totalQuantity) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<SnapshotSummary> captureSnapshot(String caId, String issuerId,
                                                     LocalDate exDate, LocalDate recordDate) {
        return Promise.ofBlocking(executor, () -> {
            // Verify snapshot not already captured
            if (snapshotExists(caId)) {
                throw new IllegalStateException("Snapshot already captured for CA: " + caId);
            }

            // Insert snapshot from position table, excluding positions opened on/after ex_date
            int rows = insertSnapshot(caId, issuerId, exDate, recordDate);
            snapshotCapturedCounter.increment();
            holdersSnapshotedCounter.increment(rows);

            long holderCount = countHolders(caId);
            double totalQty  = sumQuantity(caId);
            return new SnapshotSummary(caId, recordDate, holderCount, totalQty);
        });
    }

    public Promise<List<HolderPosition>> getSnapshot(String caId) {
        return Promise.ofBlocking(executor, () -> loadSnapshot(caId));
    }

    // ─── Persistence ─────────────────────────────────────────────────────────

    private boolean snapshotExists(String caId) throws SQLException {
        String sql = "SELECT 1 FROM ca_holder_snapshots WHERE ca_id=? LIMIT 1";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, caId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private int insertSnapshot(String caId, String issuerId, LocalDate exDate,
                                LocalDate recordDate) throws SQLException {
        // Positions settled on/before record_date, excluding trades on/after ex_date
        String sql = """
                INSERT INTO ca_holder_snapshots
                    (snapshot_id, ca_id, client_id, instrument_id, quantity, record_date, captured_at)
                SELECT gen_random_uuid()::text, ?, p.client_id, p.instrument_id,
                       p.net_quantity, ?, NOW()
                FROM positions p
                WHERE p.instrument_id = ?
                  AND p.settlement_date <= ?
                  AND NOT EXISTS (
                      SELECT 1 FROM trades t
                      WHERE t.client_id = p.client_id AND t.instrument_id = p.instrument_id
                        AND t.trade_date >= ?
                  )
                  AND p.net_quantity > 0
                ON CONFLICT (ca_id, client_id, instrument_id) DO NOTHING
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, caId);
            ps.setObject(2, recordDate);
            ps.setString(3, issuerId);
            ps.setObject(4, recordDate);
            ps.setObject(5, exDate);
            return ps.executeUpdate();
        }
    }

    private List<HolderPosition> loadSnapshot(String caId) throws SQLException {
        String sql = """
                SELECT snapshot_id, ca_id, client_id, instrument_id, quantity, record_date, captured_at
                FROM ca_holder_snapshots WHERE ca_id=?
                """;
        List<HolderPosition> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, caId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new HolderPosition(rs.getString("snapshot_id"), rs.getString("ca_id"),
                            rs.getString("client_id"), rs.getString("instrument_id"),
                            rs.getDouble("quantity"), rs.getObject("record_date", LocalDate.class),
                            rs.getObject("captured_at", LocalDateTime.class)));
                }
            }
        }
        return result;
    }

    private long countHolders(String caId) throws SQLException {
        String sql = "SELECT COUNT(DISTINCT client_id) FROM ca_holder_snapshots WHERE ca_id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, caId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    private double sumQuantity(String caId) throws SQLException {
        String sql = "SELECT COALESCE(SUM(quantity), 0) FROM ca_holder_snapshots WHERE ca_id=?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, caId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0.0;
            }
        }
    }
}
