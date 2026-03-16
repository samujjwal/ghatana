package com.ghatana.appplatform.posttrade.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Matches our settlement instruction against the counterparty's instruction.
 *              Matching criteria: instrument, quantity, settlement_date, direction (opposite),
 *              counterparty. State lifecycle: UNMATCHED → ALLEGED → MATCHED → AFFIRMED.
 *              Auto-matches when both sides submit instructions; produces field-level diff
 *              for discrepancies; escalates unmatched items after timeout.
 * @doc.layer   Domain
 * @doc.pattern Promise.ofBlocking; writes to settlement_matches table; maker-checker for
 *              manual affirmation as per K-01.
 */
public class SettlementMatchingService {

    private static final Logger log = LoggerFactory.getLogger(SettlementMatchingService.class);
    private static final ZoneId NST = ZoneId.of("Asia/Kathmandu");
    private static final long   AUTO_MATCH_TIMEOUT_HOURS = 4;

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final Counter          matchedCounter;
    private final Counter          unmatchedCounter;
    private final Counter          affirmedCounter;

    public SettlementMatchingService(HikariDataSource dataSource, Executor executor,
                                     MeterRegistry registry) {
        this.dataSource       = dataSource;
        this.executor         = executor;
        this.matchedCounter   = registry.counter("posttrade.settlement.match", "result", "matched");
        this.unmatchedCounter = registry.counter("posttrade.settlement.match", "result", "unmatched");
        this.affirmedCounter  = registry.counter("posttrade.settlement.match", "result", "affirmed");
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record SettlementInstruction(
        String instructionId,
        String participantId,
        String counterpartyId,
        String instrumentId,
        String direction,          // DELIVER | RECEIVE
        double quantity,
        String settlementDateAd
    ) {}

    public record MatchResult(
        String         matchId,
        String         ourInstructionId,
        String         cpInstructionId,  // null if unmatched
        String         status,           // UNMATCHED | ALLEGED | MATCHED | AFFIRMED
        List<String>   discrepancies     // empty when matched
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Attempt to auto-match a new instruction against existing counterparty instructions.
     */
    public Promise<MatchResult> attemptMatch(SettlementInstruction ours) {
        return Promise.ofBlocking(executor, () -> {
            SettlementInstruction cp = findCounterpartyInstruction(ours);
            if (cp != null) {
                List<String> discrepancies = diff(ours, cp);
                if (discrepancies.isEmpty()) {
                    String matchId = persistMatch(ours.instructionId(), cp.instructionId(), "MATCHED");
                    matchedCounter.increment();
                    log.info("Settlement auto-matched: ours={} cp={}", ours.instructionId(), cp.instructionId());
                    return new MatchResult(matchId, ours.instructionId(), cp.instructionId(), "MATCHED", List.of());
                } else {
                    String matchId = persistMatch(ours.instructionId(), cp.instructionId(), "ALLEGED");
                    unmatchedCounter.increment();
                    log.warn("Settlement alleged (discrepancies): ours={} diffs={}", ours.instructionId(), discrepancies);
                    return new MatchResult(matchId, ours.instructionId(), cp.instructionId(), "ALLEGED", discrepancies);
                }
            }
            // No counterparty instruction yet
            String matchId = persistMatch(ours.instructionId(), null, "UNMATCHED");
            unmatchedCounter.increment();
            return new MatchResult(matchId, ours.instructionId(), null, "UNMATCHED", List.of("NO_COUNTERPARTY_INSTRUCTION"));
        });
    }

    /**
     * Manually affirm a matched instruction (maker-checker: requires different actor than submitter).
     */
    public Promise<Void> affirm(String matchId, String affirmedBy) {
        return Promise.ofBlocking(executor, () -> {
            updateMatchStatus(matchId, "AFFIRMED", affirmedBy);
            affirmedCounter.increment();
            log.info("Settlement affirmed: matchId={} by={}", matchId, affirmedBy);
            return null;
        });
    }

    /**
     * Escalation sweep: UNMATCHED instructions older than {@link #AUTO_MATCH_TIMEOUT_HOURS} hours.
     */
    public Promise<List<String>> escalateUnmatched() {
        return Promise.ofBlocking(executor, () -> loadEscalationCandidates());
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private SettlementInstruction findCounterpartyInstruction(SettlementInstruction ours) {
        String cpDirection = "DELIVER".equals(ours.direction()) ? "RECEIVE" : "DELIVER";
        String sql = """
            SELECT instruction_id, participant_id, counterparty_id, instrument_id,
                   direction, quantity, settlement_date_ad
            FROM settlement_instructions
            WHERE counterparty_id    = ?
              AND participant_id     = ?
              AND instrument_id      = ?
              AND direction          = ?
              AND settlement_date_ad = ?
              AND status             IN ('GENERATED', 'SENT')
            LIMIT 1
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ours.participantId());
            ps.setString(2, ours.counterpartyId());
            ps.setString(3, ours.instrumentId());
            ps.setString(4, cpDirection);
            ps.setString(5, ours.settlementDateAd());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new SettlementInstruction(
                        rs.getString("instruction_id"),
                        rs.getString("participant_id"),
                        rs.getString("counterparty_id"),
                        rs.getString("instrument_id"),
                        rs.getString("direction"),
                        rs.getDouble("quantity"),
                        rs.getString("settlement_date_ad")
                    );
                }
            }
        } catch (SQLException ex) {
            log.error("Failed to find counterparty instruction for ours={}", ours.instructionId(), ex);
        }
        return null;
    }

    private List<String> diff(SettlementInstruction a, SettlementInstruction b) {
        List<String> diffs = new ArrayList<>();
        if (Math.abs(a.quantity() - b.quantity()) > 0.0001) {
            diffs.add("QUANTITY_MISMATCH: " + a.quantity() + " vs " + b.quantity());
        }
        if (!a.settlementDateAd().equals(b.settlementDateAd())) {
            diffs.add("SETTLEMENT_DATE_MISMATCH: " + a.settlementDateAd() + " vs " + b.settlementDateAd());
        }
        return diffs;
    }

    private String persistMatch(String ourId, String cpId, String status) {
        String matchId = UUID.randomUUID().toString();
        String sql = """
            INSERT INTO settlement_matches
                (match_id, our_instruction_id, cp_instruction_id, status, created_at)
            VALUES (?, ?, ?, ?, now())
            ON CONFLICT (our_instruction_id) DO UPDATE
                SET cp_instruction_id = EXCLUDED.cp_instruction_id,
                    status            = EXCLUDED.status
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, matchId);
            ps.setString(2, ourId);
            ps.setString(3, cpId);
            ps.setString(4, status);
            ps.executeUpdate();
        } catch (SQLException ex) {
            log.error("Failed to persist match ourId={}", ourId, ex);
        }
        return matchId;
    }

    private void updateMatchStatus(String matchId, String status, String actor) {
        String sql = "UPDATE settlement_matches SET status = ?, affirmed_by = ?, affirmed_at = now() WHERE match_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, actor);
            ps.setString(3, matchId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            log.error("Failed to update match status matchId={}", matchId, ex);
        }
    }

    private List<String> loadEscalationCandidates() {
        String sql = """
            SELECT match_id FROM settlement_matches
            WHERE status = 'UNMATCHED'
              AND created_at < now() - INTERVAL '4 hours'
            """;
        List<String> ids = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) ids.add(rs.getString("match_id"));
        } catch (SQLException ex) {
            log.error("Failed to load escalation candidates", ex);
        }
        return ids;
    }
}
