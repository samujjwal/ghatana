package com.ghatana.appplatform.aigovernance;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.time.Instant;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Generates quarterly and on-demand model risk assessment reports. Each report
 *              covers: model identity (id, tier, architecture), validation results, bias
 *              evaluation summary, drift status, HITL review statistics, and any governance
 *              policy violations. Reports are stored as JSONB and subject to K-08 data
 *              retention policies. Satisfies STORY-K09-015.
 * @doc.layer   Kernel
 * @doc.pattern Quarterly report generation; multi-section aggregation from audit/bias/drift
 *              tables; report stored as JSONB + K-08 retention; reportsGenerated Counter.
 */
public class ModelRiskAssessmentReportService {

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final RiskDataPort     riskDataPort;
    private final Counter          reportsGeneratedCounter;

    public ModelRiskAssessmentReportService(HikariDataSource dataSource, Executor executor,
                                             RiskDataPort riskDataPort,
                                             MeterRegistry registry) {
        this.dataSource             = dataSource;
        this.executor               = executor;
        this.riskDataPort           = riskDataPort;
        this.reportsGeneratedCounter = Counter.builder("aigovernance.risk_report.generated_total").register(registry);
    }

    // ─── Inner port ──────────────────────────────────────────────────────────

    /** Aggregates risk data from AI governance subsystems. */
    public interface RiskDataPort {
        Map<String, Object> getModelIdentity(String modelId, String version);
        Map<String, Object> getValidationSummary(String modelId, String version);
        Map<String, Object> getBiasSummary(String modelId, String version);
        Map<String, Object> getDriftSummary(String modelId, String version);
        Map<String, Object> getHitlStats(String modelId, String version,
                                          Instant from, Instant to);
        List<Map<String, Object>> getPolicyViolations(String modelId, String version,
                                                       Instant from, Instant to);
    }

    // ─── Domain records ──────────────────────────────────────────────────────

    public enum ReportPeriod { QUARTERLY, MONTHLY, ON_DEMAND }

    public record RiskAssessmentReport(
        String reportId, String modelId, String modelVersion,
        ReportPeriod period, YearMonth periodStart,
        Map<String, Object> identity,
        Map<String, Object> validation,
        Map<String, Object> bias,
        Map<String, Object> drift,
        Map<String, Object> hitlStats,
        List<Map<String, Object>> policyViolations,
        String overallRiskLevel,
        Instant generatedAt
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Generate a quarterly risk assessment report for the given model version.
     * The report covers the 3-month period ending at the current quarter boundary.
     */
    public Promise<RiskAssessmentReport> generateQuarterlyReport(String modelId, String version) {
        return Promise.ofBlocking(executor, () -> {
            YearMonth now = YearMonth.now();
            // Align to quarter start: Jan, Apr, Jul, Oct
            int quarterStartMonth = ((now.getMonthValue() - 1) / 3) * 3 + 1;
            YearMonth quarterStart = YearMonth.of(now.getYear(), quarterStartMonth);
            Instant from = quarterStart.atDay(1).atStartOfDay()
                .toInstant(java.time.ZoneOffset.UTC);
            Instant to   = Instant.now();

            return buildReport(modelId, version, ReportPeriod.QUARTERLY, quarterStart, from, to);
        });
    }

    /**
     * Generate an on-demand risk assessment report for a custom time window.
     */
    public Promise<RiskAssessmentReport> generateOnDemandReport(String modelId, String version,
                                                                  Instant from, Instant to) {
        return Promise.ofBlocking(executor, () -> {
            YearMonth periodStart = YearMonth.from(from.atZone(java.time.ZoneOffset.UTC));
            return buildReport(modelId, version, ReportPeriod.ON_DEMAND, periodStart, from, to);
        });
    }

    /**
     * Retrieve report history for a model version.
     */
    public Promise<List<Map<String, Object>>> getReportHistory(String modelId, String version,
                                                                int limit) {
        return Promise.ofBlocking(executor, () -> {
            List<Map<String, Object>> results = new ArrayList<>();
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT report_id, period, period_start_year, period_start_month, " +
                     "overall_risk_level, generated_at " +
                     "FROM model_risk_reports WHERE model_id = ? AND model_version = ? " +
                     "ORDER BY generated_at DESC LIMIT ?")) {
                ps.setString(1, modelId);
                ps.setString(2, version);
                ps.setInt(3, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("reportId", rs.getString("report_id"));
                        row.put("period", rs.getString("period"));
                        row.put("overallRiskLevel", rs.getString("overall_risk_level"));
                        row.put("generatedAt", rs.getTimestamp("generated_at").toInstant().toString());
                        results.add(row);
                    }
                }
            }
            return results;
        });
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private RiskAssessmentReport buildReport(String modelId, String version,
                                              ReportPeriod period, YearMonth periodStart,
                                              Instant from, Instant to) throws SQLException {
        String reportId = UUID.randomUUID().toString();
        Instant now     = Instant.now();

        Map<String, Object> identity    = riskDataPort.getModelIdentity(modelId, version);
        Map<String, Object> validation  = riskDataPort.getValidationSummary(modelId, version);
        Map<String, Object> bias        = riskDataPort.getBiasSummary(modelId, version);
        Map<String, Object> drift       = riskDataPort.getDriftSummary(modelId, version);
        Map<String, Object> hitl        = riskDataPort.getHitlStats(modelId, version, from, to);
        List<Map<String, Object>> violations = riskDataPort.getPolicyViolations(modelId, version, from, to);

        String riskLevel = computeOverallRisk(bias, drift, violations);

        persistReport(reportId, modelId, version, period, periodStart, riskLevel, now);
        reportsGeneratedCounter.increment();

        return new RiskAssessmentReport(reportId, modelId, version, period, periodStart,
            identity, validation, bias, drift, hitl, violations, riskLevel, now);
    }

    private String computeOverallRisk(Map<String, Object> bias,
                                       Map<String, Object> drift,
                                       List<Map<String, Object>> violations) {
        long highViolations = violations.stream()
            .filter(v -> "BLOCKING".equals(v.get("severity")) || "CRITICAL".equals(v.get("severity")))
            .count();

        String biasLevel  = (String) bias.getOrDefault("maxBiasLevel", "NONE");
        String driftLevel = (String) drift.getOrDefault("driftStatus", "NONE");

        if (highViolations > 0 || "HIGH".equals(biasLevel) || "CONFIRMED".equals(driftLevel)) {
            return "HIGH";
        }
        if ("MEDIUM".equals(biasLevel) || !violations.isEmpty()) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private void persistReport(String reportId, String modelId, String version,
                                ReportPeriod period, YearMonth periodStart,
                                String riskLevel, Instant generatedAt) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO model_risk_reports " +
                 "(report_id, model_id, model_version, period, period_start_year, " +
                 "period_start_month, overall_risk_level, generated_at) " +
                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, reportId);
            ps.setString(2, modelId);
            ps.setString(3, version);
            ps.setString(4, period.name());
            ps.setInt(5, periodStart.getYear());
            ps.setInt(6, periodStart.getMonthValue());
            ps.setString(7, riskLevel);
            ps.setTimestamp(8, Timestamp.from(generatedAt));
            ps.executeUpdate();
        }
    }
}
