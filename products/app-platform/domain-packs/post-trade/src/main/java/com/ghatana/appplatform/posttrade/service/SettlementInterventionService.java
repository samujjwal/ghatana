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
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Intervention workflow triggered by HIGH-risk settlement failure predictions
 *              (STORY-D09-017). Selects action based on top SHAP contributor:
 *              securities_availability → pre-borrow securities from lending desk,
 *              counterparty_fail_rate → early warning to counterparty ops,
 *              liquidity_risk → escalate to settlement manager.
 *              Settlement manager may override/dismiss. All interventions and outcomes
 *              tracked for model feedback. Satisfies STORY-D09-018.
 * @doc.layer   Domain
 * @doc.pattern K-09 advisory; K-07 audit trail; SHAP-driven action selection;
 *              outcome tracking for model retraining.
 */
public class SettlementInterventionService {

    private static final Logger log = LoggerFactory.getLogger(SettlementInterventionService.class);

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final LendingDeskPort  lendingDesk;
    private final NotificationPort notifications;
    private final AuditPort        audit;
    private final Counter          interventionCounter;
    private final Counter          preventedCounter;

    public SettlementInterventionService(HikariDataSource dataSource, Executor executor,
                                         LendingDeskPort lendingDesk,
                                         NotificationPort notifications,
                                         AuditPort audit,
                                         MeterRegistry registry) {
        this.dataSource          = dataSource;
        this.executor            = executor;
        this.lendingDesk         = lendingDesk;
        this.notifications       = notifications;
        this.audit               = audit;
        this.interventionCounter = registry.counter("posttrade.intervention.triggered");
        this.preventedCounter    = registry.counter("posttrade.intervention.prevented");
    }

    // ─── Inner ports ──────────────────────────────────────────────────────────

    public interface LendingDeskPort {
        String requestPreBorrow(String tradeId, String instrumentId, long quantity);
    }

    public interface NotificationPort {
        void sendEarlyWarning(String counterpartyId, String tradeId, String message);
        void escalateToManager(String tradeId, String reason, double riskScore);
    }

    /** K-07 immutable audit trail. */
    public interface AuditPort {
        void record(String entityId, String action, String actor, String detail);
    }

    // ─── Public API ──────────────────────────────────────────────────────────

    /** Triggered by SettlementFailurePredictionService when score ≥ 0.70. */
    public Promise<String> triggerIntervention(String tradeId, double riskScore, String shapFeatures) {
        return Promise.ofBlocking(executor, () -> {
            String topFeature = extractTopShapFeature(shapFeatures);
            String interventionId = UUID.randomUUID().toString();
            String action = selectAction(tradeId, topFeature, riskScore, interventionId);
            persistIntervention(interventionId, tradeId, riskScore, topFeature, action);
            audit.record(tradeId, "INTERVENTION_TRIGGERED", "SYSTEM",
                    "action=" + action + " shap=" + topFeature + " score=" + riskScore);
            interventionCounter.increment();
            return interventionId;
        });
    }

    /** Manager override: dismiss or accept recommended action. */
    public Promise<Void> recordManagerOverride(String interventionId, String managerId,
                                               String decision, String reason) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                    UPDATE settlement_interventions
                    SET manager_decision=?, manager_id=?, override_reason=?, decided_at=NOW()
                    WHERE intervention_id=?
                    """;
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, decision);
                ps.setString(2, managerId);
                ps.setString(3, reason);
                ps.setString(4, interventionId);
                ps.executeUpdate();
            }
            audit.record(interventionId, "MANAGER_OVERRIDE", managerId, "decision=" + decision);
            return null;
        });
    }

    /** Called after settlement outcome is known — feeds back to ML model. */
    public Promise<Void> recordOutcome(String tradeId, boolean settlementSucceeded) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                    UPDATE settlement_interventions
                    SET outcome = ?, outcome_recorded_at = NOW()
                    WHERE trade_id = ? AND outcome IS NULL
                    """;
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, settlementSucceeded ? "PREVENTED" : "FAILED_DESPITE_INTERVENTION");
                ps.setString(2, tradeId);
                int rows = ps.executeUpdate();
                if (rows > 0 && settlementSucceeded) preventedCounter.increment();
            }
            return null;
        });
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private String extractTopShapFeature(String shapJson) {
        // Simple extraction: find field with highest absolute value
        // shapJson format: {"securities_availability":0.35,"counterparty_fail_rate":0.20,...}
        if (shapJson == null || shapJson.isBlank()) return "liquidity_risk";
        String best = "liquidity_risk";
        double bestVal = -1;
        for (String part : shapJson.replaceAll("[{}\"]", "").split(",")) {
            String[] kv = part.split(":");
            if (kv.length == 2) {
                try {
                    double val = Math.abs(Double.parseDouble(kv[1].trim()));
                    if (val > bestVal) {
                        bestVal = val;
                        best = kv[0].trim();
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        return best;
    }

    private String selectAction(String tradeId, String topFeature, double riskScore,
                                String interventionId) {
        return switch (topFeature) {
            case "securities_availability" -> {
                String preborrowId = lendingDesk.requestPreBorrow(tradeId,
                        loadInstrumentId(tradeId), loadQuantity(tradeId));
                log.info("Pre-borrow initiated: tradeId={} preborrowId={}", tradeId, preborrowId);
                yield "PRE_BORROW";
            }
            case "counterparty_fail_rate" -> {
                notifications.sendEarlyWarning(loadCounterpartyId(tradeId), tradeId,
                        "High settlement failure risk detected. Please confirm securities availability.");
                yield "EARLY_WARNING";
            }
            default -> {
                notifications.escalateToManager(tradeId, "Risk score=" + riskScore, riskScore);
                yield "MANAGER_ESCALATION";
            }
        };
    }

    private void persistIntervention(String interventionId, String tradeId, double riskScore,
                                     String topFeature, String action) throws SQLException {
        String sql = """
                INSERT INTO settlement_interventions
                    (intervention_id, trade_id, risk_score, top_shap_feature, action, triggered_at)
                VALUES (?, ?, ?, ?, ?, NOW())
                ON CONFLICT (trade_id) DO UPDATE
                    SET risk_score=EXCLUDED.risk_score, action=EXCLUDED.action,
                        triggered_at=NOW()
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, interventionId);
            ps.setString(2, tradeId);
            ps.setDouble(3, riskScore);
            ps.setString(4, topFeature);
            ps.setString(5, action);
            ps.executeUpdate();
        }
    }

    private String loadInstrumentId(String tradeId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT instrument_id FROM trades WHERE trade_id=?")) {
            ps.setString(1, tradeId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : "";
            }
        } catch (SQLException e) { return ""; }
    }

    private long loadQuantity(String tradeId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT quantity FROM trades WHERE trade_id=?")) {
            ps.setString(1, tradeId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) { return 0L; }
    }

    private String loadCounterpartyId(String tradeId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT counterparty_id FROM trades WHERE trade_id=?")) {
            ps.setString(1, tradeId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : "";
            }
        } catch (SQLException e) { return ""; }
    }
}
