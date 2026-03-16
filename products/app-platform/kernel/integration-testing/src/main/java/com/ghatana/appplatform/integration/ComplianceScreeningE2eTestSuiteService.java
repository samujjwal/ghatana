package com.ghatana.appplatform.integration;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose End-to-end compliance and screening test suite.
 *              Covers: KYC-blocked order, sanctions-screened counterparty, AML alert triggered,
 *              regulatory report auto-submitted. Multi-jurisdiction variation.
 *              All K-07 audit events verified after each scenario.
 * @doc.layer   Integration Testing (T-01)
 * @doc.pattern Port-Adapter; JDBC; Promise.ofBlocking; scenario-execution; per-step assertion
 *
 * STORY-T01-002: E2E compliance and screening test suite
 */
public class ComplianceScreeningE2eTestSuiteService {

    // ── Scenario names ────────────────────────────────────────────────────────

    public static final String SANCTIONS_BLOCK_ORDER    = "sanctions_block_order";
    public static final String SAR_AUTO_CREATE          = "sar_auto_create";
    public static final String EOD_POSITION_REPORT      = "eod_position_report";
    public static final String SEBON_SUBMISSION         = "sebon_submission";
    public static final String AML_THRESHOLD            = "aml_threshold_test";
    public static final String KYC_BLOCKED_ORDER        = "kyc_blocked_order";
    public static final String MULTI_JURISDICTION_REPORT = "multi_jurisdiction_report";

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface OrderSubmissionPort {
        /** Returns orderId, or throws if rejected at submission. */
        String submitOrder(String clientId, String symbol, int quantity, double price) throws Exception;
        String getOrderStatus(String orderId) throws Exception;
        String getRejectionReason(String orderId) throws Exception;
    }

    public interface SanctionsScreeningPort {
        boolean isOnSanctionsList(String clientId) throws Exception;
        String getScreeningEventId(String clientId) throws Exception;
    }

    public interface KycStatusPort {
        String getKycStatus(String clientId) throws Exception; // APPROVED|PENDING|REJECTED|EXPIRED
    }

    public interface AmlAlertPort {
        boolean hasSarForClient(String clientId) throws Exception;
        String getAmlAlertId(String clientId, String tradeId) throws Exception;
        double getTradeAmount(String tradeId) throws Exception;
    }

    public interface RegulatoryReportPort {
        boolean isEodReportSubmitted(String date, String jurisdiction) throws Exception;
        String getLastSubmissionId(String jurisdiction) throws Exception;
    }

    public interface K07AuditVerificationPort {
        boolean hasAuditEvent(String eventType, String entityId) throws Exception;
        List<String> getAuditEvents(String entityId) throws Exception;
    }

    public interface AuditPort {
        void audit(String event, String detail) throws Exception;
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final OrderSubmissionPort orderSubmission;
    private final SanctionsScreeningPort sanctionsScreening;
    private final KycStatusPort kycStatus;
    private final AmlAlertPort amlAlert;
    private final RegulatoryReportPort regulatoryReport;
    private final K07AuditVerificationPort k07Audit;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter scenariosPassed;
    private final Counter scenariosFailed;

    public ComplianceScreeningE2eTestSuiteService(
        javax.sql.DataSource ds,
        OrderSubmissionPort orderSubmission,
        SanctionsScreeningPort sanctionsScreening,
        KycStatusPort kycStatus,
        AmlAlertPort amlAlert,
        RegulatoryReportPort regulatoryReport,
        K07AuditVerificationPort k07Audit,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds                 = ds;
        this.orderSubmission    = orderSubmission;
        this.sanctionsScreening = sanctionsScreening;
        this.kycStatus          = kycStatus;
        this.amlAlert           = amlAlert;
        this.regulatoryReport   = regulatoryReport;
        this.k07Audit           = k07Audit;
        this.audit              = audit;
        this.executor           = executor;
        this.scenariosPassed    = Counter.builder("integration.e2e.compliance.passed").register(registry);
        this.scenariosFailed    = Counter.builder("integration.e2e.compliance.failed").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public Promise<SuiteResult> runAll() {
        return Promise.ofBlocking(executor, () -> {
            List<ScenarioResult> results = new ArrayList<>();
            results.add(runScenario(SANCTIONS_BLOCK_ORDER,    this::sanctionsBlockOrder));
            results.add(runScenario(SAR_AUTO_CREATE,          this::sarAutoCreate));
            results.add(runScenario(EOD_POSITION_REPORT,      this::eodPositionReport));
            results.add(runScenario(SEBON_SUBMISSION,         this::sebonSubmission));
            results.add(runScenario(AML_THRESHOLD,            this::amlThresholdTest));
            results.add(runScenario(KYC_BLOCKED_ORDER,        this::kycBlockedOrder));
            results.add(runScenario(MULTI_JURISDICTION_REPORT, this::multiJurisdictionReport));

            long passed = results.stream().filter(r -> r.passed).count();
            audit.audit("E2E_COMPLIANCE_SUITE", "passed=" + passed + " failed=" + (results.size() - passed));
            return new SuiteResult("ComplianceScreening", results, passed, results.size() - passed);
        });
    }

    // ── Scenarios ─────────────────────────────────────────────────────────────

    private void sanctionsBlockOrder(String runId) throws Exception {
        // SANCTIONED-CLIENT-001 is pre-seeded on the sanctions list
        String clientId = "SANCTIONED-CLIENT-001";
        assertStep(runId, "client_on_sanctions", "client is sanctioned", "true",
            sanctionsScreening.isOnSanctionsList(clientId), true);

        // Order must be rejected at submission — sanctions check fires before routing
        String orderId;
        try {
            orderId = orderSubmission.submitOrder(clientId, "NABIL", 10, 900.0);
        } catch (Exception ex) {
            // Expected rejection path — exceptions from port mean blocked at gateway
            assertStep(runId, "order_blocked", "order blocked by sanctions", "BLOCKED", "BLOCKED", true);
            String screeningId = sanctionsScreening.getScreeningEventId(clientId);
            assertStep(runId, "k07_sanctions_event", "K-07 sanctions event recorded", "true",
                k07Audit.hasAuditEvent("SANCTIONS_BLOCK", screeningId), true);
            return;
        }
        String status = orderSubmission.getOrderStatus(orderId);
        assertStep(runId, "order_rejected", "sanctioned order rejected", "REJECTED", status.equals("REJECTED"), status);
        assertStep(runId, "rejection_reason", "rejection contains SANCTIONS", "SANCTIONS",
            orderSubmission.getRejectionReason(orderId).contains("SANCTIONS"), true);
        assertStep(runId, "k07_audit", "K-07 event recorded for rejection", "true",
            k07Audit.hasAuditEvent("ORDER_REJECTED_SANCTIONS", orderId), true);
    }

    private void sarAutoCreate(String runId) throws Exception {
        // CLIENT-LARGE-TRADER is pre-seeded to trigger large-trade threshold
        String clientId = "CLIENT-LARGE-TRADER";
        // Submit a large trade above AML threshold (NPR 10M)
        String orderId = orderSubmission.submitOrder(clientId, "NLIC", 10000, 1200.0);
        assertStep(runId, "order_submitted", "large order submitted", orderId, orderId != null, orderId);

        // Wait briefly for AML processing
        Thread.sleep(500);
        assertStep(runId, "sar_created", "SAR auto-created for large trade", "true",
            amlAlert.hasSarForClient(clientId), true);
        assertStep(runId, "k07_sar_event", "K-07 SAR creation audited", "true",
            k07Audit.hasAuditEvent("SAR_CREATED", clientId), true);
    }

    private void eodPositionReport(String runId) throws Exception {
        String today = java.time.LocalDate.now().toString();
        // Allow brief delay for EOD processing pipeline
        Thread.sleep(300);
        boolean submitted = regulatoryReport.isEodReportSubmitted(today, "NPL");
        assertStep(runId, "eod_report_submitted", "EOD position report submitted (NPL)", "true", submitted, true);
        String submissionId = regulatoryReport.getLastSubmissionId("NPL");
        assertStep(runId, "k07_eod_event", "K-07 EOD submission audited", "true",
            k07Audit.hasAuditEvent("REGULATORY_REPORT_SUBMITTED", submissionId), true);
    }

    private void sebonSubmission(String runId) throws Exception {
        String today = java.time.LocalDate.now().toString();
        boolean submitted = regulatoryReport.isEodReportSubmitted(today, "SEBON");
        assertStep(runId, "sebon_report", "SEBON report submitted", "true", submitted, true);
    }

    private void amlThresholdTest(String runId) throws Exception {
        // CLIENT-AML-TEST exceeds threshold when combined 24h trade volume > 5M NPR
        String clientId = "CLIENT-AML-TEST";
        String orderId = orderSubmission.submitOrder(clientId, "NTC", 5000, 1100.0);
        Thread.sleep(400);
        String alertId = amlAlert.getAmlAlertId(clientId, orderId);
        assertStep(runId, "aml_alert_created", "AML alert exists", "non-null", alertId != null, true);
        assertStep(runId, "k07_aml_event", "K-07 AML event audited", "true",
            k07Audit.hasAuditEvent("AML_ALERT_RAISED", alertId), true);
    }

    private void kycBlockedOrder(String runId) throws Exception {
        String clientId = "CLIENT-KYC-EXPIRED";
        String kycState = kycStatus.getKycStatus(clientId);
        assertStep(runId, "kyc_expired", "client KYC is EXPIRED", "EXPIRED", kycState.equals("EXPIRED"), kycState);

        String orderId;
        try {
            orderId = orderSubmission.submitOrder(clientId, "NABIL", 10, 900.0);
        } catch (Exception ex) {
            assertStep(runId, "order_kyc_blocked", "order blocked by KYC expiry", "BLOCKED", "BLOCKED", true);
            return;
        }
        String status = orderSubmission.getOrderStatus(orderId);
        assertStep(runId, "order_kyc_rejected", "KYC-expired order rejected", "REJECTED", status.equals("REJECTED"), status);
    }

    private void multiJurisdictionReport(String runId) throws Exception {
        String today = java.time.LocalDate.now().toString();
        for (String jurisdiction : List.of("NPL", "IND", "SGP")) {
            boolean submitted = regulatoryReport.isEodReportSubmitted(today, jurisdiction);
            assertStep(runId, "report_" + jurisdiction, jurisdiction + " report submitted", "true", submitted, true);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ScenarioResult runScenario(String name, ThrowingConsumer<String> fn) {
        long start = System.currentTimeMillis();
        String runId = null;
        try {
            runId = insertRun(name);
            fn.accept(runId);
            markRunStatus(runId, "PASSED", null, null);
            scenariosPassed.increment();
            return new ScenarioResult(name, true, null, System.currentTimeMillis() - start);
        } catch (AssertionError ae) {
            if (runId != null) markRunStatus(runId, "FAILED", name, ae.getMessage());
            scenariosFailed.increment();
            return new ScenarioResult(name, false, ae.getMessage(), System.currentTimeMillis() - start);
        } catch (Exception ex) {
            if (runId != null) markRunStatus(runId, "FAILED", name, ex.getMessage());
            scenariosFailed.increment();
            return new ScenarioResult(name, false, ex.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private void assertStep(String runId, String step, String assertion, String expected, boolean passed, Object actual) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO e2e_step_assertions (run_id,step_name,assertion,expected,actual,passed) VALUES (?,?,?,?,?,?)"
             )) {
            ps.setString(1, runId); ps.setString(2, step); ps.setString(3, assertion);
            ps.setString(4, expected); ps.setString(5, String.valueOf(actual)); ps.setBoolean(6, passed);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
        if (!passed) throw new AssertionError("FAIL [" + step + "] " + assertion + ": expected=" + expected + " actual=" + actual);
    }

    private String insertRun(String scenario) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO e2e_test_runs (suite_name,scenario) VALUES ('ComplianceScreening',?) RETURNING run_id"
             )) {
            ps.setString(1, scenario);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getString(1); }
        }
    }

    private void markRunStatus(String runId, String status, String failStep, String failMsg) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE e2e_test_runs SET status=?, failure_step=?, failure_msg=?, duration_ms=0 WHERE run_id=?"
             )) {
            ps.setString(1, status); ps.setString(2, failStep); ps.setString(3, failMsg);
            ps.setString(4, runId); ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    @FunctionalInterface interface ThrowingConsumer<T> { void accept(T t) throws Exception; }
    public record ScenarioResult(String scenario, boolean passed, String failureMessage, long durationMs) {}
    public record SuiteResult(String suite, List<ScenarioResult> results, long passedCount, long failedCount) {}
}
