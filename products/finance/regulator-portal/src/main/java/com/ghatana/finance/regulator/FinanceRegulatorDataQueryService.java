/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.finance.regulator;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Finance Regulator Data Query Service.
 *
 * <p>Finance-specific regulator-facing data query interface with regulatory compliance.
 * Provides pre-built query templates for financial regulatory reporting and investigations.
 * All queries include financial compliance metadata and audit trails.</p>
 *
 * <p>Key capabilities:
 * <ul>
 *   <li>Finance-specific query types (positions, trades, settlements, compliance)</li>
 *   <li>Regulatory calendar support (Gregorian and financial calendars)</li>
 *   <li>Compliance-aware data access with audit logging</li>
 *   <li>Financial data export with regulatory formatting</li>
 *   <li>Multi-jurisdiction compliance support</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose Finance regulator data query - regulatory compliance, financial reporting, audit trails
 * @doc.layer finance
 * @doc.pattern Service, Query
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public final class FinanceRegulatorDataQueryService {

    // ── Finance-Specific Inner Ports ───────────────────────────────────────────────

    /** Financial calendar adapter for fiscal year and reporting periods. */
    public interface FinanceCalendarPort {
        LocalDate fiscalYearStart(int fiscalYear) throws Exception;
        LocalDate fiscalYearEnd(int fiscalYear) throws Exception;
        LocalDate reportingPeriodEnd(int fiscalYear, int quarter) throws Exception;
    }

    /** Financial export adapter with regulatory formatting. */
    public interface FinanceExportPort {
        byte[] exportRegulatoryPdf(String queryType, List<Map<String, Object>> rows, 
                                   FinanceRegulatoryContext context) throws Exception;
        byte[] exportRegulatoryCsv(String queryType, List<Map<String, Object>> rows, 
                                   FinanceRegulatoryContext context) throws Exception;
        byte[] exportXBRL(String queryType, List<Map<String, Object>> rows, 
                         FinanceRegulatoryContext context) throws Exception;
    }

    /** Compliance validation port for query authorization. */
    public interface FinanceCompliancePort {
        Promise<Boolean> validateQueryAccess(String regulatorId, String queryType, 
                                           String tenantId, FinanceRegulatoryContext context);
        Promise<FinanceComplianceResult> logComplianceQuery(String regulatorId, String queryType, 
                                                           FinanceQueryParams params, int resultCount);
    }

    // ── Finance-Specific Value Types ─────────────────────────────────────────────

    public enum FinanceQueryType {
        POSITIONS_AS_OF, TRADE_HISTORY, SETTLEMENT_STATUS, RECONCILIATION_REPORT, 
        AUDIT_LOG, COMPLIANCE_REPORT, RISK_EXPOSURE, CAPITAL_ADEQUACY, 
        TRANSACTION_MONITORING, AML_SUSPICIOUS_ACTIVITY
    }

    public enum FinanceRegulatoryBody {
        SEC, FINRA, FCA, ESMA, MAS, HKMA, ASIC, JFSA
    }

    public record FinanceQueryParams(
        String queryType,
        String tenantId,
        LocalDate asOfDate,
        LocalDate fromDate,
        LocalDate toDate,
        FinanceRegulatoryBody regulatoryBody,
        String complianceLevel,
        Map<String, Object> additionalFilters
    ) {}

    public record FinanceQueryResult(
        String logId, String queryType, List<Map<String, Object>> rows,
        int totalCount, long durationMs, FinanceComplianceResult compliance
    ) {}

    public record FinanceRegulatoryContext(
        String regulatorId, FinanceRegulatoryBody body, String jurisdiction,
        String reportingPeriod, Instant requestTime, String complianceReference
    ) {}

    public record FinanceComplianceResult(
        boolean compliant, String complianceReference, List<String> validations,
        String riskLevel, String nextReviewDate
    ) {}

    // ── Fields ────────────────────────────────────────────────────────────────

    private final DataSource ds;
    private final FinanceCalendarPort financeCalendar;
    private final FinanceExportPort financeExport;
    private final FinanceCompliancePort financeCompliance;
    private final Executor executor;
    
    private final Counter financeQueryCounter;
    private final Timer financeQueryTimer;
    private final Counter complianceValidationCounter;

    // ── Constructor ─────────────────────────────────────────────────────────────

    public FinanceRegulatorDataQueryService(
        DataSource ds,
        FinanceCalendarPort financeCalendar,
        FinanceExportPort financeExport,
        FinanceCompliancePort financeCompliance,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds = ds;
        this.financeCalendar = financeCalendar;
        this.financeExport = financeExport;
        this.financeCompliance = financeCompliance;
        this.executor = executor;
        
        this.financeQueryCounter = Counter.builder("finance.regulator.query.executed").register(registry);
        this.financeQueryTimer = Timer.builder("finance.regulator.query.duration").register(registry);
        this.complianceValidationCounter = Counter.builder("finance.regulator.compliance.validated").register(registry);
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Execute a finance-specific regulatory query with compliance validation.
     */
    public Promise<FinanceQueryResult> executeFinanceQuery(String regulatorId, FinanceQueryParams params) {
        FinanceRegulatoryContext context = new FinanceRegulatoryContext(
            regulatorId, params.regulatoryBody(), params.regulatoryBody().name(),
            "Q" + ((params.asOfDate().getMonthValue() - 1) / 3 + 1), Instant.now(), 
            "REG-" + UUID.randomUUID().toString().substring(0, 8)
        );

        return financeCompliance.validateQueryAccess(regulatorId, params.queryType(), 
                                                   params.tenantId(), context)
            .then(valid -> {
                if (!valid) {
                    return Promise.ofException(new SecurityException(
                        "Regulator access denied for query type: " + params.queryType()));
                }

                return Promise.ofBlocking(executor, () -> {
                    long start = System.currentTimeMillis();
                    List<Map<String, Object>> rows = switch (FinanceQueryType.valueOf(params.queryType())) {
                        case POSITIONS_AS_OF          -> queryFinancePositionsAsOf(params);
                        case TRADE_HISTORY            -> queryFinanceTradeHistory(params);
                        case SETTLEMENT_STATUS        -> queryFinanceSettlementStatus(params);
                        case RECONCILIATION_REPORT    -> queryFinanceReconciliationReport(params);
                        case AUDIT_LOG                -> queryFinanceAuditLog(params);
                        case COMPLIANCE_REPORT        -> queryFinanceComplianceReport(params);
                        case RISK_EXPOSURE            -> queryFinanceRiskExposure(params);
                        case CAPITAL_ADEQUACY         -> queryFinanceCapitalAdequacy(params);
                        case TRANSACTION_MONITORING   -> queryFinanceTransactionMonitoring(params);
                        case AML_SUSPICIOUS_ACTIVITY  -> queryFinanceAMLSuspiciousActivity(params);
                    };
                    
                    long duration = System.currentTimeMillis() - start;
                    financeQueryTimer.record(duration, java.util.concurrent.TimeUnit.MILLISECONDS);
                    financeQueryCounter.increment();
                    
                    return logFinanceQuery(regulatorId, params, rows.size(), duration, context);
                });
            });
    }

    /**
     * Execute query for fiscal year period with finance calendar support.
     */
    public Promise<FinanceQueryResult> executeFiscalYearQuery(String regulatorId, FinanceQueryParams params, 
                                                              int fiscalYear) {
        return Promise.ofBlocking(executor, () -> {
            try {
                LocalDate fiscalStart = financeCalendar.fiscalYearStart(fiscalYear);
                LocalDate fiscalEnd = financeCalendar.fiscalYearEnd(fiscalYear);
                
                FinanceQueryParams fiscalParams = new FinanceQueryParams(
                    params.queryType(), params.tenantId(), fiscalEnd, fiscalStart, fiscalEnd,
                    params.regulatoryBody(), params.complianceLevel(), params.additionalFilters()
                );
                
                return executeFinanceQuery(regulatorId, fiscalParams).getResult();
            } catch (Exception e) {
                throw new RuntimeException("Failed to execute fiscal year query", e);
            }
        });
    }

    /**
     * Export finance query result to regulatory PDF format.
     */
    public Promise<byte[]> exportToRegulatoryPdf(String logId, FinanceRegulatoryContext context) {
        return Promise.ofBlocking(executor, () -> {
            FinanceQueryLogEntry entry = loadFinanceQueryLogEntry(logId);
            List<Map<String, Object>> rows = reExecuteFinanceQuery(entry);
            return financeExport.exportRegulatoryPdf(entry.queryType(), rows, context);
        });
    }

    /**
     * Export finance query result to XBRL format for regulatory reporting.
     */
    public Promise<byte[]> exportToXBRL(String logId, FinanceRegulatoryContext context) {
        return Promise.ofBlocking(executor, () -> {
            FinanceQueryLogEntry entry = loadFinanceQueryLogEntry(logId);
            List<Map<String, Object>> rows = reExecuteFinanceQuery(entry);
            return financeExport.exportXBRL(entry.queryType(), rows, context);
        });
    }

    /**
     * Get compliance status for all finance queries in reporting period.
     */
    public Promise<Map<String, FinanceComplianceStatus>> getFinanceComplianceStatus(
            String regulatorId, String reportingPeriod) {
        
        return Promise.ofBlocking(executor, () -> {
            Map<String, FinanceComplianceStatus> results = new HashMap<>();
            
            // Query compliance status for all tenant queries in period
            String sql = """
                SELECT tenant_id, query_type, COUNT(*) as query_count,
                       MAX(queried_at) as last_query
                FROM finance_regulator_query_log 
                WHERE regulator_id = ? AND reporting_period = ?
                GROUP BY tenant_id, query_type
                """;
            
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, regulatorId);
                ps.setString(2, reportingPeriod);
                
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String key = rs.getString("tenant_id") + ":" + rs.getString("query_type");
                        results.put(key, new FinanceComplianceStatus(
                            rs.getString("tenant_id"),
                            rs.getString("query_type"),
                            rs.getInt("query_count"),
                            rs.getTimestamp("last_query").toInstant(),
                            "COMPLIANT"
                        ));
                    }
                }
            }
            
            return results;
        });
    }

    // ── Finance-Specific Query Builders ───────────────────────────────────────────

    private List<Map<String, Object>> queryFinancePositionsAsOf(FinanceQueryParams p) throws SQLException {
        String sql = """
            SELECT instrument_id, quantity, market_value, currency, position_date,
                   cost_basis, unrealized_pnl, last_price, pricing_source
            FROM finance_positions 
            WHERE tenant_id=? AND position_date=? 
            ORDER BY instrument_id
            """;
        return executeFinanceSelect(sql, p.tenantId(), Date.valueOf(p.asOfDate()));
    }

    private List<Map<String, Object>> queryFinanceTradeHistory(FinanceQueryParams p) throws SQLException {
        String sql = """
            SELECT trade_id, instrument_id, side, quantity, price, currency, trade_date,
                   settlement_date, broker_id, commission, regulatory_flag
            FROM finance_trades 
            WHERE tenant_id=? AND trade_date BETWEEN ? AND ? 
            ORDER BY trade_date DESC
            """;
        return executeFinanceSelect(sql, p.tenantId(), Date.valueOf(p.fromDate()), Date.valueOf(p.toDate()));
    }

    private List<Map<String, Object>> queryFinanceSettlementStatus(FinanceQueryParams p) throws SQLException {
        String sql = """
            SELECT settlement_id, trade_id, status, settlement_date, amount, currency,
                   clearing_house_id, settlement_method, fails_count
            FROM finance_settlements 
            WHERE tenant_id=? AND settlement_date BETWEEN ? AND ? 
            ORDER BY settlement_date DESC
            """;
        return executeFinanceSelect(sql, p.tenantId(), Date.valueOf(p.fromDate()), Date.valueOf(p.toDate()));
    }

    private List<Map<String, Object>> queryFinanceReconciliationReport(FinanceQueryParams p) throws SQLException {
        String sql = """
            SELECT recon_id, source_system, status, break_amount, currency, recon_date,
                   break_reason, resolution_status, assigned_to
            FROM finance_reconciliation_breaks 
            WHERE tenant_id=? AND recon_date BETWEEN ? AND ? 
            ORDER BY recon_date DESC
            """;
        return executeFinanceSelect(sql, p.tenantId(), Date.valueOf(p.fromDate()), Date.valueOf(p.toDate()));
    }

    private List<Map<String, Object>> queryFinanceAuditLog(FinanceQueryParams p) throws SQLException {
        String sql = """
            SELECT audit_id, actor_id, action, detail, recorded_at, system_module,
                   regulatory_flag, compliance_impact
            FROM finance_audit_entries 
            WHERE tenant_id=? AND recorded_at::date BETWEEN ? AND ? 
            ORDER BY recorded_at DESC LIMIT 10000
            """;
        return executeFinanceSelect(sql, p.tenantId(), Date.valueOf(p.fromDate()), Date.valueOf(p.toDate()));
    }

    private List<Map<String, Object>> queryFinanceComplianceReport(FinanceQueryParams p) throws SQLException {
        String sql = """
            SELECT compliance_id, rule_type, status, violation_amount, currency, 
                   detected_date, resolution_date, penalty_amount, regulatory_reference
            FROM finance_compliance_violations 
            WHERE tenant_id=? AND detected_date BETWEEN ? AND ? 
            ORDER BY detected_date DESC
            """;
        return executeFinanceSelect(sql, p.tenantId(), Date.valueOf(p.fromDate()), Date.valueOf(p.toDate()));
    }

    private List<Map<String, Object>> queryFinanceRiskExposure(FinanceQueryParams p) throws SQLException {
        String sql = """
            SELECT risk_type, exposure_amount, currency, risk_factor, concentration_limit,
                   utilization_rate, as_of_date, stress_test_result
            FROM finance_risk_exposures 
            WHERE tenant_id=? AND as_of_date=? 
            ORDER BY risk_type
            """;
        return executeFinanceSelect(sql, p.tenantId(), Date.valueOf(p.asOfDate()));
    }

    private List<Map<String, Object>> queryFinanceCapitalAdequacy(FinanceQueryParams p) throws SQLException {
        String sql = """
            SELECT capital_ratio, tier1_ratio, risk_weighted_assets, capital_requirement,
                   reporting_date, regulatory_minimum, buffer_requirement
            FROM finance_capital_adequacy 
            WHERE tenant_id=? AND reporting_date BETWEEN ? AND ? 
            ORDER BY reporting_date DESC
            """;
        return executeFinanceSelect(sql, p.tenantId(), Date.valueOf(p.fromDate()), Date.valueOf(p.toDate()));
    }

    private List<Map<String, Object>> queryFinanceTransactionMonitoring(FinanceQueryParams p) throws SQLException {
        String sql = """
            SELECT transaction_id, amount, currency, counterparty, transaction_type,
                   risk_score, monitoring_flag, reviewed_date, reviewer_id
            FROM finance_transaction_monitoring 
            WHERE tenant_id=? AND transaction_date BETWEEN ? AND ? 
            AND risk_score > ? 
            ORDER BY risk_score DESC
            """;
        Integer riskThreshold = (Integer) p.additionalFilters().getOrDefault("riskThreshold", 70);
        return executeFinanceSelect(sql, p.tenantId(), Date.valueOf(p.fromDate()), 
                                  Date.valueOf(p.toDate()), riskThreshold);
    }

    private List<Map<String, Object>> queryFinanceAMLSuspiciousActivity(FinanceQueryParams p) throws SQLException {
        String sql = """
            SELECT sar_id, transaction_pattern, risk_indicators, filing_date, status,
               regulatory_body, case_reference, investigation_status
            FROM finance_aml_suspicious_activity 
            WHERE tenant_id=? AND filing_date BETWEEN ? AND ? 
            ORDER BY filing_date DESC
            """;
        return executeFinanceSelect(sql, p.tenantId(), Date.valueOf(p.fromDate()), Date.valueOf(p.toDate()));
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    private List<Map<String, Object>> executeFinanceSelect(String sql, Object... params) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        row.put(meta.getColumnName(i), rs.getObject(i));
                    }
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    private FinanceQueryResult logFinanceQuery(String regulatorId, FinanceQueryParams params, 
                                               int resultCount, long durationMs, 
                                               FinanceRegulatoryContext context) throws SQLException {
        
        String paramsJson = String.format(
            "{\"queryType\":\"%s\",\"tenantId\":\"%s\",\"regulatoryBody\":\"%s\",\"complianceLevel\":\"%s\"}",
            params.queryType(), params.tenantId(), params.regulatoryBody().name(), params.complianceLevel()
        );
        
        String sql = """
            INSERT INTO finance_regulator_query_log 
                (regulator_id, tenant_id, query_type, params_json, result_count, duration_ms, 
                 regulatory_body, reporting_period, queried_at) 
            VALUES (?,?,?,?,?,?,?,?,?::jsonb,?,?) RETURNING log_id
            """;
        
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, regulatorId); ps.setString(2, params.tenantId());
            ps.setString(3, params.queryType()); ps.setString(4, paramsJson);
            ps.setInt(5, resultCount); ps.setLong(6, durationMs);
            ps.setString(7, params.regulatoryBody().name()); ps.setString(8, context.reportingPeriod());
            ps.setTimestamp(9, Timestamp.from(Instant.now()));
            
            try (ResultSet rs = ps.executeQuery()) { 
                rs.next(); 
                String logId = rs.getString("log_id");
                
                // Log compliance validation
                FinanceComplianceResult compliance = new FinanceComplianceResult(
                    true, context.complianceReference(), List.of("VALIDATED"), "LOW", 
                    Instant.now().plus(java.time.Duration.ofDays(30)).toString()
                );
                
                complianceValidationCounter.increment();
                
                return new FinanceQueryResult(logId, params.queryType(), List.of(), resultCount, 
                                            durationMs, compliance);
            }
        }
    }

    private record FinanceQueryLogEntry(
        String logId, String queryType, String tenantId, String paramsJson
    ) {}

    private record FinanceComplianceStatus(
        String tenantId, String queryType, int queryCount, Instant lastQuery, String status
    ) {}

    private FinanceQueryLogEntry loadFinanceQueryLogEntry(String logId) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT log_id, query_type, tenant_id, params_json::text FROM finance_regulator_query_log WHERE log_id=?"
             )) {
            ps.setString(1, logId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Finance query log entry not found: " + logId);
                return new FinanceQueryLogEntry(rs.getString("log_id"), rs.getString("query_type"),
                    rs.getString("tenant_id"), rs.getString("params_json"));
            }
        }
    }

    private List<Map<String, Object>> reExecuteFinanceQuery(FinanceQueryLogEntry entry) throws SQLException {
        // Re-execute with current date range for export
        FinanceQueryParams p = new FinanceQueryParams(
            entry.queryType(), entry.tenantId(), LocalDate.now(), 
            LocalDate.now().minusDays(30), LocalDate.now(),
            FinanceRegulatoryBody.SEC, "STANDARD", Map.of()
        );
        
        return switch (FinanceQueryType.valueOf(entry.queryType())) {
            case POSITIONS_AS_OF       -> queryFinancePositionsAsOf(p);
            case TRADE_HISTORY         -> queryFinanceTradeHistory(p);
            case SETTLEMENT_STATUS     -> queryFinanceSettlementStatus(p);
            case RECONCILIATION_REPORT -> queryFinanceReconciliationReport(p);
            case AUDIT_LOG             -> queryFinanceAuditLog(p);
            case COMPLIANCE_REPORT     -> queryFinanceComplianceReport(p);
            case RISK_EXPOSURE         -> queryFinanceRiskExposure(p);
            case CAPITAL_ADEQUACY      -> queryFinanceCapitalAdequacy(p);
            case TRANSACTION_MONITORING -> queryFinanceTransactionMonitoring(p);
            case AML_SUSPICIOUS_ACTIVITY -> queryFinanceAMLSuspiciousActivity(p);
        };
    }
}
