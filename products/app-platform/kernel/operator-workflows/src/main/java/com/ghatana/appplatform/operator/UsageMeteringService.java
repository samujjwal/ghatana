package com.ghatana.appplatform.operator;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Track billable events per tenant for usage-based billing.
 *              Billable metrics: API calls per service, settlement volume, reconciliations,
 *              report submissions, plugin executions.
 *              Time-series metering: per tenant, per metric, per day (UTC).
 *              Monthly billing report per tenant: usage summary against plan tiers.
 *              Plan tiers: flat_fee + per-unit overage pricing.
 *              Export to CSV/PDF for invoicing.
 * @doc.layer   Operator Workflows (O-01)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking; Micrometer
 *
 * STORY-O01-011: Usage metering for billing
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS tenant_billing_plans (
 *   plan_id        TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   tenant_id      TEXT NOT NULL UNIQUE,
 *   plan_name      TEXT NOT NULL,
 *   flat_fee       NUMERIC(12,2) NOT NULL DEFAULT 0,
 *   api_call_unit_price    NUMERIC(10,6) NOT NULL DEFAULT 0,
 *   settlement_unit_price  NUMERIC(10,6) NOT NULL DEFAULT 0,
 *   plugin_exec_unit_price NUMERIC(10,6) NOT NULL DEFAULT 0,
 *   free_tier_api_calls    BIGINT NOT NULL DEFAULT 0,
 *   updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * CREATE TABLE IF NOT EXISTS tenant_usage_metering (
 *   meter_id       TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   tenant_id      TEXT NOT NULL,
 *   metric_type    TEXT NOT NULL,  -- API_CALLS | SETTLEMENTS | RECONCILIATIONS | REPORT_SUBMISSIONS | PLUGIN_EXECUTIONS
 *   service_name   TEXT,
 *   usage_date     DATE NOT NULL,
 *   quantity       BIGINT NOT NULL DEFAULT 0,
 *   UNIQUE (tenant_id, metric_type, service_name, usage_date)
 * );
 * CREATE INDEX IF NOT EXISTS idx_usage_tenant_date ON tenant_usage_metering(tenant_id, usage_date);
 * </pre>
 */
public class UsageMeteringService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface BillingExportPort {
        String exportInvoicePdf(String tenantId, String period, Map<String, Object> billData) throws Exception;
        String exportUsageCsv(String tenantId, String period, List<Map<String, Object>> rows) throws Exception;
    }

    public interface AuditPort {
        void record(String actorId, String action, String detail) throws Exception;
    }

    // ── Value types ───────────────────────────────────────────────────────────

    public record BillingReport(
        String tenantId, String period, Map<String, Long> metricTotals,
        java.math.BigDecimal flatFee, java.math.BigDecimal overageCharge,
        java.math.BigDecimal totalDue, String exportPath
    ) {}

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final BillingExportPort exporter;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter meteringEventsCounter;

    public UsageMeteringService(
        javax.sql.DataSource ds,
        BillingExportPort exporter,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds                   = ds;
        this.exporter             = exporter;
        this.audit                = audit;
        this.executor             = executor;
        this.meteringEventsCounter = Counter.builder("operator.billing.metering_events").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Record a metering event for a tenant. Called by service interceptors.
     * Uses UPSERT to increment the daily counter atomically.
     */
    public Promise<Void> record(String tenantId, String metricType, String serviceName, long quantity) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO tenant_usage_metering (tenant_id, metric_type, service_name, usage_date, quantity) " +
                     "VALUES (?,?,?,CURRENT_DATE,?) " +
                     "ON CONFLICT (tenant_id, metric_type, service_name, usage_date) " +
                     "DO UPDATE SET quantity = tenant_usage_metering.quantity + EXCLUDED.quantity"
                 )) {
                ps.setString(1, tenantId); ps.setString(2, metricType);
                ps.setString(3, serviceName); ps.setLong(4, quantity);
                ps.executeUpdate();
            }
            meteringEventsCounter.increment();
            return null;
        });
    }

    /** Generate monthly billing report for a tenant (month = "2026-03"). */
    public Promise<BillingReport> monthlyReport(String tenantId, String month, String format, String requestedBy) {
        return Promise.ofBlocking(executor, () -> {
            java.time.YearMonth ym = java.time.YearMonth.parse(month);
            java.time.LocalDate from = ym.atDay(1);
            java.time.LocalDate to   = ym.atEndOfMonth();

            // Aggregate usage by metric type
            Map<String, Long> totals = new LinkedHashMap<>();
            List<Map<String, Object>> rows = new ArrayList<>();
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT metric_type, service_name, SUM(quantity)::bigint AS total " +
                     "FROM tenant_usage_metering WHERE tenant_id=? AND usage_date BETWEEN ? AND ? " +
                     "GROUP BY metric_type, service_name ORDER BY metric_type, service_name"
                 )) {
                ps.setString(1, tenantId); ps.setDate(2, Date.valueOf(from)); ps.setDate(3, Date.valueOf(to));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String metricType = rs.getString("metric_type");
                        long qty = rs.getLong("total");
                        totals.merge(metricType, qty, Long::sum);
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("metricType", metricType);
                        row.put("serviceName", rs.getString("service_name"));
                        row.put("quantity", qty);
                        rows.add(row);
                    }
                }
            }

            // Load plan
            PlanData plan = loadPlan(tenantId);
            java.math.BigDecimal overage = calculateOverage(totals, plan);
            java.math.BigDecimal total = plan.flatFee().add(overage);

            Map<String, Object> billData = new LinkedHashMap<>();
            billData.put("tenantId", tenantId); billData.put("period", month);
            billData.put("flatFee", plan.flatFee()); billData.put("overage", overage); billData.put("total", total);
            billData.put("usage", totals);

            String exportPath = "PDF".equalsIgnoreCase(format)
                ? exporter.exportInvoicePdf(tenantId, month, billData)
                : exporter.exportUsageCsv(tenantId, month, rows);

            audit.record(requestedBy, "BILLING_REPORT_GENERATED", "tenantId=" + tenantId + " period=" + month);
            return new BillingReport(tenantId, month, totals, plan.flatFee(), overage, total, exportPath);
        });
    }

    /** Return raw daily usage rows for a tenant over a date range. */
    public Promise<List<Map<String, Object>>> usageTimeSeries(String tenantId,
                                                               java.time.LocalDate from,
                                                               java.time.LocalDate to) {
        return Promise.ofBlocking(executor, () -> {
            List<Map<String, Object>> rows = new ArrayList<>();
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT usage_date::text, metric_type, service_name, SUM(quantity)::bigint AS qty " +
                     "FROM tenant_usage_metering WHERE tenant_id=? AND usage_date BETWEEN ? AND ? " +
                     "GROUP BY usage_date, metric_type, service_name ORDER BY usage_date, metric_type"
                 )) {
                ps.setString(1, tenantId); ps.setDate(2, Date.valueOf(from)); ps.setDate(3, Date.valueOf(to));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> r = new LinkedHashMap<>();
                        r.put("date", rs.getString("usage_date"));
                        r.put("metricType", rs.getString("metric_type"));
                        r.put("serviceName", rs.getString("service_name"));
                        r.put("quantity", rs.getLong("qty"));
                        rows.add(r);
                    }
                }
            }
            return rows;
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private record PlanData(java.math.BigDecimal flatFee, java.math.BigDecimal apiCallUnitPrice,
                             java.math.BigDecimal settlementUnitPrice, long freeApiCalls) {}

    private PlanData loadPlan(String tenantId) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT flat_fee, api_call_unit_price, settlement_unit_price, free_tier_api_calls " +
                 "FROM tenant_billing_plans WHERE tenant_id=?"
             )) {
            ps.setString(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return new PlanData(java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO,
                    java.math.BigDecimal.ZERO, 0);
                return new PlanData(rs.getBigDecimal("flat_fee"), rs.getBigDecimal("api_call_unit_price"),
                    rs.getBigDecimal("settlement_unit_price"), rs.getLong("free_tier_api_calls"));
            }
        }
    }

    private java.math.BigDecimal calculateOverage(Map<String, Long> totals, PlanData plan) {
        java.math.BigDecimal overage = java.math.BigDecimal.ZERO;
        long apiCalls = totals.getOrDefault("API_CALLS", 0L);
        long billableApiCalls = Math.max(0, apiCalls - plan.freeApiCalls());
        overage = overage.add(plan.apiCallUnitPrice().multiply(java.math.BigDecimal.valueOf(billableApiCalls)));
        long settlements = totals.getOrDefault("SETTLEMENTS", 0L);
        overage = overage.add(plan.settlementUnitPrice().multiply(java.math.BigDecimal.valueOf(settlements)));
        return overage;
    }
}
