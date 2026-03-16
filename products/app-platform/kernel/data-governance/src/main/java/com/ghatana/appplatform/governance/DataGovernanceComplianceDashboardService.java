package com.ghatana.appplatform.governance;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.time.Instant;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @doc.type    DomainService
 * @doc.purpose Governance compliance snapshot publisher. Queries the catalog, classification,
 *              quality, retention, and lineage services to produce five coverage KPIs:
 *              catalogCoverage, classificationCoverage, avgQualityScore, retentionCompliance,
 *              lineageCoverage. Each KPI has a traffic-light indicator: GREEN ≥90%, YELLOW
 *              70-89%, RED <70%. Publishes the snapshot to K-06 DashboardPort and generates
 *              a monthly JSON compliance report on demand. Satisfies STORY-K08-012.
 * @doc.layer   Kernel
 * @doc.pattern K-06 DashboardPort publishing; traffic-light KPI thresholds; monthly report
 *              auto-generation; Gauge metrics per KPI; compliance snapshots persisted.
 */
public class DataGovernanceComplianceDashboardService {

    private static final double GREEN_THRESHOLD  = 90.0;
    private static final double YELLOW_THRESHOLD = 70.0;

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final DashboardPort    dashboardPort;
    private final AtomicReference<Double> latestCatalogCoverage       = new AtomicReference<>(0.0);
    private final AtomicReference<Double> latestClassificationCoverage = new AtomicReference<>(0.0);
    private final AtomicReference<Double> latestAvgQualityScore        = new AtomicReference<>(0.0);
    private final AtomicReference<Double> latestRetentionCompliance    = new AtomicReference<>(0.0);
    private final AtomicReference<Double> latestLineageCoverage        = new AtomicReference<>(0.0);

    public DataGovernanceComplianceDashboardService(HikariDataSource dataSource, Executor executor,
                                                     DashboardPort dashboardPort,
                                                     MeterRegistry registry) {
        this.dataSource    = dataSource;
        this.executor      = executor;
        this.dashboardPort = dashboardPort;

        Gauge.builder("governance.compliance.catalog_coverage", latestCatalogCoverage, AtomicReference::get).register(registry);
        Gauge.builder("governance.compliance.classification_coverage", latestClassificationCoverage, AtomicReference::get).register(registry);
        Gauge.builder("governance.compliance.avg_quality_score", latestAvgQualityScore, AtomicReference::get).register(registry);
        Gauge.builder("governance.compliance.retention_compliance", latestRetentionCompliance, AtomicReference::get).register(registry);
        Gauge.builder("governance.compliance.lineage_coverage", latestLineageCoverage, AtomicReference::get).register(registry);
    }

    // ─── Inner port ──────────────────────────────────────────────────────────

    /** K-06 DashboardPort: publishes metrics maps to workspace dashboards. */
    public interface DashboardPort {
        void publishMetrics(String dashboardId, Map<String, Object> metrics);
    }

    // ─── Domain records ──────────────────────────────────────────────────────

    public enum TrafficLight { GREEN, YELLOW, RED }

    public record KpiSnapshot(
        double catalogCoverage, TrafficLight catalogLight,
        double classificationCoverage, TrafficLight classificationLight,
        double avgQualityScore, TrafficLight qualityLight,
        double retentionCompliance, TrafficLight retentionLight,
        double lineageCoverage, TrafficLight lineageLight,
        Instant capturedAt
    ) {}

    public record MonthlyComplianceReport(
        String reportId, YearMonth period,
        KpiSnapshot snapshot,
        String summary, Instant generatedAt
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Compute latest KPI snapshot and publish to K-06 DashboardPort.
     */
    public Promise<KpiSnapshot> publishDashboard() {
        return Promise.ofBlocking(executor, () -> {
            KpiSnapshot snapshot = computeSnapshot();
            updateGauges(snapshot);

            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("catalogCoveragePercent", snapshot.catalogCoverage());
            metrics.put("catalogStatus", snapshot.catalogLight().name());
            metrics.put("classificationCoveragePercent", snapshot.classificationCoverage());
            metrics.put("classificationStatus", snapshot.classificationLight().name());
            metrics.put("avgQualityScore", snapshot.avgQualityScore());
            metrics.put("qualityStatus", snapshot.qualityLight().name());
            metrics.put("retentionCompliancePercent", snapshot.retentionCompliance());
            metrics.put("retentionStatus", snapshot.retentionLight().name());
            metrics.put("lineageCoveragePercent", snapshot.lineageCoverage());
            metrics.put("lineageStatus", snapshot.lineageLight().name());
            metrics.put("capturedAt", snapshot.capturedAt().toString());

            dashboardPort.publishMetrics("data-governance-compliance", metrics);
            persistSnapshot(snapshot);
            return snapshot;
        });
    }

    /**
     * Generate and persist a monthly compliance report for the current month.
     */
    public Promise<MonthlyComplianceReport> generateMonthlyReport() {
        return Promise.ofBlocking(executor, () -> {
            YearMonth period = YearMonth.now();
            String reportId  = UUID.randomUUID().toString();
            Instant now      = Instant.now();
            KpiSnapshot snapshot = computeSnapshot();

            String summary = String.format(
                "Period: %s | Catalog: %.1f%% (%s) | Classification: %.1f%% (%s) | " +
                "Quality: %.1f (%s) | Retention: %.1f%% (%s) | Lineage: %.1f%% (%s)",
                period,
                snapshot.catalogCoverage(), snapshot.catalogLight(),
                snapshot.classificationCoverage(), snapshot.classificationLight(),
                snapshot.avgQualityScore(), snapshot.qualityLight(),
                snapshot.retentionCompliance(), snapshot.retentionLight(),
                snapshot.lineageCoverage(), snapshot.lineageLight()
            );

            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO governance_monthly_reports " +
                     "(report_id, period_year, period_month, summary, generated_at) " +
                     "VALUES (?, ?, ?, ?, NOW()) " +
                     "ON CONFLICT (period_year, period_month) DO UPDATE SET " +
                     "summary = EXCLUDED.summary, generated_at = NOW()")) {
                ps.setString(1, reportId);
                ps.setInt(2, period.getYear());
                ps.setInt(3, period.getMonthValue());
                ps.setString(4, summary);
                ps.executeUpdate();
            }

            return new MonthlyComplianceReport(reportId, period, snapshot, summary, now);
        });
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private KpiSnapshot computeSnapshot() throws SQLException {
        double catalogCoverage       = queryCatalogCoverage();
        double classificationCoverage = queryClassificationCoverage();
        double avgQuality            = queryAvgQualityScore();
        double retentionCompliance   = queryRetentionCompliance();
        double lineageCoverage       = queryLineageCoverage();
        return new KpiSnapshot(
            catalogCoverage,       trafficLight(catalogCoverage),
            classificationCoverage, trafficLight(classificationCoverage),
            avgQuality,            trafficLight(avgQuality),
            retentionCompliance,   trafficLight(retentionCompliance),
            lineageCoverage,       trafficLight(lineageCoverage),
            Instant.now()
        );
    }

    private double queryCatalogCoverage() throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT ROUND(100.0 * COUNT(*) FILTER (WHERE in_catalog) / NULLIF(COUNT(*), 0), 2) " +
                 "FROM data_catalog_assets WHERE lifecycle_status = 'ACTIVE'")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0.0;
            }
        }
    }

    private double queryClassificationCoverage() throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT ROUND(100.0 * COUNT(*) FILTER (WHERE classification_level IS NOT NULL) " +
                 "/ NULLIF(COUNT(*), 0), 2) FROM data_catalog_assets WHERE lifecycle_status = 'ACTIVE'")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0.0;
            }
        }
    }

    private double queryAvgQualityScore() throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT ROUND(AVG(latest_quality_score), 2) FROM data_catalog_assets " +
                 "WHERE lifecycle_status = 'ACTIVE' AND latest_quality_score IS NOT NULL")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0.0;
            }
        }
    }

    private double queryRetentionCompliance() throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT ROUND(100.0 * COUNT(*) FILTER (WHERE p.policy_id IS NOT NULL) " +
                 "/ NULLIF(COUNT(*), 0), 2) " +
                 "FROM data_catalog_assets a LEFT JOIN data_retention_policies p USING (asset_id) " +
                 "WHERE a.lifecycle_status = 'ACTIVE'")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0.0;
            }
        }
    }

    private double queryLineageCoverage() throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT ROUND(100.0 * COUNT(DISTINCT asset_id) " +
                 "/ NULLIF((SELECT COUNT(*) FROM data_catalog_assets WHERE lifecycle_status = 'ACTIVE'), 0), 2) " +
                 "FROM data_lineage_edges")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0.0;
            }
        }
    }

    private TrafficLight trafficLight(double value) {
        if (value >= GREEN_THRESHOLD)  return TrafficLight.GREEN;
        if (value >= YELLOW_THRESHOLD) return TrafficLight.YELLOW;
        return TrafficLight.RED;
    }

    private void updateGauges(KpiSnapshot snap) {
        latestCatalogCoverage.set(snap.catalogCoverage());
        latestClassificationCoverage.set(snap.classificationCoverage());
        latestAvgQualityScore.set(snap.avgQualityScore());
        latestRetentionCompliance.set(snap.retentionCompliance());
        latestLineageCoverage.set(snap.lineageCoverage());
    }

    private void persistSnapshot(KpiSnapshot snap) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO governance_compliance_snapshots " +
                 "(catalog_coverage, classification_coverage, avg_quality_score, " +
                 "retention_compliance, lineage_coverage, captured_at) " +
                 "VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setDouble(1, snap.catalogCoverage());
            ps.setDouble(2, snap.classificationCoverage());
            ps.setDouble(3, snap.avgQualityScore());
            ps.setDouble(4, snap.retentionCompliance());
            ps.setDouble(5, snap.lineageCoverage());
            ps.setTimestamp(6, Timestamp.from(snap.capturedAt()));
            ps.executeUpdate();
        }
    }
}
