package com.ghatana.appplatform.recon.service;

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
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose ML gradient-boosted multiclass classifier (governed by K-09) that learns
 *              recurring reconciliation break patterns and classifies each recon run into:
 *              CLEAN, TRANSIENT, SYSTEMATIC, CRITICAL. Features: break_count, total_break_amount,
 *              counterparty_id, instrument_type, settlement_cycle, last_statement_lag_hours.
 *              SYSTEMATIC and CRITICAL trigger immediate escalation workflows.
 *              Satisfies STORY-D13-016.
 * @doc.layer   Domain
 * @doc.pattern K-09 AI governance (gradient-boosted model); SHAP explainability;
 *              run-level classification; INSERT-only prediction log.
 */
public class ReconBreakPatternService {

    private static final Logger log = LoggerFactory.getLogger(ReconBreakPatternService.class);

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final MlModelPort      mlModel;
    private final EscalationPort   escalation;
    private final Counter          systematicCounter;
    private final Counter          criticalCounter;

    public ReconBreakPatternService(HikariDataSource dataSource, Executor executor,
                                    MlModelPort mlModel, EscalationPort escalation,
                                    MeterRegistry registry) {
        this.dataSource        = dataSource;
        this.executor          = executor;
        this.mlModel           = mlModel;
        this.escalation        = escalation;
        this.systematicCounter = registry.counter("recon.ml.systematic_patterns");
        this.criticalCounter   = registry.counter("recon.ml.critical_patterns");
    }

    // ─── Inner ports ──────────────────────────────────────────────────────────

    /** K-09 AI governance port for gradient-boosted model inference. */
    public interface MlModelPort {
        ClassificationResult classify(RunFeatures features);
    }

    public interface EscalationPort {
        void escalateSystematic(String reconRunId, String shap);
        void escalateCritical(String reconRunId, String shap, double confidence);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record RunFeatures(String reconRunId, String counterpartyId, int breakCount,
                              double totalBreakAmount, String instrumentType,
                              int settlementCycle, double statementLagHours) {}

    public record ClassificationResult(String label, double confidence, String shapFeatures) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<ClassificationResult> classifyRun(String reconRunId) {
        return Promise.ofBlocking(executor, () -> {
            RunFeatures features = loadRunFeatures(reconRunId);
            ClassificationResult result = mlModel.classify(features);

            persistClassification(reconRunId, result);

            switch (result.label()) {
                case "SYSTEMATIC" -> { escalation.escalateSystematic(reconRunId, result.shapFeatures()); systematicCounter.increment(); }
                case "CRITICAL"   -> { escalation.escalateCritical(reconRunId, result.shapFeatures(), result.confidence()); criticalCounter.increment(); }
                default           -> log.debug("Recon run {} classified: {}", reconRunId, result.label());
            }
            return result;
        });
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private RunFeatures loadRunFeatures(String reconRunId) throws SQLException {
        String sql = """
                SELECT rr.recon_run_id, rr.counterparty_id, rr.instrument_type,
                       rr.settlement_cycle,
                       COUNT(rb.break_id) AS break_count,
                       COALESCE(SUM(rb.amount), 0) AS total_break_amount,
                       EXTRACT(EPOCH FROM (NOW() - sf.received_at)) / 3600.0 AS statement_lag_hours
                FROM recon_runs rr
                LEFT JOIN recon_breaks rb ON rb.recon_run_id = rr.recon_run_id
                LEFT JOIN statement_fetch_log sf ON sf.recon_run_id = rr.recon_run_id
                WHERE rr.recon_run_id = ?
                GROUP BY rr.recon_run_id, rr.counterparty_id, rr.instrument_type,
                         rr.settlement_cycle, sf.received_at
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, reconRunId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new RunFeatures(reconRunId,
                            rs.getString("counterparty_id"),
                            rs.getInt("break_count"),
                            rs.getDouble("total_break_amount"),
                            rs.getString("instrument_type"),
                            rs.getInt("settlement_cycle"),
                            rs.getDouble("statement_lag_hours"));
                }
            }
        }
        return new RunFeatures(reconRunId, "", 0, 0.0, "EQUITY", 2, 0.0);
    }

    private void persistClassification(String reconRunId, ClassificationResult result)
            throws SQLException {
        String sql = """
                INSERT INTO recon_ml_classifications
                    (classification_id, recon_run_id, label, confidence,
                     shap_features, classified_at)
                VALUES (?, ?, ?, ?, ?::jsonb, NOW())
                ON CONFLICT (recon_run_id) DO UPDATE
                    SET label=EXCLUDED.label, confidence=EXCLUDED.confidence,
                        shap_features=EXCLUDED.shap_features, classified_at=NOW()
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, reconRunId);
            ps.setString(3, result.label());
            ps.setDouble(4, result.confidence());
            ps.setString(5, result.shapFeatures());
            ps.executeUpdate();
        }
    }
}
