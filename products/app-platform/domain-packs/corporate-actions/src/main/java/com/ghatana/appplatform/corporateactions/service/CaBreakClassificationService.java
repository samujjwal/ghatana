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
 * @doc.purpose K-09 autonomous tier gradient-boosted ML model that classifies CA reconciliation
 *              breaks into 7 root-cause labels: DATA_FEED_LATENCY, ENTITLEMENT_CALCULATION_ERROR,
 *              TAX_MISMATCH, ELECTION_MISMATCH, CURRENCY_ROUNDING, SYSTEMS_BUG, GENUINE_BREAK.
 *              Routes auto-remediation per label: re-calc, re-process, or escalate. Provides SHAP
 *              top feature explanation. Satisfies STORY-D12-013.
 * @doc.layer   Domain
 * @doc.pattern K-09 gradient-boost model inference; ModelInferencePort; SHAP explainability;
 *              auto-remediation routing; ON CONFLICT DO UPDATE; Counter.
 */
public class CaBreakClassificationService {

    private static final int TOP_SHAP_FEATURES = 3;

    private final HikariDataSource    dataSource;
    private final Executor            executor;
    private final ModelInferencePort  modelPort;
    private final RemediationPort     remediationPort;
    private final Counter             classifiedCounter;
    private final Counter             autoRemediatedCounter;

    public CaBreakClassificationService(HikariDataSource dataSource, Executor executor,
                                         ModelInferencePort modelPort,
                                         RemediationPort remediationPort,
                                         MeterRegistry registry) {
        this.dataSource           = dataSource;
        this.executor             = executor;
        this.modelPort            = modelPort;
        this.remediationPort      = remediationPort;
        this.classifiedCounter    = Counter.builder("ca.break.classified_total").register(registry);
        this.autoRemediatedCounter = Counter.builder("ca.break.auto_remediated_total").register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    /** K-09 gradient-boost model inference. */
    public interface ModelInferencePort {
        /** Returns the predicted label for the provided break feature vector. */
        String predictLabel(BreakFeatureVector features);
        /** Returns SHAP top-N feature attributions (feature_name → attribution). */
        List<ShapFeature> shapTopFeatures(BreakFeatureVector features, int n);
    }

    public interface RemediationPort {
        void triggerRecalculation(String caId, String clientId);
        void triggerReprocess(String caId, String clientId);
        void escalate(String caId, String clientId, String label, String reason);
    }

    // ─── Break labels ─────────────────────────────────────────────────────────

    public enum BreakLabel {
        DATA_FEED_LATENCY,
        ENTITLEMENT_CALCULATION_ERROR,
        TAX_MISMATCH,
        ELECTION_MISMATCH,
        CURRENCY_ROUNDING,
        SYSTEMS_BUG,
        GENUINE_BREAK
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record BreakFeatureVector(String breakId, String caId, String clientId,
                                      String breakType, double expectedValue, double actualValue,
                                      double difference, boolean isCashBreak,
                                      double elapsedDaysSincePaymentDate, int retryCount) {}

    public record ShapFeature(String featureName, double attribution) {}

    public record BreakClassification(String classificationId, String breakId, String caId,
                                       String clientId, BreakLabel label, double confidence,
                                       List<ShapFeature> shapFeatures, String remediationAction,
                                       LocalDateTime classifiedAt) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<List<BreakClassification>> classifyBreaks(String caId) {
        return Promise.ofBlocking(executor, () -> {
            List<CaReconBreakRow> openBreaks = loadOpenBreaks(caId);
            List<BreakClassification> results = new ArrayList<>();
            for (CaReconBreakRow row : openBreaks) {
                BreakFeatureVector features = buildFeatureVector(row, caId);
                String rawLabel             = modelPort.predictLabel(features);
                BreakLabel label            = parseLabel(rawLabel);
                List<ShapFeature> shap      = modelPort.shapTopFeatures(features, TOP_SHAP_FEATURES);
                String action               = route(label, row);

                BreakClassification classification = new BreakClassification(
                        UUID.randomUUID().toString(), row.breakId(), caId, row.clientId(),
                        label, 1.0, shap, action, LocalDateTime.now());

                persistClassification(classification);
                classifiedCounter.increment();
                results.add(classification);
            }
            return results;
        });
    }

    // ─── Routing ─────────────────────────────────────────────────────────────

    private String route(BreakLabel label, CaReconBreakRow row) {
        return switch (label) {
            case DATA_FEED_LATENCY, ENTITLEMENT_CALCULATION_ERROR -> {
                remediationPort.triggerRecalculation(row.caId(), row.clientId());
                autoRemediatedCounter.increment();
                yield "RECALCULATE";
            }
            case TAX_MISMATCH, ELECTION_MISMATCH, CURRENCY_ROUNDING -> {
                remediationPort.triggerReprocess(row.caId(), row.clientId());
                autoRemediatedCounter.increment();
                yield "REPROCESS";
            }
            case SYSTEMS_BUG, GENUINE_BREAK -> {
                remediationPort.escalate(row.caId(), row.clientId(), label.name(),
                        "Manual review required: " + label);
                yield "ESCALATE";
            }
        };
    }

    // ─── Feature engineering ─────────────────────────────────────────────────

    private BreakFeatureVector buildFeatureVector(CaReconBreakRow row, String caId) throws SQLException {
        String paymentDateSql = "SELECT payment_date FROM corporate_actions WHERE ca_id=?";
        LocalDate paymentDate = LocalDate.now();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(paymentDateSql)) {
            ps.setString(1, caId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) paymentDate = rs.getObject("payment_date", LocalDate.class);
            }
        }
        long elapsedDays = java.time.temporal.ChronoUnit.DAYS.between(paymentDate, LocalDate.now());

        return new BreakFeatureVector(row.breakId(), row.caId(), row.clientId(),
                row.breakType(), row.expectedValue().doubleValue(), row.actualValue().doubleValue(),
                row.difference().doubleValue(), row.isCashBreak(),
                elapsedDays, row.retryCount());
    }

    private BreakLabel parseLabel(String raw) {
        try {
            return BreakLabel.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return BreakLabel.GENUINE_BREAK; // safe default
        }
    }

    // ─── Persistence ─────────────────────────────────────────────────────────

    private void persistClassification(BreakClassification c) throws SQLException {
        String sql = """
                INSERT INTO ca_break_classifications
                    (classification_id, break_id, ca_id, client_id, label, shap_features,
                     remediation_action, classified_at)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?)
                ON CONFLICT (break_id) DO UPDATE
                    SET label=EXCLUDED.label, shap_features=EXCLUDED.shap_features,
                        remediation_action=EXCLUDED.remediation_action,
                        classified_at=EXCLUDED.classified_at
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, c.classificationId()); ps.setString(2, c.breakId());
            ps.setString(3, c.caId()); ps.setString(4, c.clientId());
            ps.setString(5, c.label().name()); ps.setString(6, shapToJson(c.shapFeatures()));
            ps.setString(7, c.remediationAction()); ps.setObject(8, c.classifiedAt());
            ps.executeUpdate();
        }
    }

    private String shapToJson(List<ShapFeature> shap) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < shap.size(); i++) {
            ShapFeature f = shap.get(i);
            sb.append("{\"feature\":\"").append(f.featureName())
              .append("\",\"attribution\":").append(f.attribution()).append("}");
            if (i < shap.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    // ─── Data loading ─────────────────────────────────────────────────────────

    record CaReconBreakRow(String breakId, String caId, String clientId, String breakType,
                            java.math.BigDecimal expectedValue, java.math.BigDecimal actualValue,
                            java.math.BigDecimal difference, boolean isCashBreak, int retryCount) {}

    private List<CaReconBreakRow> loadOpenBreaks(String caId) throws SQLException {
        String sql = """
                SELECT break_id, ca_id, client_id, break_type, expected_value, actual_value,
                       difference,
                       (break_type IN ('AMOUNT_MISMATCH','MISSING_PAYMENT','EXTRA_PAYMENT')) AS is_cash_break,
                       COALESCE(retry_count, 0) AS retry_count
                FROM ca_recon_breaks
                WHERE ca_id=? AND status='OPEN'
                """;
        List<CaReconBreakRow> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, caId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.add(new CaReconBreakRow(
                        rs.getString("break_id"), rs.getString("ca_id"), rs.getString("client_id"),
                        rs.getString("break_type"), rs.getBigDecimal("expected_value"),
                        rs.getBigDecimal("actual_value"), rs.getBigDecimal("difference"),
                        rs.getBoolean("is_cash_break"), rs.getInt("retry_count")));
            }
        }
        return rows;
    }
}
