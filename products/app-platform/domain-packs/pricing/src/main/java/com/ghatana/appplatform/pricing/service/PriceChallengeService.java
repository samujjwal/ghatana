package com.ghatana.appplatform.pricing.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
 * @doc.purpose Price challenge workflow: auto-flag MTM prices that deviate >threshold%
 *              from previous close or model price. Challenge reasons: STALE_PRICE, OUTLIER,
 *              MODEL_DEVIATION, MANUAL. Analyst review: accept or override with reason.
 *              Override requires maker-checker (K-07 AuditPort). Re-runs MTM for affected
 *              positions after accepted override. Satisfies STORY-D05-010.
 * @doc.layer   Domain
 * @doc.pattern Maker-checker for overrides; K-07 AuditPort; auto-flagging; re-run MTM;
 *              Counter for auto-flagged/overridden/accepted challenges.
 */
public class PriceChallengeService {

    private static final double DEFAULT_DEVIATION_THRESHOLD = 0.05; // 5%

    private final HikariDataSource   dataSource;
    private final Executor           executor;
    private final ConfigPort         configPort;
    private final AuditPort          auditPort;
    private final MtmBatchEngineService mtmEngine;
    private final Counter            autoFlagCounter;
    private final Counter            overrideCounter;
    private final Counter            acceptedCounter;

    public PriceChallengeService(HikariDataSource dataSource, Executor executor,
                                  ConfigPort configPort, AuditPort auditPort,
                                  MtmBatchEngineService mtmEngine, MeterRegistry registry) {
        this.dataSource      = dataSource;
        this.executor        = executor;
        this.configPort      = configPort;
        this.auditPort       = auditPort;
        this.mtmEngine       = mtmEngine;
        this.autoFlagCounter = Counter.builder("pricing.challenge.auto_flagged_total").register(registry);
        this.overrideCounter = Counter.builder("pricing.challenge.overridden_total").register(registry);
        this.acceptedCounter = Counter.builder("pricing.challenge.accepted_total").register(registry);
    }

    // ─── Inner ports ──────────────────────────────────────────────────────────

    /** K-02 configurable deviation threshold per instrument. */
    public interface ConfigPort {
        double getDeviationThreshold(String instrumentId);
    }

    /** K-07 audit trail. */
    public interface AuditPort {
        void logOverride(String challengeId, String actorId, String action, String reason,
                         BigDecimal oldPrice, BigDecimal newPrice);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public enum ChallengeReason { STALE_PRICE, OUTLIER, MODEL_DEVIATION, MANUAL }
    public enum ChallengeStatus { OPEN, ACCEPTED, OVERRIDDEN }

    public record PriceChallenge(String challengeId, String instrumentId, LocalDate priceDate,
                                 BigDecimal flaggedPrice, BigDecimal previousClose,
                                 double deviationPct, ChallengeReason reason,
                                 ChallengeStatus status, String submittedBy,
                                 LocalDateTime submittedAt) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<List<PriceChallenge>> runAutoFlagging(LocalDate runDate) {
        return Promise.ofBlocking(executor, () -> autoFlag(runDate));
    }

    public Promise<PriceChallenge> manualFlag(String instrumentId, LocalDate priceDate,
                                               String reason, String flaggedBy) {
        return Promise.ofBlocking(executor, () -> {
            BigDecimal flaggedPrice = loadEodPrice(instrumentId, priceDate);
            BigDecimal prevClose    = loadEodPrice(instrumentId, priceDate.minusDays(1));
            double dev = prevClose.compareTo(BigDecimal.ZERO) == 0 ? 0.0
                    : flaggedPrice.subtract(prevClose).abs().divide(prevClose, 6, RoundingMode.HALF_UP).doubleValue();
            return persistChallenge(instrumentId, priceDate, flaggedPrice, prevClose, dev,
                    ChallengeReason.MANUAL, flaggedBy);
        });
    }

    public Promise<Void> accept(String challengeId, String analystId) {
        return Promise.ofBlocking(executor, () -> {
            updateChallengeStatus(challengeId, ChallengeStatus.ACCEPTED, analystId, null, null);
            auditPort.logOverride(challengeId, analystId, "ACCEPT", "Price accepted as-is", null, null);
            acceptedCounter.increment();
            return null;
        });
    }

    public Promise<Void> override(String challengeId, String submitterId,
                                   BigDecimal newPrice, String reason) {
        return Promise.ofBlocking(executor, () -> {
            // Maker-checker: override must be approved separately
            PriceChallenge ch = loadChallenge(challengeId);
            auditPort.logOverride(challengeId, submitterId, "OVERRIDE_SUBMITTED", reason,
                    ch.flaggedPrice(), newPrice);
            persistOverrideRequest(challengeId, newPrice, reason, submitterId);
            return null;
        });
    }

    public Promise<Void> approveOverride(String challengeId, String approverId) {
        return Promise.ofBlocking(executor, () -> {
            OverrideRequest req = loadOverrideRequest(challengeId);
            if (req.submittedBy().equals(approverId)) {
                throw new IllegalStateException("Approver cannot be same as override submitter (maker-checker)");
            }
            applyPriceOverride(challengeId, req.newPrice(), req.instrumentId(), req.priceDate());
            updateChallengeStatus(challengeId, ChallengeStatus.OVERRIDDEN, approverId, req.newPrice(), req.reason());
            auditPort.logOverride(challengeId, approverId, "OVERRIDE_APPROVED", req.reason(),
                    null, req.newPrice());
            // Re-run MTM for affected date
            mtmEngine.runEodMtm(req.priceDate());
            overrideCounter.increment();
            return null;
        });
    }

    // ─── Auto-flagging logic ─────────────────────────────────────────────────

    private List<PriceChallenge> autoFlag(LocalDate runDate) throws SQLException {
        List<PriceChallenge> flagged = new ArrayList<>();
        List<PriceDeviationRow> deviations = loadDeviations(runDate);

        for (PriceDeviationRow row : deviations) {
            double threshold = configPort.getDeviationThreshold(row.instrumentId());
            if (threshold == 0.0) threshold = DEFAULT_DEVIATION_THRESHOLD;
            if (row.deviationPct() > threshold) {
                ChallengeReason reason = row.isStale() ? ChallengeReason.STALE_PRICE : ChallengeReason.OUTLIER;
                PriceChallenge ch = persistChallenge(row.instrumentId(), runDate,
                        row.currentPrice(), row.previousClose(), row.deviationPct(), reason, "SYSTEM");
                flagged.add(ch);
                autoFlagCounter.increment();
            }
        }
        return flagged;
    }

    private record PriceDeviationRow(String instrumentId, BigDecimal currentPrice,
                                     BigDecimal previousClose, double deviationPct, boolean isStale) {}

    private List<PriceDeviationRow> loadDeviations(LocalDate runDate) throws SQLException {
        List<PriceDeviationRow> rows = new ArrayList<>();
        String sql = """
                SELECT curr.instrument_id,
                       curr.price AS current_price,
                       prev.price AS previous_close,
                       ABS(curr.price - prev.price) / NULLIF(prev.price, 0) AS deviation_pct,
                       (curr.source_timestamp < (? - INTERVAL '1 day')) AS is_stale
                FROM instrument_prices_eod curr
                JOIN instrument_prices_eod prev ON prev.instrument_id = curr.instrument_id
                    AND prev.price_date = ? - INTERVAL '1 day'
                WHERE curr.price_date = ?
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, runDate);
            ps.setObject(2, runDate);
            ps.setObject(3, runDate);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new PriceDeviationRow(rs.getString("instrument_id"),
                            rs.getBigDecimal("current_price"), rs.getBigDecimal("previous_close"),
                            rs.getDouble("deviation_pct"), rs.getBoolean("is_stale")));
                }
            }
        }
        return rows;
    }

    private PriceChallenge persistChallenge(String instrumentId, LocalDate priceDate,
                                             BigDecimal flaggedPrice, BigDecimal previousClose,
                                             double deviationPct, ChallengeReason reason,
                                             String submittedBy) throws SQLException {
        String challengeId = UUID.randomUUID().toString();
        String sql = """
                INSERT INTO price_challenges
                    (challenge_id, instrument_id, price_date, flagged_price, previous_close,
                     deviation_pct, reason, status, submitted_by, submitted_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'OPEN', ?, NOW())
                ON CONFLICT (instrument_id, price_date, reason) DO NOTHING
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, challengeId);
            ps.setString(2, instrumentId);
            ps.setObject(3, priceDate);
            ps.setBigDecimal(4, flaggedPrice);
            ps.setBigDecimal(5, previousClose);
            ps.setDouble(6, deviationPct);
            ps.setString(7, reason.name());
            ps.setString(8, submittedBy);
            ps.executeUpdate();
        }
        return new PriceChallenge(challengeId, instrumentId, priceDate, flaggedPrice, previousClose,
                deviationPct, reason, ChallengeStatus.OPEN, submittedBy, LocalDateTime.now());
    }

    private PriceChallenge loadChallenge(String challengeId) throws SQLException {
        String sql = "SELECT * FROM price_challenges WHERE challenge_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, challengeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Challenge not found: " + challengeId);
                return new PriceChallenge(rs.getString("challenge_id"), rs.getString("instrument_id"),
                        rs.getObject("price_date", LocalDate.class), rs.getBigDecimal("flagged_price"),
                        rs.getBigDecimal("previous_close"), rs.getDouble("deviation_pct"),
                        ChallengeReason.valueOf(rs.getString("reason")),
                        ChallengeStatus.valueOf(rs.getString("status")),
                        rs.getString("submitted_by"), rs.getTimestamp("submitted_at").toLocalDateTime());
            }
        }
    }

    private void updateChallengeStatus(String challengeId, ChallengeStatus status,
                                        String actorId, BigDecimal overridePrice, String note)
            throws SQLException {
        String sql = "UPDATE price_challenges SET status = ?, resolved_by = ?, resolved_at = NOW()," +
                " override_price = ?, resolution_note = ? WHERE challenge_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setString(2, actorId);
            ps.setBigDecimal(3, overridePrice);
            ps.setString(4, note);
            ps.setString(5, challengeId);
            ps.executeUpdate();
        }
    }

    private void persistOverrideRequest(String challengeId, BigDecimal newPrice, String reason,
                                         String submittedBy) throws SQLException {
        String sql = """
                INSERT INTO price_override_requests (challenge_id, new_price, reason, submitted_by, submitted_at)
                VALUES (?, ?, ?, ?, NOW()) ON CONFLICT (challenge_id) DO UPDATE
                SET new_price = EXCLUDED.new_price, reason = EXCLUDED.reason
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, challengeId);
            ps.setBigDecimal(2, newPrice);
            ps.setString(3, reason);
            ps.setString(4, submittedBy);
            ps.executeUpdate();
        }
    }

    private record OverrideRequest(String instrumentId, LocalDate priceDate, BigDecimal newPrice,
                                   String reason, String submittedBy) {}

    private OverrideRequest loadOverrideRequest(String challengeId) throws SQLException {
        String sql = """
                SELECT c.instrument_id, c.price_date, o.new_price, o.reason, o.submitted_by
                FROM price_override_requests o
                JOIN price_challenges c ON c.challenge_id = o.challenge_id
                WHERE o.challenge_id = ?
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, challengeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Override request not found: " + challengeId);
                return new OverrideRequest(rs.getString("instrument_id"),
                        rs.getObject("price_date", LocalDate.class), rs.getBigDecimal("new_price"),
                        rs.getString("reason"), rs.getString("submitted_by"));
            }
        }
    }

    private void applyPriceOverride(String challengeId, BigDecimal newPrice,
                                     String instrumentId, LocalDate priceDate) throws SQLException {
        String sql = "UPDATE instrument_prices_eod SET price = ?, is_overridden = TRUE WHERE instrument_id = ? AND price_date = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, newPrice);
            ps.setString(2, instrumentId);
            ps.setObject(3, priceDate);
            ps.executeUpdate();
        }
    }

    private BigDecimal loadEodPrice(String instrumentId, LocalDate priceDate) throws SQLException {
        String sql = "SELECT price FROM instrument_prices_eod WHERE instrument_id = ? AND price_date = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, instrumentId);
            ps.setObject(2, priceDate);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBigDecimal("price") : BigDecimal.ZERO;
            }
        }
    }
}
